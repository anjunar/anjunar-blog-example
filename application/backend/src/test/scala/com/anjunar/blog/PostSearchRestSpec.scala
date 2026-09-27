package com.anjunar.blog

import com.anjunar.json.mapper.intermediate.JsonParser
import com.anjunar.json.mapper.intermediate.model.{JsonArray, JsonObject}
import dev.resteasy.embedded.server.UndertowCdiEmbeddedServer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

import java.net.{CookieManager, CookiePolicy, InetAddress, ServerSocket, URI, URLEncoder}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.sql.{DriverManager, Timestamp, Types}
import java.time.{Duration, Instant}
import java.util.UUID
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.Using

class PostSearchRestSpec extends AnyFunSuite with BeforeAndAfterAll {
  private var server: UndertowCdiEmbeddedServer = null
  private var port = 0
  private val clients = mutable.Buffer.empty[HttpClient]
  private val accountIds = mutable.Buffer.empty[UUID]
  private val ids = Vector.fill(4)(UUID.randomUUID()).sortBy(_.toString)
  private val marker = "search" + UUID.randomUUID().toString.take(8)
  private val password = "a local search test passphrase"
  private lazy val hash = PasswordHash.create(password)

  private def connection() = {
    val config = DatabaseConfig.load()
    DriverManager.getConnection(config.url, config.user, config.password)
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    Using.resource(connection()) { c =>
      Using.resource(c.prepareStatement(
        "insert into public.blog_post (id, version, slug, title, content, summary, status, published_at) " +
          "values (?, 0, ?, ?, ?, ?, ?, ?)")) { insert =>
        ids.zipWithIndex.foreach { (id, index) =>
          insert.setObject(1, id)
          insert.setString(2, "search-" + id)
          insert.setString(3, marker + (if (index < 2) " Alpha" else if (index == 2) " %_! & + café" else " Draft"))
          insert.setString(4, "body-only-" + marker)
          insert.setString(5, if (index == 0) "summary-only-" + marker else null)
          insert.setString(6, if (index == 3) "DRAFT" else "PUBLISHED")
          if (index == 3) insert.setNull(7, Types.TIMESTAMP_WITH_TIMEZONE)
          else insert.setTimestamp(7, Timestamp.from(Instant.parse("2026-09-27T10:00:00Z").minusSeconds(if (index == 2) 60 else 0)))
          insert.executeUpdate()
        }
      }
    }
    val socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    port = try socket.getLocalPort finally socket.close()
    server = ApplicationMain.start(port, security = SecurityConfig(false))
  }

  override protected def afterAll(): Unit = try {
    clients.foreach(_.close())
    if (server != null) server.stop()
  } finally {
    try Using.resource(connection()) { c =>
      for ((table, owned) <- Seq("blog_post" -> ids, "blog_account" -> accountIds.toVector)) {
        Using.resource(c.prepareStatement(s"delete from public.$table where id = ?")) { delete =>
          owned.foreach { id => delete.setObject(1, id); delete.executeUpdate() }
        }
      }
    } finally super.afterAll()
  }

  private def encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
  private def query(text: String = marker, rest: String = ""): String = "?q=" + encode(text) + rest

  private final class Browser {
    private val cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL)
    private val client = HttpClient.newBuilder().cookieHandler(cookies).build()
    clients += client

    def get(path: String): HttpResponse[String] = client.send(
      HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port$path"))
        .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString())

    def list(suffix: String, editorial: Boolean = false): HttpResponse[String] =
      get((if (editorial) "/service/editorial/posts" else "/service/blog/posts") + suffix)

