package com.anjunar.blog

import com.anjunar.json.mapper.intermediate.JsonParser
import com.anjunar.json.mapper.intermediate.model.JsonObject
import dev.resteasy.embedded.server.UndertowCdiEmbeddedServer
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.funsuite.AnyFunSuite

import java.net.{CookieManager, CookiePolicy, InetAddress, ServerSocket, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.sql.{Connection, DriverManager}
import java.time.Duration
import java.util.UUID
import java.util.concurrent.{Callable, Executors}
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.Using

class AccountRecoverySpec extends AnyFunSuite with BeforeAndAfterAll with BeforeAndAfterEach {
  private var server: UndertowCdiEmbeddedServer = null
  private var smtp: SmtpInbox = null
  private var port = 0
  private val owned = mutable.Buffer.empty[String]
  private val clients = mutable.Buffer.empty[HttpClient]
  private val password = "a long registration passphrase"
  private val replacement = "a different recovery passphrase"

  private def database[A](work: Connection => A): A = {
    val config = DatabaseConfig.load()
    Using.resource(DriverManager.getConnection(config.url, config.user, config.password))(work)
  }
  private def email(): String = {
    val value = s"recovery-${UUID.randomUUID()}@example.test"
    owned += value
    value
  }
  override protected def beforeAll(): Unit = {
    super.beforeAll()
    require(sys.env.get("BLOG_SMTP_HOST").contains("127.0.0.1") &&
      sys.env.get("BLOG_SMTP_MODE").contains("local"), "Recovery tests require the local SMTP capture configuration")
    smtp = new SmtpInbox(sys.env.getOrElse("BLOG_SMTP_PORT", "1025").toInt)
  }
  override protected def beforeEach(): Unit = {
    super.beforeEach()
    val socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    port = try socket.getLocalPort finally socket.close()
    server = ApplicationMain.start(port, security = SecurityConfig(false))
  }
  override protected def afterEach(): Unit = {
    try {
      clients.foreach(_.close()); clients.clear()
      if (server != null) server.stop()
      assert(smtp.empty, "A test left an unexpected account email")
    } finally super.afterEach()
  }
  override protected def afterAll(): Unit = try {
    if (smtp != null) smtp.close()
    database { connection =>
      for (table <- Seq("blog_account_token", "blog_account"))
        Using.resource(connection.prepareStatement(s"delete from public.$table where email = ?")) { query =>
          owned.foreach { value => query.setString(1, value); query.executeUpdate() }
        }
    }
  } finally super.afterAll()

  private final class Browser {
    private val cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL)
    private val client = HttpClient.newBuilder().cookieHandler(cookies).build()
    clients += client
    def send(path: String, body: Option[String] = None, csrf: String = "",
        extra: Map[String, String] = Map.empty): HttpResponse[String] = {
      val builder = HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port/service/auth/$path"))
        .timeout(Duration.ofSeconds(20)).header("Accept", "application/json")
      if (csrf.nonEmpty) builder.header("X-CSRF-Token", csrf)
      extra.foreach((key, value) => builder.setHeader(key, value))
      body.foreach(value => builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(value)))
      client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }
    def csrf(): String = JsonParser.parse(send("session").body()).asInstanceOf[JsonObject].getString("csrfToken")
    def post(path: String, body: String, extra: Map[String, String] = Map.empty): HttpResponse[String] =
      send(path, Some(body), csrf(), extra)
    def login(value: String, secret: String): Int =
      post("login", new JsonObject().put("email", value).put("password", secret).encode()).statusCode()
  }
  private def address(value: String): String = new JsonObject().put("email", value).encode()
  private def completion(token: String, secret: String = password): String =
    new JsonObject().put("token", token).put("password", secret).encode()
  private def link(value: String, browser: Browser, purpose: String = "register"): String = {
    val response = browser.post(purpose, address(value))
    assert(response.statusCode() == 202, response.body())
    assert(response.headers().firstValue("Cache-Control").orElse("") == "no-store")
    val message = smtp.next()
    assert(message.getAllRecipients.head.toString == value)
    val text = message.getContent.toString
    assert(text.contains(sys.env("BLOG_PUBLIC_ORIGIN") + "/en/"))
    "token=([A-Za-z0-9_-]{43})".r.findFirstMatchIn(text).get.group(1)
  }
  private def registered(browser: Browser): String = {
    val value = email()
    val token = link(value, browser)
    assert(browser.post("confirm", completion(token)).statusCode() == 200)
    value
  }
  private def count(table: String, value: String): Long = database { connection =>
    Using.resource(connection.prepareStatement(s"select count(*) from public.$table where email = ?")) { query =>
      query.setString(1, value)
      Using.resource(query.executeQuery()) { rows => rows.next(); rows.getLong(1) }
    }
  }

  test("registration verifies email before choosing a password and creates only a reader") {
    val browser = new Browser()
    val value = email()
    val token = link(value, browser)
    assert(count("blog_account", value) == 0)
    assert(browser.login(value, password) == 401)
    database { connection =>
      Using.resource(connection.prepareStatement("select digest from public.blog_account_token where email = ?")) { query =>
        query.setString(1, value)
        Using.resource(query.executeQuery()) { rows =>
          assert(rows.next()); assert(rows.getString(1) == AccountToken.digest(token)); assert(rows.getString(1) != token)
        }
      }
    }
    val result = browser.post("confirm", completion(token))
    assert(result.statusCode() == 200, result.body())
    assert(!result.body().contains(token) && !result.body().contains(password))
    assert(browser.send("me").statusCode() == 401)
    assert(browser.post("confirm", completion(token)).statusCode() == 400)
    assert(browser.login(value, password) == 200)
    assert(browser.send("me").body().contains("READER"))
    assert(count("blog_account_token", value) == 0)
  }

  test("existing, unknown and locked addresses receive the same generic response") {
    val browser = new Browser()
    val value = registered(browser)
    val unknown = email()
    val existing = browser.post("register", address(value))
    val absent = browser.post("forgot-password", address(unknown))
    assert(existing.statusCode() == 202 && absent.statusCode() == 202)
    assert(existing.body() == absent.body())
    database { connection =>
      Using.resource(connection.prepareStatement("update public.blog_account set locked = true where email = ?")) { query =>
        query.setString(1, value); query.executeUpdate()
      }
    }
    val locked = browser.post("forgot-password", address(value))
    assert(locked.statusCode() == 202 && locked.body() == absent.body())
    assert(count("blog_account_token", unknown) == 0)
  }

  test("a new link invalidates the old link and purposes cannot be exchanged") {
    val browser = new Browser()
    val value = email()
    val old = link(value, browser)
    val fresh = link(value, browser)
    assert(browser.post("confirm", completion(old)).statusCode() == 400)
    assert(browser.post("reset-password", completion(fresh)).statusCode() == 400)
    assert(browser.post("confirm", completion(fresh)).statusCode() == 200)
    val reset = link(value, browser, "forgot-password")
    assert(browser.post("confirm", completion(reset)).statusCode() == 400)
    assert(browser.post("reset-password", completion(reset, replacement)).statusCode() == 200)
  }

  test("expired and forged tokens cannot create an account") {
    val browser = new Browser()
    val value = email()
    val token = link(value, browser)
    database { connection =>
      Using.resource(connection.prepareStatement("update public.blog_account_token set expires_at = now() - interval '1 second' where email = ?")) { query =>
        query.setString(1, value); query.executeUpdate()
      }
    }
    assert(browser.post("confirm", completion(token)).statusCode() == 400)
    assert(browser.post("confirm", completion(SessionIdentity.newToken())).statusCode() == 400)
    assert(count("blog_account", value) == 0)
  }

  test("reset changes the password, consumes the link and revokes all existing sessions") {
    val browser = new Browser()
    val value = registered(browser)
    val other = new Browser()
    assert(browser.login(value, password) == 200)
    assert(other.login(value, password) == 200)
    val resetter = new Browser()
    val token = link(value, resetter, "forgot-password")
    assert(resetter.post("reset-password", completion(token, replacement)).statusCode() == 200)
    assert(browser.send("me").statusCode() == 401)
    assert(other.send("me").statusCode() == 401)
    assert(resetter.send("me").statusCode() == 401)
    assert(resetter.post("reset-password", completion(token, replacement)).statusCode() == 400)
    assert(resetter.login(value, password) == 401)
    assert(resetter.login(value, replacement) == 200)
  }

  test("lock or credential changes invalidate previously issued reset links") {
    val browser = new Browser()
    for (column <- Seq("locked = true", "authentication_version = authentication_version + 1")) {
      val value = registered(browser)
      val token = link(value, browser, "forgot-password")
      database { connection =>
        Using.resource(connection.prepareStatement(s"update public.blog_account set $column where email = ?")) { query =>
          query.setString(1, value); query.executeUpdate()
        }
      }
      assert(browser.post("reset-password", completion(token, replacement)).statusCode() == 400)
    }
  }

  test("CSRF, command boundaries and mail throttling cover all recovery endpoints") {
    val browser = new Browser()
    val value = email()
    for (endpoint <- Seq("register", "forgot-password", "confirm", "reset-password"))
      assert(browser.send(endpoint, Some("{}")).statusCode() == 403)
    for (body <- Seq("{}", "null", "{broken", address("not-an-email"),
      """{"email":"test@example.com","role":"ADMIN"}""", """{"email":"a@example.com,b@example.com"}"""))
      assert(browser.post("register", body).statusCode() == 400)
    assert(browser.post("register", "x" * 4097).statusCode() == 413)
    assert(browser.post("register", address(value), Map("Sec-Fetch-Site" -> "cross-site")).statusCode() == 403)
    assert(browser.post("confirm", completion(SessionIdentity.newToken(), "too short")).statusCode() == 400)
    for (_ <- 1 to 5) assert(browser.post("forgot-password", address(value)).statusCode() == 202)
    val throttled = browser.post("forgot-password", address(value))
    assert(throttled.statusCode() == 429)
    assert(throttled.headers().firstValue("Retry-After").orElse("") == "60")
  }

  test("response and commit failures do not send mail or consume a confirmation link") {
    val browser = new Browser()
    for (failure <- Seq("writer", "commit")) {
      val value = email()
      assert(browser.post("register", address(value), Map("X-Test-Auth-Failure" -> failure)).statusCode() == 500)
      assert(count("blog_account_token", value) == 0)
      val token = link(value, browser)
      assert(browser.post("confirm", completion(token), Map("X-Test-Auth-Failure" -> failure)).statusCode() == 500)
      assert(count("blog_account", value) == 0)
      assert(count("blog_account_token", value) == 1)
      assert(browser.post("confirm", completion(token)).statusCode() == 200)
    }
  }

  test("a reset rollback preserves the password, token and authenticated session") {
    val browser = new Browser()
    val value = registered(browser)
    assert(browser.login(value, password) == 200)
    val resetter = new Browser()
    val token = link(value, resetter, "forgot-password")
    for (failure <- Seq("writer", "commit")) {
      assert(resetter.post("reset-password", completion(token, replacement),
        Map("X-Test-Auth-Failure" -> failure)).statusCode() == 500)
      assert(browser.send("me").statusCode() == 200)
      assert(count("blog_account_token", value) == 1)
    }
    assert(resetter.post("reset-password", completion(token, replacement)).statusCode() == 200)
    assert(browser.send("me").statusCode() == 401)
  }

  test("two simultaneous uses of one registration token produce exactly one account") {
    val first = new Browser()
    val second = new Browser()
    val value = email()
    val token = link(value, first)
    val csrf1 = first.csrf()
    val csrf2 = second.csrf()
    Using.resource(Executors.newFixedThreadPool(2)) { executor =>
      val tasks = Seq[Callable[Int]](
        () => first.send("confirm", Some(completion(token)), csrf1).statusCode(),
        () => second.send("confirm", Some(completion(token)), csrf2).statusCode())
      val statuses = executor.invokeAll(tasks.asJava).asScala.map(_.get()).sorted
      assert(statuses == Seq(200, 400))
    }
    assert(count("blog_account", value) == 1)
  }
}
