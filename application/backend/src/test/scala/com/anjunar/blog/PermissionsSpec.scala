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

class PermissionsSpec extends AnyFunSuite with BeforeAndAfterAll {
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
      if (method == "POST") builder.header("Content-Type", "application/json")
      builder.method(method, HttpRequest.BodyPublishers.ofString(body))
      client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }
    def session(): JsonObject = json(send("auth/session"))
    def login(email: String, secret: String = password): HttpResponse[String] =
      send("auth/login", "POST", new JsonObject().put("email", email).put("password", secret).encode(),
        session().getString("csrfToken"))
  }

  private def json(response: HttpResponse[String]): JsonObject = {
    assert(response.statusCode() == 200, response.body())
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

  test("anonymous and reader requests cannot enter editorial endpoints or forge commands") {
    val id = draft()
    val anonymous = new Browser()
    assert(anonymous.send("editorial/posts").statusCode() == 401)
    assert(anonymous.send(path(id)).statusCode() == 401)
    assert(action(anonymous, id, "publish").statusCode() == 401)
    assert(!relations(anonymous.session()).contains("editorial"))
    val email = account()
    changeRole(email, "READER")
    val reader = new Browser()
    json(reader.login(email))
    assert(reader.send("editorial/posts").statusCode() == 403)
    assert(reader.send(path(id)).statusCode() == 403)
    assert(action(reader, id, "publish").statusCode() == 403)
    assert(!relations(reader.session()).contains("editorial"))
  }

  test("admin preview, publication and retraction return fresh links and committed versions") {
    val id = draft()
    val browser = admin()
    assert(relations(browser.session()).contains("editorial"))
    val initial = json(browser.send(path(id)))
    assert(initial.getJsonObject("data").getString("content") == "A private body")
    assert(relations(initial) == Set("self", "update", "publish", "translation"))
    assert(browser.send(s"blog/posts/permission-$id").statusCode() == 404)
    val published = json(action(browser, id, "publish"))
    assert(published.getJsonObject("data").getString("status") == "PUBLISHED")
    assert(published.getJsonObject("data").value.get("version").value == "1")
    assert(relations(published) == Set("self", "update", "retract", "public", "translation"))
    assert(action(browser, id, "publish").statusCode() == 409)
    val publicResponse = browser.send(s"blog/posts/permission-$id")
    assert(publicResponse.headers().firstValue("Cache-Control").orElse("") == "no-store")
    assert(relations(json(publicResponse)) == Set("self", "preview"))
    val anonymous = new Browser()
    assert(relations(json(anonymous.send(s"blog/posts/permission-$id"))) == Set("self"))
    val retracted = json(action(browser, id, "retract"))
    assert(retracted.getJsonObject("data").getString("status") == "DRAFT")
    assert(retracted.getJsonObject("data").value.get("version").value == "2")
    assert(!retracted.getJsonObject("data").value.containsKey("publishedAt"))
    assert(relations(retracted) == Set("self", "update", "publish", "translation"))
    assert(action(browser, id, "retract").statusCode() == 409)
    assert(anonymous.send(s"blog/posts/permission-$id").statusCode() == 404)
  }

  test("empty drafts offer no publication command and direct calls fail") {
    val id = draft("")
    val browser = admin()
    assert(relations(json(browser.send(path(id)))) == Set("self", "update", "translation"))
    assert(action(browser, id, "publish").statusCode() == 409)
    assert(browser.send("editorial/posts/not-a-uuid").statusCode() == 404)
    assert(browser.send(path(UUID.randomUUID())).statusCode() == 404)
  }

  test("editorial collection validates bounds and returns graph projections with page links") {
    draft()
    val browser = admin()
    val first = json(browser.send("editorial/posts?offset=0&limit=1"))
    assert(relations(first).contains("next") && !relations(first).contains("previous"))
    val row = first.value.get("rows").asInstanceOf[JsonArray].value.get(0).asInstanceOf[JsonObject]
    assert(!row.getJsonObject("data").value.containsKey("content"))
    assert(relations(row).contains("self"))
    assert(row.getJsonObject("data").getString("contentLocale") == "en")
    val metadata = row.getJsonObject("schema").value.get("entries").asInstanceOf[JsonArray]
    assert(metadata.value.asScala.exists(_.asInstanceOf[JsonObject].getString("name") == "contentLocale"))
    assert(relations(json(browser.send("editorial/posts?offset=1&limit=1"))).contains("previous"))
    Seq("offset=-1", "limit=0", "limit=101", "offset=bad").foreach { query =>
      assert(browser.send(s"editorial/posts?$query").statusCode() == 400)
    }
  }

  test("CSRF and a changed database role invalidate previously advertised actions") {
    val id = draft()
    val email = account()
    val browser = new Browser()
    json(browser.login(email))
    assert(relations(json(browser.send(path(id)))).contains("publish"))
    assert(browser.send(s"${path(id)}/publish", "POST").statusCode() == 403)
    assert(action(browser, id, "publish", Map("Sec-Fetch-Site" -> "cross-site")).statusCode() == 403)
    changeRole(email, "READER")
    assert(!relations(browser.session()).contains("editorial"))
    assert(action(browser, id, "publish").statusCode() == 403)
    changeRole(email, "ADMIN")
    assert(json(browser.send(path(id))).getJsonObject("data").getString("status") == "DRAFT")
  }

  test("writer and commit failures roll back publication and its version") {
    val browser = admin()
    for (failure <- Seq("writer", "commit")) {
      val id = draft()
      assert(action(browser, id, "publish", Map("X-Test-Auth-Failure" -> failure)).statusCode() == 500)
      val unchanged = json(browser.send(path(id)))
      assert(unchanged.getJsonObject("data").getString("status") == "DRAFT")
      assert(unchanged.getJsonObject("data").value.get("version").value == "0")
      assert(relations(unchanged) == Set("self", "update", "publish", "translation"))
    }
  }

  test("concurrent publication locks the row and commits only one transition") {
    val id = draft()
    val first = admin()
    val second = admin()
    val a = first.session().getString("csrfToken")
    val b = second.session().getString("csrfToken")
    val executor = Executors.newFixedThreadPool(2)
    try {
      val tasks = Seq((first, a), (second, b)).map { (browser, token) =>
        executor.submit(new Callable[Int] {
          override def call(): Int = browser.send(s"${path(id)}/publish", "POST", csrf = token).statusCode()
        })
      }
      assert(tasks.map(_.get()).sorted == Seq(200, 409))
      assert(json(first.send(path(id))).getJsonObject("data").value.get("version").value == "1")
    } finally executor.close()
  }

  test("mapper rules resolve the current caller and keep lifecycle fields read-only") {
    val anonymous = new Browser()
    assert(anonymous.send("_test/permissions", extra = Map("Accept" -> "text/plain")).body() == "Original title|-1|DRAFT|null")
    val email = account()
    changeRole(email, "READER")
    val reader = new Browser()
    json(reader.login(email))
    assert(reader.send("_test/permissions", extra = Map("Accept" -> "text/plain")).body() == "Original title|-1|DRAFT|null")
    val browser = admin()
    assert(browser.send("_test/permissions", extra = Map("Accept" -> "text/plain")).body() == "Changed title|-1|DRAFT|null")
    // A cached schema must not retain the previous administrator's rights.
    assert(anonymous.send("_test/permissions", extra = Map("Accept" -> "text/plain")).body() == "Original title|-1|DRAFT|null")
  }
}