    def login(role: String): Unit = {
      val id = UUID.randomUUID()
      val email = s"search-$id@example.test"
      accountIds += id
      Using.resource(connection()) { c =>
        Using.resource(c.prepareStatement(
          "insert into public.blog_account (id, version, email, role, password_hash, authentication_version, locked) " +
            "values (?, 0, ?, ?, ?, 0, false)")) { insert =>
          insert.setObject(1, id); insert.setString(2, email); insert.setString(3, role); insert.setString(4, hash)
          insert.executeUpdate()
        }
      }
      val token = json(get("/service/auth/session")).getString("csrfToken")
      val body = new JsonObject().put("email", email).put("password", password).encode()
      val response = client.send(HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port/service/auth/login"))
        .header("Content-Type", "application/json").header("X-CSRF-Token", token)
        .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString())
      assert(response.statusCode() == 200, response.body())
    }
  }

  private def json(response: HttpResponse[String]): JsonObject = {
    assert(response.statusCode() == 200, response.body())
    JsonParser.parse(response.body()).asInstanceOf[JsonObject]
  }

  private def rows(table: JsonObject): Vector[JsonObject] =
    Option(table.value.get("rows").asInstanceOf[JsonArray]).toVector
      .flatMap(_.value.asScala).map(_.asInstanceOf[JsonObject].getJsonObject("data"))

  private def size(table: JsonObject): Long = table.value.get("size").value.toString.toLong
  private def rowIds(table: JsonObject): Vector[String] = rows(table).map(_.getString("id"))
  private def links(table: JsonObject): Map[String, String] =
    table.value.get("$links").asInstanceOf[JsonArray].value.asScala
      .map(_.asInstanceOf[JsonObject]).map(link => link.getString("rel") -> link.getString("url")).toMap

  test("public search and count include only published matches, even for a signed-in administrator") {
    val browser = new Browser()
    browser.login("ADMIN")
    val result = json(browser.list(query()))
    assert(size(result) == 3 && rowIds(result) == ids.take(3).map(_.toString))
    assert(browser.list(query(rest = "&status=DRAFT")).statusCode() == 400)
    assert(size(json(browser.list(query(rest = "&status=PUBLISHED")))) == 3)
    assert(!result.encode().contains("body-only-"))
    assert(rows(result).forall(row => !row.value.containsKey("content") && row.value.get("version").value == "0"))
  }

  test("search matches title, slug and summary but does not inspect the body") {
    val browser = new Browser()
    assert(size(json(browser.list(query(marker.toUpperCase + " ALPHA")))) == 2)
    assert(rowIds(json(browser.list(query(ids.head.toString)))) == Vector(ids.head.toString))
    assert(rowIds(json(browser.list(query("summary-only-" + marker)))) == Vector(ids.head.toString))
    assert(size(json(browser.list(query("body-only-" + marker)))) == 0)
  }

  test("percent, underscore and the escape character are literal search text") {
    val browser = new Browser()
    assert(rowIds(json(browser.list(query(marker + " %")))) == Vector(ids(2).toString))
    assert(size(json(browser.list(query(marker + " _")))) == 0)
    assert(rowIds(json(browser.list(query(marker + " %_! & + café")))) == Vector(ids(2).toString))
    assert(size(json(browser.list(query("' OR 1=1 --")))) == 0)
  }

  test("page links preserve encoded text, status, sorting and the page size") {
    val browser = new Browser()
    val first = json(browser.list(query(marker + " Alpha", "&sort=title-desc&limit=1")))
    assert(size(first) == 2 && rowIds(first) == Vector(ids.head.toString))
    val nextUrl = links(first)("next")
    assert(nextUrl.contains("sort=title-desc") && nextUrl.contains("status=PUBLISHED") && nextUrl.contains("limit=1"))
    val second = json(browser.get(nextUrl))
    assert(rowIds(second) == Vector(ids(1).toString) && size(second) == 2)
    assert(!links(second).contains("next"))
    assert(rowIds(json(browser.get(links(second)("previous")))) == rowIds(first))
    val encoded = json(browser.list(query(marker + " %_! & + café", "&limit=1")))
    assert(rowIds(json(browser.get(links(encoded)("self")))) == Vector(ids(2).toString))
    for (literal <- Seq(marker + "%20", marker + "{query}")) {
      val absent = json(browser.list(query(literal)))
      assert(size(absent) == 0)
      assert(size(json(browser.get(links(absent)("self")))) == 0)
    }
  }

  test("ordering uses a UUID tie-breaker and reverses the requested primary order") {
    val browser = new Browser()
    val oldest = json(browser.list(query(rest = "&sort=oldest")))
    assert(rowIds(oldest) == Vector(ids(2), ids(0), ids(1)).map(_.toString))
    for (order <- Seq("title", "title-desc")) {
      val first = json(browser.list(query(marker + " Alpha", s"&sort=$order&limit=1")))
      val second = json(browser.get(links(first)("next")))
      assert(rowIds(first) ++ rowIds(second) == ids.take(2).map(_.toString))
    }
  }

  test("invalid bounds, text, status and sort values receive 400") {
    val browser = new Browser()
    for (suffix <- Seq("?offset=-1", "?offset=2147483648", "?limit=0", "?limit=101", "?limit=abc",
      "?sort=title%20desc", "?sort=content", "?status=unknown", "?q=" + ("a" * 101), "?q=a%00b")) {
      assert(browser.list(suffix).statusCode() == 400, suffix)
    }
    assert(browser.list("?q=" + ("a" * 100)).statusCode() == 200)
  }

  test("empty pages retain the filtered count and a link to the first matching page") {
    val browser = new Browser()
    val empty = json(browser.list(query(rest = "&offset=2147483647&limit=100")))
    assert(rows(empty).isEmpty && size(empty) == 3)
    assert(!links(empty).contains("next"))
    assert(size(json(browser.get(links(empty)("first")))) == 3)
    val absent = json(browser.list(query("no-match-" + marker)))
    assert(rows(absent).isEmpty && size(absent) == 0 && !links(absent).contains("next"))
  }

  test("editorial status filters affect both rows and count and drafts sort after dated posts") {
    val browser = new Browser()
    browser.login("ADMIN")
    assert(size(json(browser.list(query(), editorial = true))) == 4)
    val drafts = json(browser.list(query(rest = "&status=DRAFT"), editorial = true))
    assert(rowIds(drafts) == Vector(ids.last.toString) && size(drafts) == 1)
    assert(size(json(browser.list(query(rest = "&status=PUBLISHED"), editorial = true))) == 3)
    for (order <- Seq("newest", "oldest")) {
      val result = json(browser.list(query(rest = s"&sort=$order"), editorial = true))
      assert(rowIds(result).last == ids.last.toString && size(result) == 4)
    }
    assert(links(drafts).contains("create"))
  }

  test("editorial filters do not bypass endpoint authorization") {
    val browser = new Browser()
    assert(browser.list(query(rest = "&status=DRAFT"), editorial = true).statusCode() == 401)
    browser.login("READER")
    assert(browser.list(query(rest = "&status=DRAFT"), editorial = true).statusCode() == 403)
  }
}
