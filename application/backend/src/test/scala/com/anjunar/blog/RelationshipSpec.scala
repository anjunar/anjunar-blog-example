package com.anjunar.blog

import com.anjunar.json.mapper.intermediate.JsonParser
import com.anjunar.json.mapper.intermediate.model.{JsonArray, JsonObject}
import dev.resteasy.embedded.server.UndertowCdiEmbeddedServer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

import java.net.{CookieManager, CookiePolicy, InetAddress, ServerSocket, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.sql.DriverManager
import java.time.Duration
import java.util.UUID
import java.util.concurrent.{Callable, Executors}
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.Using

class RelationshipSpec extends AnyFunSuite with BeforeAndAfterAll {
  private var server: UndertowCdiEmbeddedServer = null
  private var port = 0
  private val owned = mutable.Buffer.empty[UUID]
  private val clients = mutable.Buffer.empty[HttpClient]
  private val password = "a long tutorial passphrase"
  private lazy val hash = PasswordHash.create(password)

  private def connection() = {
    val config = DatabaseConfig.load()
    DriverManager.getConnection(config.url, config.user, config.password)
  }

  private def freePort(): Int = {
    val socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    try socket.getLocalPort finally socket.close()
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    port = freePort()
    server = ApplicationMain.start(port, security = SecurityConfig(false))
  }

  override protected def afterAll(): Unit = try {
    clients.foreach(_.close())
    if (server != null) server.stop()
  } finally {
    Using.resource(connection()) { c =>
      Using.resource(c.prepareStatement("delete from public.blog_post_tag where post_id = ?")) { q =>
        posts.foreach { id => q.setObject(1, id); q.executeUpdate() }
      }
    }
    Using.resource(connection()) { c =>
      Using.resource(c.prepareStatement("delete from public.blog_post where id = ?")) { q =>
        posts.foreach { id => q.setObject(1, id); q.executeUpdate() }
      }
    }
    Using.resource(connection()) { c =>
      Using.resource(c.prepareStatement("delete from public.blog_tag where id = ?")) { q =>
        ownedTags.foreach { id => q.setObject(1, id); q.executeUpdate() }
      }
    }
    Using.resource(connection()) { connection =>
      Using.resource(connection.prepareStatement("delete from public.blog_account where id = ?")) { query =>
        owned.foreach { id => query.setObject(1, id); query.executeUpdate() }
      }
    }
    super.afterAll()
  }

  private def account(locked: Boolean = false): String = {
    val id = UUID.randomUUID()
    val email = s"auth-$id@example.com"
    owned += id
    Using.resource(connection()) { connection =>
      Using.resource(connection.prepareStatement(
        """insert into public.blog_account (id, version, email, role, password_hash, authentication_version, locked)
          |values (?, 0, ?, 'ADMIN', ?, 0, ?)""".stripMargin)) { query =>
        query.setObject(1, id)
        query.setString(2, email)
        query.setString(3, hash)
        query.setBoolean(4, locked)
        query.executeUpdate()
      }
    }
    email
  }

  private final class Browser {
    val cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL)
    val client = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build()
    clients += client
    def cookie: String = cookies.getCookieStore.getCookies.asScala.find(_.getName == SecurityConfig.cookieName).get.getValue
    def send(path: String, method: String = "GET", body: String = "", csrf: String = "",
        extra: Map[String, String] = Map.empty): HttpResponse[String] = {
      val builder = HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port/service/$path"))
        .timeout(Duration.ofSeconds(20)).header("Accept", "application/json")
      if (csrf.nonEmpty) builder.header("X-CSRF-Token", csrf)
      extra.foreach((key, value) => builder.setHeader(key, value))
      if (method == "POST" || method == "PATCH") builder.header("Content-Type", "application/json")
      builder.method(method, HttpRequest.BodyPublishers.ofString(body))
      client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }
    def session(): JsonObject = json(send("auth/session"))
    def login(email: String, secret: String = password): HttpResponse[String] =
      send("auth/login", "POST", new JsonObject().put("email", email).put("password", secret).encode(),
        session().getString("csrfToken"))
  }

  private def json(response: HttpResponse[String]): JsonObject = {
    assert(Set(200, 201).contains(response.statusCode()), response.body())
    JsonParser.parse(response.body()).asInstanceOf[JsonObject]
  }


  private val posts = mutable.Buffer.empty[UUID]
  private def draft(content: String = "A private body"): UUID = {
    val id = UUID.randomUUID()
    posts += id
    Using.resource(connection()) { c =>
      Using.resource(c.prepareStatement(
        "insert into public.blog_post (id, version, slug, title, content, status) values (?, 0, ?, ?, ?, 'DRAFT')")) { q =>
        q.setObject(1, id); q.setString(2, s"permission-$id")
        q.setString(3, s"Permission test $id"); q.setString(4, content); q.executeUpdate()
      }
    }
    id
  }
  private def changeRole(email: String, role: String): Unit =
    Using.resource(connection()) { c =>
      Using.resource(c.prepareStatement("update public.blog_account set role = ? where email = ?")) { q =>
        q.setString(1, role); q.setString(2, email); q.executeUpdate()
      }
    }
  private def path(id: UUID): String = s"editorial/posts/$id"
  private def relations(value: JsonObject): Set[String] =
    Option(value.value.get("$links").asInstanceOf[JsonArray]).toSeq.flatMap(_.value.asScala)
      .map(_.asInstanceOf[JsonObject].getString("rel")).toSet
  private def admin(): Browser = {
    val browser = new Browser()
    json(browser.login(account()))
    browser
  }
  private def action(browser: Browser, id: UUID, name: String, extra: Map[String, String] = Map.empty) =
    browser.send(s"${path(id)}/$name", "POST", csrf = browser.session().getString("csrfToken"), extra = extra)


  private def input(slug: String = s"edit-${UUID.randomUUID()}"): JsonObject =
    new JsonObject().put("slug", slug).put("title", "A carefully prepared post").put("content", "Original body")

  private def create(browser: Browser, body: JsonObject): HttpResponse[String] = {
    val response = browser.send("editorial/posts", "POST", body.encode(), browser.session().getString("csrfToken"))
    if (response.statusCode() == 201) posts += UUID.fromString(json(response).getJsonObject("data").getString("id"))
    response
  }

  private def patch(browser: Browser, id: UUID, body: String, extra: Map[String, String] = Map.empty) =
    browser.send(path(id), "PATCH", body, browser.session().getString("csrfToken"), extra)

  private def fields(problem: JsonObject): Set[String] =
    Option(problem.value.get("errors").asInstanceOf[JsonArray]).toSeq.flatMap(_.value.asScala)
      .flatMap(_.asInstanceOf[JsonObject].value.get("path").asInstanceOf[JsonArray].value.asScala)
      .map(_.value.toString).toSet

  private def problem(response: HttpResponse[String], status: Int): JsonObject = {
    assert(response.statusCode() == status, response.body())
    assert(response.headers().firstValue("Content-Type").orElse("").startsWith(Problem.mediaType), response.body())
    val body = JsonParser.parse(response.body()).asInstanceOf[JsonObject]
    assert(body.value.get("status").value == status.toString)
    assert(!response.body().contains("RESTEASY") && !response.body().contains("Exception"))
    body
  }

  private val ownedTags = mutable.Buffer.empty[UUID]
  private def tag(browser: Browser, name: String = "Scala"): JsonObject = {
    val response = browser.send("editorial/tags", "POST",
      new JsonObject().put("slug", s"tag-${UUID.randomUUID()}").put("name", name).encode(),
      browser.session().getString("csrfToken"))
    val result = json(response).getJsonObject("data")
    ownedTags += UUID.fromString(result.getString("id"))
    result
  }
  private def version(value: JsonObject): String = value.value.get("version").value.toString
  private def data(browser: Browser, id: UUID): JsonObject = json(browser.send(path(id))).getJsonObject("data")
  private def ref(value: JsonObject): String = s"""{"id":"${value.getString("id")}"}"""
  private def arrayIds(value: JsonObject, name: String): Set[String] =
    Option(value.value.get(name)).toSeq.flatMap(_.asInstanceOf[JsonArray].value.asScala)
      .map(_.asInstanceOf[JsonObject].getString("id")).toSet

  test("new posts default to the administrator and publish only safe author fields") {
    val browser = admin()
    val self = browser.session().getJsonObject("account")
    val changed = json(browser.send(s"editorial/authors/${self.getString("id")}", "PATCH",
      s"""{"version":${version(self)},"displayName":"Tutorial author"}""",
      browser.session().getString("csrfToken"))).getJsonObject("data")
    assert(changed.getString("displayName") == "Tutorial author")
    val created = json(create(browser, input())).getJsonObject("data")
    val id = UUID.fromString(created.getString("id"))
    assert(created.getJsonObject("author").getString("id") == self.getString("id"))
    json(action(browser, id, "publish"))
    val publicPost = json(new Browser().send(s"blog/posts/${created.getString("slug")}"))
    val author = publicPost.getJsonObject("data").getJsonObject("author")
    assert(author.value.keySet().asScala.toSet == Set("id", "version", "displayName", "@type"))
    assert(author.getString("displayName") == "Tutorial author")
  }

  test("IDs replace existing authors; omission preserves and null clears them") {
    val browser = admin()
    account()
    val second = owned.last
    val created = json(create(browser, input())).getJsonObject("data")
    val id = UUID.fromString(created.getString("id"))
    val replaced = json(patch(browser, id, s"""{"version":${version(created)},"author":{"id":"$second"}}""")).getJsonObject("data")
    assert(replaced.getJsonObject("author").getString("id") == second.toString)
    val kept = json(patch(browser, id, s"""{"version":${version(replaced)},"summary":"Keep this author"}""")).getJsonObject("data")
    assert(kept.getJsonObject("author").getString("id") == second.toString)
    val cleared = json(patch(browser, id, s"""{"version":${version(kept)},"author":null}""")).getJsonObject("data")
    assert(!cleared.value.containsKey("author") && !data(browser, id).value.containsKey("author"))
  }

  test("posts share tags and clearing a set never deletes a shared tag") {
    val browser = admin()
    val shared = tag(browser)
    val first = json(create(browser, input())).getJsonObject("data")
    val second = json(create(browser, input())).getJsonObject("data")
    val firstId = UUID.fromString(first.getString("id"))
    val secondId = UUID.fromString(second.getString("id"))
    val added = json(patch(browser, firstId, s"""{"version":${version(first)},"tags":[${ref(shared)}]}""")).getJsonObject("data")
    json(patch(browser, secondId, s"""{"version":${version(second)},"tags":[${ref(shared)}]}"""))
    assert(arrayIds(added, "tags") == Set(shared.getString("id")))
    val renamed = json(browser.send(s"editorial/tags/${shared.getString("id")}", "PATCH",
      s"""{"version":${version(shared)},"name":"Scala ecosystem"}""",
      browser.session().getString("csrfToken"))).getJsonObject("data")
    assert(renamed.getString("name") == "Scala ecosystem")
    json(patch(browser, firstId, s"""{"version":${version(added)},"tags":[]}"""))
    assert(arrayIds(data(browser, firstId), "tags").isEmpty)
    assert(arrayIds(data(browser, secondId), "tags") == Set(shared.getString("id")))
    assert(data(browser, secondId).value.get("tags").asInstanceOf[JsonArray].value.get(0)
      .asInstanceOf[JsonObject].getString("name") == "Scala ecosystem")
  }

  test("reference input rejects nested edits, new children, duplicates and malformed IDs") {
    val browser = admin()
    val created = json(create(browser, input())).getJsonObject("data")
    val id = UUID.fromString(created.getString("id"))
    val author = created.getJsonObject("author")
    val shared = tag(browser)
    val malformed = Seq(
      s""""author":{"id":"${author.getString("id")}","displayName":"Unwanted"}""",
      """"author":{}""", """"author":{"id":"invalid"}""",
      s""""author":{"id":"${UUID.randomUUID()}"}""",
      s""""author":{"id":"${author.getString("id")}","@type":"BlogTag"}""",
      """"tags":null""", """"tags":[{"name":"New child"}]""",
      s""""tags":[${ref(shared)},${ref(shared)}]""",
      s""""tags":[{"id":"${shared.getString("id")}","name":"Unwanted"}]""",
      s""""tags":[{"id":"${UUID.randomUUID()}"}]"""
    )
    malformed.foreach { selected =>
      problem(patch(browser, id, s"""{"version":${version(created)},"title":"Must roll back",$selected}"""), 400)
      val after = data(browser, id)
      assert(after.getString("title") == created.getString("title"))
      assert(version(after) == version(created) && arrayIds(after, "tags").isEmpty)
    }
  }

  test("mapper size validation rejects 21 incoming tags and rolls back other changes") {
    val browser = admin()
    val values = (1 to 21).map(index => tag(browser, s"Tag $index"))
    val created = json(create(browser, input())).getJsonObject("data")
    val id = UUID.fromString(created.getString("id"))
    val selected = json(patch(browser, id, s"""{"version":0,"tags":[${ref(values.head)}]}""")).getJsonObject("data")
    val failed = problem(patch(browser, id,
      s"""{"version":${version(selected)},"title":"Must roll back","tags":[${values.map(ref).mkString(",")}]}"""), 400)
    assert(fields(failed).contains("tags"))
    val after = data(browser, id)
    assert(after.getString("title") == created.getString("title"))
    assert(arrayIds(after, "tags") == Set(values.head.getString("id")))
    assert(version(after) == version(selected))
  }

  test("an already selected author must remain eligible when their ID is resubmitted") {
    val browser = admin()
    val otherEmail = account()
    val other = owned.last
    val id = UUID.fromString(json(create(browser, input())).getJsonObject("data").getString("id"))
    val assigned = json(patch(browser, id, s"""{"version":0,"author":{"id":"$other"}}""")).getJsonObject("data")
    changeRole(otherEmail, "READER")
    assert(fields(problem(patch(browser, id,
      s"""{"version":${version(assigned)},"author":{"id":"$other"}}"""), 400)).contains("author"))
    val available = json(browser.send("editorial/authors?limit=100"))
    val ids = available.value.get("rows").asInstanceOf[JsonArray].value.asScala
      .map(_.asInstanceOf[JsonObject].getJsonObject("data").getString("id"))
    assert(!ids.contains(other.toString))
    account(locked = true)
    val lockedId = owned.last
    problem(patch(browser, id, s"""{"version":${version(assigned)},"author":{"id":"$lockedId"}}"""), 400)
  }

  test("catalog writes enforce versions, scalar validation and the account allowlist") {
    val browser = admin()
    val shared = tag(browser)
    val target = s"editorial/tags/${shared.getString("id")}"
    val csrf = browser.session().getString("csrfToken")
    val changed = json(browser.send(target, "PATCH", """{"version":0,"name":"Renamed tag"}""", csrf)).getJsonObject("data")
    problem(browser.send(target, "PATCH", """{"version":0,"name":"Stale tag"}""", csrf), 409)
    assert(fields(problem(browser.send(target, "PATCH",
      s"""{"version":${version(changed)},"name":"x"}""", csrf), 400)).contains("name"))
    val self = browser.session().getJsonObject("account")
    val authorTarget = s"editorial/authors/${self.getString("id")}"
    problem(browser.send(authorTarget, "PATCH", s"""{"version":${version(self)},"role":"READER"}""", csrf), 400)
    problem(browser.send(authorTarget, "PATCH", s"""{"version":${version(self)},"email":"changed@example.com"}""", csrf), 400)
    assert(fields(problem(browser.send(authorTarget, "PATCH",
      s"""{"version":${version(self)},"displayName":"x"}""", csrf), 400)).contains("displayName"))
  }

  test("catalog endpoints require administrators and CSRF for writes") {
    val anonymous = new Browser()
    Seq("editorial/authors", "editorial/tags").foreach(path => problem(anonymous.send(path), 401))
    val browser = admin()
    val shared = tag(browser)
    problem(browser.send(s"editorial/tags/${shared.getString("id")}", "PATCH",
      """{"version":0,"name":"No CSRF"}"""), 403)
    problem(browser.send("editorial/tags", "POST", """{"slug":"no-csrf","name":"No CSRF"}"""), 403)
    val readerEmail = account()
    changeRole(readerEmail, "READER")
    val reader = new Browser()
    json(reader.login(readerEmail))
    Seq("editorial/authors", "editorial/tags").foreach(path => problem(reader.send(path), 403))
    problem(reader.send("editorial/tags", "POST", """{"slug":"forbidden","name":"Forbidden"}""",
      reader.session().getString("csrfToken")), 403)
  }

  test("catalog pages expose bounded rows, totals and links without private account fields") {
    val browser = admin()
    tag(browser, "First tag")
    tag(browser, "Second tag")
    Seq("editorial/authors", "editorial/tags").foreach { endpoint =>
      val page = json(browser.send(s"$endpoint?limit=1"))
      assert(page.value.get("rows").asInstanceOf[JsonArray].value.size() == 1)
      assert(page.value.get("size").value.toString.toLong >= 1)
      assert(relations(page).contains("self"))
      problem(browser.send(s"$endpoint?limit=101"), 400)
      problem(browser.send(s"$endpoint?offset=-1"), 400)
    }
    val authors = json(browser.send("editorial/authors?limit=100")).encode()
    assert(!authors.contains("\"email\"") && !authors.contains("\"passwordHash\"") && !authors.contains("\"role\""))
  }
}
