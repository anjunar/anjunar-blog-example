package com.anjunar.blog

import com.anjunar.json.mapper.intermediate.JsonParser
import com.anjunar.json.mapper.intermediate.model.{JsonArray, JsonObject}
import dev.resteasy.embedded.server.UndertowCdiEmbeddedServer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

import java.net.{InetAddress, ServerSocket, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.sql.{DriverManager, Timestamp, Types}
import java.time.{Duration, Instant}
import java.util.UUID
import scala.jdk.CollectionConverters.*
import scala.util.Using

class BlogPostsRestSpec extends AnyFunSuite with BeforeAndAfterAll {
  private var server: UndertowCdiEmbeddedServer = null
  private var client: HttpClient = null
  private var port = 0
  private var previousSize = 0L
  private var publicationTime: Instant = null
  private val publishedIds = Vector.fill(3)(UUID.randomUUID()).sortBy(_.toString)
  private val draftId = UUID.randomUUID()
  private val ownedIds = publishedIds :+ draftId
  private val content = "A complete post: \"Hello, readers!\"\nGrüße from the tutorial."
  private val listFields = Set("id", "version", "slug", "title", "summary", "status", "publishedAt", "contentLocale")
  private val detailFields = listFields ++ Set("content", "availableLocales")

  private def connection() = {
    val config = DatabaseConfig.load()
    DriverManager.getConnection(config.url, config.user, config.password)
  }

  private def slug(id: UUID): String = s"rest-test-$id"

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    Using.resource(connection()) { connection =>
      Using.resource(connection.createStatement()) { query =>
        Using.resource(query.executeQuery(
            "select count(*), max(published_at) from public.blog_post where status = 'PUBLISHED'")) { result =>
          result.next()
          previousSize = result.getLong(1)
          publicationTime = Option(result.getTimestamp(2)).map(_.toInstant)
            .getOrElse(Instant.parse("2026-09-27T10:15:42.123456Z")).plusSeconds(60)
        }
      }
      // Put this suite's rows first without changing pre-existing posts.
      Using.resource(connection.prepareStatement(
          """insert into public.blog_post
            |(id, version, slug, title, content, summary, status, published_at)
            |values (?, 0, ?, ?, ?, ?, ?, ?)""".stripMargin)) { insert =>
        for (id <- ownedIds) {
          val published = id != draftId
          insert.setObject(1, id)
          insert.setString(2, slug(id))
          insert.setString(3, "A public REST post")
          insert.setString(4, content)
          insert.setString(5, "A short introduction.")
          insert.setString(6, if (published) "PUBLISHED" else "DRAFT")
          if (published) {
            val at = if (id == publishedIds.last) publicationTime.minusSeconds(1) else publicationTime
            insert.setTimestamp(7, Timestamp.from(at))
          } else insert.setNull(7, Types.TIMESTAMP_WITH_TIMEZONE)
          insert.executeUpdate()
        }
      }
    }
    val reservation = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    port = try reservation.getLocalPort finally reservation.close()
    server = ApplicationMain.start(port)
    client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
  }

  override protected def afterAll(): Unit =
    try {
      if (client != null) client.close()
    } finally {
      try {
        if (server != null) server.stop()
      } finally {
        try Using.resource(connection()) { connection =>
          Using.resource(connection.prepareStatement("delete from public.blog_post where id = ?")) { delete =>
            for (id <- ownedIds) {
              delete.setObject(1, id)
              delete.executeUpdate()
            }
          }
        }
        finally super.afterAll()
      }
    }

  private def request(path: String = "", method: String = "GET"): HttpResponse[String] = {
    val request = HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port/service/blog/posts$path"))
      .timeout(Duration.ofSeconds(20))
      .header("Accept", "application/json")
      .method(method, HttpRequest.BodyPublishers.noBody())
      .build()
    client.send(request, HttpResponse.BodyHandlers.ofString())
  }

  private def json(response: HttpResponse[String]): JsonObject = {
    assert(response.statusCode() == 200, response.body())
    assert(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"))
    JsonParser.parse(response.body()).asInstanceOf[JsonObject]
  }

  private def rows(table: JsonObject): Vector[JsonObject] =
    table.value.get("rows").asInstanceOf[JsonArray].value.asScala
      .map(_.asInstanceOf[JsonObject]).toVector

  private def assertSchema(wrapper: JsonObject, expected: Set[String]): Unit = {
    val entries = wrapper.getJsonObject("schema").value.get("entries")
      .asInstanceOf[JsonArray].value.asScala.map(_.asInstanceOf[JsonObject])
    assert(entries.map(_.getString("name")).toSet == expected)
    assert(entries.forall(_.getString("type").nonEmpty))
  }

  test("a public list omits content, counts only published posts, and has a stable order") {
    val table = json(request("?limit=3"))
    assert(table.value.get("size").value == (previousSize + 3).toString)
    val page = rows(table)
    assert(page.map(_.getJsonObject("data").getString("id")) == publishedIds.map(_.toString))
    for (wrapper <- page) {
      val post = wrapper.getJsonObject("data")
      assert(post.value.keySet().asScala.toSet == listFields + "@type")
      assert(post.getString("status") == "PUBLISHED")
      assert(post.value.get("version").value == "0")
      assertSchema(wrapper, listFields)
    }
  }

  test("offset and limit select a page while size remains the total published count") {
    val table = json(request("?offset=1&limit=1"))
    val page = rows(table)
    assert(page.size == 1)
    assert(page.head.getJsonObject("data").getString("id") == publishedIds(1).toString)
    assert(table.value.get("size").value == (previousSize + 3).toString)
    val defaults = rows(json(request()))
    assert(defaults.size == math.min(20L, previousSize + 3).toInt)
  }

  test("a detail response contains the complete post, version, timestamp, and matching field metadata") {
    val id = publishedIds.head
    val wrapper = json(request(s"/${slug(id)}"))
    val post = wrapper.getJsonObject("data")
    assert(post.value.keySet().asScala.toSet == detailFields + "@type")
    assert(post.getString("id") == id.toString)
    assert(post.getString("slug") == slug(id))
    assert(post.getString("content") == content)
    assert(post.getString("publishedAt") == publicationTime.toString)
    assert(post.value.get("version").value == "0")
    assertSchema(wrapper, detailFields ++ Set("author", "tags", "coverImage", "coverAlt", "contentFormat", "translation"))
  }

  test("draft and unknown slugs both return 404 without disclosing post content") {
    for (id <- Seq(draftId, UUID.randomUUID())) {
      val response = request(s"/${slug(id)}")
      assert(response.statusCode() == 404)
      assert(!response.body().contains(content))
    }
  }

  test("invalid paging bounds and nonnumeric query values return 400") {
    for (query <- Seq("?offset=-1", "?limit=0", "?limit=101", "?limit=abc", "?offset=abc", "?limit=2147483648", "?limit=")) {
      assert(request(query).statusCode() == 400, query)
    }
  }

  test("an empty page retains the total and follows the mapper's omitted-empty-collection contract") {
    val table = json(request(s"?offset=${previousSize + 3}&limit=1"))
    assert(table.value.get("size").value == (previousSize + 3).toString)
    assert(!table.value.containsKey("rows"))
  }

  test("public resources expose no write method") {
    assert(request(method = "POST").statusCode() == 405)
    assert(request(s"/${slug(publishedIds.head)}", method = "PUT").statusCode() == 405)
    assert(request(s"/${slug(publishedIds.head)}", method = "DELETE").statusCode() == 405)
  }

  test("HEAD returns no body and subsequent JSON reads still work") {
    val response = request(s"/${slug(publishedIds.head)}", method = "HEAD")
    assert(response.statusCode() == 200)
    assert(response.body().isEmpty)
    assert(json(request("?limit=1")).value.containsKey("rows"))
  }
}
