package com.anjunar.blog

import com.anjunar.json.mapper.intermediate.JsonParser
import com.anjunar.json.mapper.intermediate.model.JsonObject
import dev.resteasy.embedded.server.UndertowCdiEmbeddedServer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

import java.net.{CookieManager, CookiePolicy, InetAddress, ServerSocket, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.sql.DriverManager
import java.time.Duration
import java.util.UUID
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.Using

class AuthenticationSpec extends AnyFunSuite with BeforeAndAfterAll {
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
      extra.foreach((key, value) => builder.header(key, value))
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

  test("anonymous session supplies CSRF, private cache headers and a protected cookie") {
    val browser = new Browser()
    val response = browser.send("auth/session")
    val state = json(response)
    assert(state.getString("csrfToken").length == 43)
    assert(!state.value.containsKey("account"))
    assert(response.headers().firstValue("Cache-Control").orElse("") == "no-store")
    val cookie = response.headers().firstValue("Set-Cookie").orElse("")
    assert(cookie.contains("BLOGSESSION=") && cookie.contains("HttpOnly") && cookie.contains("SameSite=Lax"))
    assert(cookie.contains("Path=/") && !cookie.contains("Domain="))
    assert(browser.send("auth/me").statusCode() == 401)
    assert(browser.session().getString("csrfToken") == state.getString("csrfToken"))
  }

  test("login rotates cookie and CSRF, preserves version zero and never exposes secrets") {
    val email = account()
    val browser = new Browser()
    val before = browser.session().getString("csrfToken")
    val oldCookie = browser.cookie
    val state = json(browser.login("  " + email.toUpperCase + "  "))
    assert(browser.cookie != oldCookie)
    assert(state.getString("csrfToken") != before)
    val value = state.getJsonObject("account")
    assert(value.getString("email") == email)
    assert(value.getString("role") == "ADMIN")
    assert(value.value.get("version").value == "0")
    assert(value.value.keySet().asScala.toSet == Set("id", "version", "email", "role", "@type"))
    val me = browser.send("auth/me")
    assert(json(me).getJsonObject("data").getString("email") == email)
    assert(!me.body().contains(hash) && !me.body().contains("passwordHash") && !me.body().contains("authenticationVersion"))
    val anonymous = new Browser()
    assert(anonymous.send("auth/me", extra = Map("Cookie" -> s"BLOGSESSION=$oldCookie")).statusCode() == 401)
  }

  test("wrong, unknown and locked credentials have the same failure and remain anonymous") {
    for ((email, secret) <- Seq((account(), "incorrect password"), (s"${UUID.randomUUID()}@example.com", password),
      (account(locked = true), password))) {
      val browser = new Browser()
      val response = browser.login(email, secret)
      assert(response.statusCode() == 401)
      assert(!response.body().contains(email) && !response.body().contains(hash))
      assert(browser.send("auth/me").statusCode() == 401)
    }
  }

  test("login and logout require the session's CSRF token and reject cross-site metadata") {
    val email = account()
    val browser = new Browser()
    val body = new JsonObject().put("email", email).put("password", password).encode()
    assert(browser.send("auth/login", "POST", body).statusCode() == 403)
    val token = browser.session().getString("csrfToken")
    assert(browser.send("auth/login", "POST", body, "wrong").statusCode() == 403)
    assert(browser.send("auth/login", "POST", body, token, Map("Sec-Fetch-Site" -> "cross-site")).statusCode() == 403)
    val other = new Browser()
    assert(other.send("auth/login", "POST", body, token).statusCode() == 403)
    json(browser.login(email))
    assert(browser.send("auth/logout", "POST").statusCode() == 403)
    assert(browser.send("auth/me").statusCode() == 200)
  }

  test("logout invalidates the authenticated session and returns a fresh anonymous token") {
    val browser = new Browser()
    val state = json(browser.login(account()))
    val oldCookie = browser.cookie
    val loggedOut = json(browser.send("auth/logout", "POST", csrf = state.getString("csrfToken")))
    assert(!loggedOut.value.containsKey("account"))
    assert(loggedOut.getString("csrfToken") != state.getString("csrfToken"))
    assert(browser.cookie != oldCookie)
    assert(browser.send("auth/me").statusCode() == 401)
    val attacker = new Browser()
    assert(attacker.send("auth/me", extra = Map("Cookie" -> s"BLOGSESSION=$oldCookie")).statusCode() == 401)
  }

  test("a credential version change revokes an already authenticated session") {
    val email = account()
    val browser = new Browser()
    json(browser.login(email))
    Using.resource(connection()) { connection =>
      Using.resource(connection.prepareStatement(
        "update public.blog_account set authentication_version = authentication_version + 1 where email = ?")) { query =>
        query.setString(1, email)
        query.executeUpdate()
      }
    }
    assert(browser.send("auth/me").statusCode() == 401)
    assert(!browser.session().value.containsKey("account"))
  }

  test("locking an account revokes an existing session") {
    val email = account()
    val browser = new Browser()
    json(browser.login(email))
    Using.resource(connection()) { connection =>
      Using.resource(connection.prepareStatement("update public.blog_account set locked = true where email = ?")) { query =>
        query.setString(1, email)
        query.executeUpdate()
      }
    }
    assert(browser.send("auth/me").statusCode() == 401)
  }

  test("the credential command rejects extra fields, malformed JSON and oversized bodies") {
    val browser = new Browser()
    val token = browser.session().getString("csrfToken")
    for (body <- Seq("{}", "null", "{broken", """{"email":"a@example.com","password":42}""",
      """{"email":"a@example.com","password":"anything","role":"ADMIN"}""")) {
      assert(browser.send("auth/login", "POST", body, token).statusCode() == 400)
    }
    assert(browser.send("auth/login", "POST", "x" * 4097, token).statusCode() == 413)
  }

  test("HTTP sign-in attempts are throttled even for unknown accounts") {
    val browser = new Browser()
    val email = s"${UUID.randomUUID()}@example.com"
    for (_ <- 1 to 5) assert(browser.login(email).statusCode() == 401)
    val blocked = browser.login(email)
    assert(blocked.statusCode() == 429)
    assert(blocked.headers().firstValue("Retry-After").orElse("") == "60")
  }

  test("URL session tracking is disabled") {
    val browser = new Browser()
    json(browser.login(account()))
    val other = new Browser()
    assert(other.send(s"auth/me;jsessionid=${browser.cookie}").statusCode() != 200)
  }

  test("secure cookies are the default") {
    server.stop()
    server = null
    val securePort = freePort()
    val secureServer = ApplicationMain.start(securePort, security = SecurityConfig())
    try {
      Using.resource(HttpClient.newHttpClient()) { client =>
        val response = client.send(HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$securePort/service/auth/session"))
          .GET().build(), HttpResponse.BodyHandlers.ofString())
        assert(response.statusCode() == 200)
        assert(response.headers().firstValue("Set-Cookie").orElse("").contains("Secure"))
      }
    } finally secureServer.stop()
  }
}
