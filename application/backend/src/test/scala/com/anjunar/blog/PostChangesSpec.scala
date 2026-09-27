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

class PostChangesSpec extends AnyFunSuite with BeforeAndAfterAll {
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
    Using.resource(connection()) { connection =>
      Using.resource(connection.prepareStatement("delete from public.blog_account where id = ?")) { query =>
        owned.foreach { id => query.setObject(1, id); query.executeUpdate() }
      }
    }
    Using.resource(connection()) { c =>
      Using.resource(c.prepareStatement("delete from public.blog_post where id = ?")) { q =>
        posts.foreach { id => q.setObject(1, id); q.executeUpdate() }
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

  test("create returns 201, Location, server identity, version zero and the complete detail contract") {
    val browser = admin()
    val command = input()
    command.put("status", "PUBLISHED").put("publishedAt", "2026-09-27T10:00:00Z")
    val response = create(browser, command)
    assert(response.statusCode() == 201, response.body())
    val result = json(response)
    val post = result.getJsonObject("data")
    assert(post.getString("slug") == command.getString("slug"))
    assert(post.getString("content") == "Original body")
    assert(post.getString("status") == "DRAFT" && !post.value.containsKey("publishedAt"))
    assert(post.value.get("version").value == "0")
    assert(URI.create(response.headers().firstValue("Location").orElseThrow()).getPath ==
      s"/service/editorial/posts/${post.getString("id")}")
    assert(relations(result) == Set("self", "update", "publish"))
    assert(relations(json(browser.send("editorial/posts"))).contains("create"))
    assert(browser.send(s"blog/posts/${post.getString("slug")}").statusCode() == 404)
  }

  test("PATCH preserves omitted fields, clears an explicit null and returns the next version") {
    val browser = admin()
    val id = draft()
    val initial = json(browser.send(path(id))).getJsonObject("data")
    val first = json(patch(browser, id, """{"version":0,"title":"Changed title","summary":"An introduction"}"""))
      .getJsonObject("data")
    assert(first.getString("title") == "Changed title" && first.getString("summary") == "An introduction")
    assert(first.getString("slug") == initial.getString("slug") && first.getString("content") == "A private body")
    assert(first.value.get("version").value == "1")
    val second = json(patch(browser, id, """{"version":1,"summary":null}""")).getJsonObject("data")
    assert(!second.value.containsKey("summary") && second.value.get("version").value == "2")
    assert(json(browser.send(path(id))).getJsonObject("data").getString("title") == "Changed title")
  }

  test("new posts validate omitted required values and retain the legal empty-draft default") {
    val browser = admin()
    val missing = new JsonObject().put("slug", s"missing-${UUID.randomUUID()}")
    assert(fields(problem(create(browser, missing), 400)).contains("title"))
    val short = input().put("title", "x")
    assert(fields(problem(create(browser, short), 400)).contains("title"))
    val badSlug = input().put("slug", "Invalid Slug!")
    assert(fields(problem(create(browser, badSlug), 400)).contains("slug"))
    val valid = input()
    valid.value.remove("content")
    val created = json(create(browser, valid)).getJsonObject("data")
    assert(!created.value.containsKey("content") && created.getString("status") == "DRAFT")
  }

  test("version is a required nonnegative JSON integer; identity cannot be reassigned") {
    val browser = admin()
    val id = draft()
    Seq("{}", """{"version":null}""", """{"version":"0"}""", """{"version":-1}""",
      """{"version":0.5}""", """{"version":9223372036854775808}""").foreach { body =>
      assert(fields(problem(patch(browser, id, body), 400)).contains("version"))
    }
    assert(fields(problem(patch(browser, id,
      s"""{"version":0,"id":"${UUID.randomUUID()}"}"""), 400)).contains("id"))
    assert(fields(problem(create(browser, input().put("id", UUID.randomUUID().toString)), 400)).contains("id"))
    assert(fields(problem(create(browser, input().put("version", Int.box(99))), 400)).contains("version"))
    val valid = json(patch(browser, id, s"""{"version":0,"id":"$id","title":"Matching identity"}"""))
    assert(valid.getJsonObject("data").getString("id") == id.toString)
  }

  test("stale edits fail with a conflict instead of overwriting a committed change") {
    val browser = admin()
    val id = draft()
    json(patch(browser, id, """{"version":0,"title":"First editor saved"}"""))
    val stale = problem(patch(browser, id, """{"version":0,"title":"Stale editor saved"}"""), 409)
    assert(stale.getString("type") == Problem.conflict)
    val saved = json(browser.send(path(id))).getJsonObject("data")
    assert(saved.getString("title") == "First editor saved" && saved.value.get("version").value == "1")
    problem(patch(browser, UUID.randomUUID(), """{"version":0}"""), 404)
  }

  test("two simultaneous edits of the same version commit only one update") {
    val id = draft()
    val first = admin()
    val second = admin()
    val tokens = Seq(first, second).map(browser => browser -> browser.session().getString("csrfToken"))
    val executor = Executors.newFixedThreadPool(2)
    try {
      val pending = tokens.zipWithIndex.map { case ((browser, token), index) =>
        executor.submit(new Callable[Int] {
          override def call(): Int = browser.send(path(id), "PATCH",
            s"""{"version":0,"title":"Concurrent editor $index"}""", token).statusCode()
        })
      }
      assert(pending.map(_.get()).sorted == Seq(200, 409))
      assert(json(first.send(path(id))).getJsonObject("data").value.get("version").value == "1")
    } finally executor.close()
  }

  test("field validation rolls back other assignments from the same request") {
    val browser = admin()
    val id = draft()
    val original = json(browser.send(path(id))).getJsonObject("data").getString("title")
    val command = new JsonObject().put("version", Int.box(0)).put("title", "A valid new title")
      .put("summary", "x" * 301)
    assert(fields(problem(patch(browser, id, command.encode()), 400)).contains("summary"))
    val saved = json(browser.send(path(id))).getJsonObject("data")
    assert(saved.getString("title") == original && saved.value.get("version").value == "0")
  }

  test("whole-entity validation protects the published state after a partial edit") {
    val browser = admin()
    val id = draft()
    json(action(browser, id, "publish"))
    val original = json(browser.send(path(id))).getJsonObject("data").getString("title")
    val response = patch(browser, id, """{"version":1,"title":"Would otherwise be valid","content":""}""")
    assert(fields(problem(response, 400)).contains("publicationConsistent"))
    val saved = json(browser.send(path(id))).getJsonObject("data")
    assert(saved.getString("title") == original && saved.getString("content") == "A private body")
    assert(saved.getString("status") == "PUBLISHED" && saved.value.get("version").value == "1")
  }

  test("read-only fields stay unchanged while editable fields use the mapper rules") {
    val browser = admin()
    val id = draft()
    val result = json(patch(browser, id,
      """{"version":0,"title":"Allowed title","status":"PUBLISHED","publishedAt":"2026-09-27T10:00:00Z"}"""))
      .getJsonObject("data")
    assert(result.getString("title") == "Allowed title")
    assert(result.getString("status") == "DRAFT" && !result.value.containsKey("publishedAt"))
    assert(result.value.get("version").value == "1")
  }

  test("duplicate slugs produce a field conflict without changing either post") {
    val browser = admin()
    val first = json(create(browser, input())).getJsonObject("data")
    val second = json(create(browser, input())).getJsonObject("data")
    val slug = first.getString("slug")
    assert(fields(problem(create(browser, input(slug)), 409)).contains("slug"))
    val id = UUID.fromString(second.getString("id"))
    val command = new JsonObject().put("version", Int.box(0)).put("slug", slug).put("title", "Must roll back")
    assert(fields(problem(patch(browser, id, command.encode()), 409)).contains("slug"))
    val saved = json(browser.send(path(id))).getJsonObject("data")
    assert(saved.getString("slug") == second.getString("slug") && saved.value.get("version").value == "0")
  }

  test("concurrent creates sharing a slug yield one created post and one conflict") {
    val browser = admin()
    val other = admin()
    val tokenA = browser.session().getString("csrfToken")
    val tokenB = other.session().getString("csrfToken")
    val body = input().encode()
    val executor = Executors.newFixedThreadPool(2)
    try {
      val pending = Seq((browser, tokenA), (other, tokenB)).map { (client, token) =>
        executor.submit(new Callable[HttpResponse[String]] {
          override def call() = client.send("editorial/posts", "POST", body, token)
        })
      }
      val responses = pending.map(_.get())
      responses.filter(_.statusCode() == 201).foreach { response =>
        posts += UUID.fromString(json(response).getJsonObject("data").getString("id"))
      }
      assert(responses.map(_.statusCode()).sorted == Seq(201, 409))
      problem(responses.find(_.statusCode() == 409).get, 409)
    } finally executor.close()
  }

  test("roles and CSRF are enforced before parsing or applying input") {
    val id = draft()
    val anonymous = new Browser()
    problem(anonymous.send("editorial/posts", "POST", "{broken"), 401)
    problem(anonymous.send(path(id), "PATCH", "{broken"), 401)
    val email = account()
    changeRole(email, "READER")
    val reader = new Browser()
    json(reader.login(email))
    problem(reader.send("editorial/posts", "POST", "{broken"), 403)
    problem(reader.send(path(id), "PATCH", "{broken"), 403)
    val browser = admin()
    problem(browser.send("editorial/posts", "POST", input().encode()), 403)
    problem(browser.send(path(id), "PATCH", """{"version":0,"title":"Forbidden"}"""), 403)
    problem(patch(browser, id, """{"version":0,"title":"Forbidden"}""", Map("Sec-Fetch-Site" -> "cross-site")), 403)
    assert(json(browser.send(path(id))).getJsonObject("data").value.get("version").value == "0")
  }

  test("malformed, duplicate, oversized and unknown input fails without mutation") {
    val browser = admin()
    val id = draft()
    Seq("[]", "null", "{", """{"version":0}{}""", """{"version":0,"version":1}""",
      """{"version":0,"title":{"$ref":"another-post"}}""", """{"version":0,"title":42}""",
      """{"version":0,"@type":"Account"}""", """{"version":0,"author":{"id":"missing"}}""").foreach { body =>
      problem(patch(browser, id, body), 400)
    }
    problem(patch(browser, id, " " * (RequestJson.maxBytes + 1)), 413)
    val saved = json(browser.send(path(id))).getJsonObject("data")
    assert(saved.value.get("version").value == "0")
  }

  test("writer or commit failure rolls back a valid edit and keeps its version") {
    val browser = admin()
    for (failure <- Seq("writer", "commit")) {
      val id = draft()
      val initial = json(browser.send(path(id))).getJsonObject("data")
      val response = patch(browser, id, """{"version":0,"title":"Must not survive"}""",
        Map("X-Test-Auth-Failure" -> failure))
      val error = problem(response, 500)
      assert(error.getString("errorId") != null)
      val saved = json(browser.send(path(id))).getJsonObject("data")
      assert(saved.getString("title") == initial.getString("title") && saved.value.get("version").value == "0")
    }
  }

  test("preparation leaves the original entity untouched for authorization and successful apply is single-use") {
    val browser = admin()
    val id = draft()
    val original = json(browser.send(path(id))).getJsonObject("data").getString("title")
    val denied = browser.send(s"_test/changes/$id", "PATCH",
      """{"version":0,"title":"Must not be assigned before authorization"}""", browser.session().getString("csrfToken"))
    assert(denied.statusCode() == 403)
    assert(denied.headers().firstValue("X-Test-Original-Title").orElse("") == original)
    assert(json(browser.send(path(id))).getJsonObject("data").value.get("version").value == "0")
    val single = browser.send(s"_test/changes/$id/apply", "PATCH",
      """{"version":0,"title":"Apply only once"}""", browser.session().getString("csrfToken"), Map("Accept" -> "text/plain"))
    assert(single.statusCode() == 200 && single.body() == "single-use")
  }

  test("a no-op keeps its version while an edit after publication uses the new version") {
    val browser = admin()
    val id = draft()
    assert(json(patch(browser, id, """{"version":0}""")).getJsonObject("data").value.get("version").value == "0")
    json(action(browser, id, "publish"))
    problem(patch(browser, id, """{"version":0,"title":"Old version"}"""), 409)
    val updated = json(patch(browser, id, """{"version":1,"title":"New public title"}""")).getJsonObject("data")
    assert(updated.getString("status") == "PUBLISHED" && updated.value.get("version").value == "2")
  }
}
