package com.anjunar.blog

import com.anjunar.json.mapper.intermediate.JsonParser
import com.anjunar.json.mapper.intermediate.model.{JsonArray, JsonObject}
import dev.resteasy.embedded.server.UndertowCdiEmbeddedServer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.{CookieManager, CookiePolicy, InetAddress, ServerSocket, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.sql.DriverManager
import java.time.Duration
import java.util.UUID
import java.util.concurrent.{Callable, Executors}
import javax.imageio.ImageIO
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.Using

class PostTranslationSpec extends AnyFunSuite with BeforeAndAfterAll {
  private var server: UndertowCdiEmbeddedServer = null
  private var port = 0
  private val accounts = mutable.Buffer.empty[UUID]
  private val posts = mutable.Buffer.empty[UUID]
  private val images = mutable.Buffer.empty[UUID]
  private val clients = mutable.Buffer.empty[HttpClient]
  private val password = "a long tutorial passphrase"
  private lazy val hash = PasswordHash.create(password)
  private def connection() = {
    val config = DatabaseConfig.load()
    DriverManager.getConnection(config.url, config.user, config.password)
  }
  private def sql(statement: String, values: Any*): Unit = Using.resource(connection()) { c =>
    Using.resource(c.prepareStatement(statement)) { query =>
      values.zipWithIndex.foreach((value, index) => query.setObject(index + 1, value))
      query.executeUpdate()
    }
  }
  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    port = socket.getLocalPort
    socket.close()
    server = ApplicationMain.start(port, security = SecurityConfig(false))
  }
  override protected def afterAll(): Unit = try {
    clients.foreach(_.close())
    if (server != null) server.stop()
  } finally {
    posts.foreach { id =>
      sql("delete from public.blog_post_translation_media where translation_id in (select id from public.blog_post_translation where post_id = ?)", id)
      sql("delete from public.blog_post_translation where post_id = ?", id)
      sql("delete from public.blog_post where id = ?", id)
    }
    images.foreach(id => sql("delete from public.blog_media where id = ?", id))
    accounts.foreach(id => sql("delete from public.blog_account where id = ?", id))
    super.afterAll()
  }
  private final class Browser {
    val client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build()
    clients += client
    def send(path: String, method: String = "GET", body: String = "", csrf: String = ""): HttpResponse[String] = {
      val request = HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port/service/$path"))
        .timeout(Duration.ofSeconds(20)).header("Accept", "*/*")
      if (csrf.nonEmpty) request.header("X-CSRF-Token", csrf)
      if (method == "POST" || method == "PATCH") request.header("Content-Type", "application/json")
      client.send(request.method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString())
    }
    def token: String = json(send("auth/session")).getString("csrfToken")
    def write(path: String, method: String, body: String): HttpResponse[String] = send(path, method, body, token)
    def upload(): UUID = {
      val bytes = new ByteArrayOutputStream()
      ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", bytes)
      val request = HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port/service/editorial/media"))
        .header("Content-Type", "image/png").header("X-CSRF-Token", token)
        .POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray)).build()
      val id = UUID.fromString(json(client.send(request, HttpResponse.BodyHandlers.ofString())).getJsonObject("data").getString("id"))
      images += id
      id
    }
  }
  private def admin(role: String = "ADMIN"): Browser = {
    val id = UUID.randomUUID()
    accounts += id
    val email = s"translation-$id@example.com"
    sql("insert into public.blog_account (id, version, email, role, password_hash, authentication_version, locked) values (?, 0, ?, ?, ?, 0, false)",
      id, email, role, hash)
    val browser = new Browser()
    json(browser.write("auth/login", "POST", new JsonObject().put("email", email).put("password", password).encode()))
    browser
  }
  private def post(published: Boolean = true, title: String = "English source", summary: String = "English summary"): UUID = {
    val id = UUID.randomUUID()
    posts += id
    sql("insert into public.blog_post (id, version, slug, title, summary, content, status, published_at) values (?, 0, ?, ?, ?, 'English body', ?, case when ? then current_timestamp else null end)",
      id, s"translation-$id", title, summary, if (published) "PUBLISHED" else "DRAFT", published)
    id
  }
  private def path(id: UUID): String = s"editorial/posts/$id/translations"
  private def publicPath(id: UUID, locale: String = "de"): String = s"blog/posts/translation-$id?locale=$locale"
  private def json(response: HttpResponse[String]): JsonObject = {
    assert(Set(200, 201).contains(response.statusCode()), s"${response.statusCode()}: ${response.body()}")
    JsonParser.parse(response.body()).asInstanceOf[JsonObject]
  }
  private def data(response: HttpResponse[String]): JsonObject = json(response).getJsonObject("data")
  private def version(value: JsonObject): String = value.value.get("version").value.toString
  private def input(title: String = "Deutscher Titel", content: String = "Deutscher Inhalt"): String =
    new JsonObject().put("title", title).put("content", content).encode()
  private def create(browser: Browser, id: UUID, body: String = input()): JsonObject =
    data(browser.write(s"${path(id)}/de", "POST", body))
  private def command(browser: Browser, id: UUID, value: JsonObject, name: String): JsonObject =
    data(browser.write(s"${path(id)}/${value.getString("id")}/$name", "POST", s"""{"version":${version(value)}}"""))
  private def rows(value: JsonObject): Seq[JsonObject] = value.value.get("rows").asInstanceOf[JsonArray].value.asScala
    .toSeq.map(_.asInstanceOf[JsonObject].getJsonObject("data"))

  test("missing and draft German translations fall back to the complete English source") {
    val browser = admin()
    val id = post()
    val blank = data(browser.send(s"${path(id)}/de"))
    assert(!blank.value.containsKey("id") && !blank.value.containsKey("title") && version(blank) == "-1")
    val visitor = new Browser()
    assert(data(visitor.send(publicPath(id))).getString("contentLocale") == "en")
    create(browser, id)
    val draft = data(visitor.send(publicPath(id)))
    assert(draft.getString("content") == "English body" && !draft.value.containsKey("translation"))
    assert(!draft.encode().contains("Deutscher"))
  }
  test("publication selects the whole translation, preserves null summary, and never rewrites English") {
    val browser = admin()
    val id = post()
    val published = command(browser, id, create(browser, id), "publish")
    assert(version(published) == "1")
    val selected = data(new Browser().send(publicPath(id)))
    assert(selected.getString("contentLocale") == "de" && selected.getString("title") == "English source")
    val translation = selected.getJsonObject("translation")
    assert(translation.getString("title") == "Deutscher Titel" && !translation.value.containsKey("summary"))
    assert(!translation.value.containsKey("post") && !translation.value.containsKey("inlineMedia"))
    val english = data(browser.send(publicPath(id, "en")))
    assert(english.getString("contentLocale") == "en" && !english.value.containsKey("translation"))
    val editorial = data(browser.send(s"editorial/posts/$id"))
    assert(editorial.getString("title") == "English source" && version(editorial) == "0")
    command(browser, id, published, "retract")
    assert(data(browser.send(publicPath(id))).getString("contentLocale") == "en")
  }
  test("the root publication state gates every language") {
    val browser = admin()
    val id = post(published = false)
    command(browser, id, create(browser, id), "publish")
    val visitor = new Browser()
    assert(visitor.send(publicPath(id)).statusCode() == 404)
    assert(visitor.send(publicPath(id, "en")).statusCode() == 404)
  }
  test("translation writes require the current version and are independent of source edits") {
    val browser = admin()
    val id = post()
    val saved = create(browser, id)
    val url = s"${path(id)}/${saved.getString("id")}"
    assert(browser.write(url, "PATCH", """{"title":"Neuer Titel"}""").statusCode() == 400)
    assert(version(data(browser.write(url, "PATCH", """{"version":0,"title":"Neuer Titel"}"""))) == "1")
    assert(browser.write(url, "PATCH", """{"version":0,"title":"Veralteter Titel"}""").statusCode() == 409)
    assert(version(data(browser.write(s"editorial/posts/$id", "PATCH", """{"version":0,"title":"Updated English source"}"""))) == "1")
    assert(data(browser.send(s"${path(id)}/de")).getString("title") == "Neuer Titel")
  }
  test("immutable fields, malformed Markdown and invalid title roll back the entire update") {
    val browser = admin()
    val id = post()
    val saved = create(browser, id)
    val url = s"${path(id)}/${saved.getString("id")}"
    for (field <- Seq("post", "locale", "published", "inlineMedia"))
      assert(browser.write(url, "PATCH", s"""{"version":0,"$field":null}""").statusCode() == 400, field)
    assert(browser.write(url, "PATCH", """{"version":0,"title":"x","content":"New body"}""").statusCode() == 400)
    assert(browser.write(url, "PATCH", """{"version":0,"title":"Changed title","content":"![bad](https://example.com/tracker.png)"}""").statusCode() == 400)
    val unchanged = data(browser.send(s"${path(id)}/de"))
    assert(unchanged.getString("title") == "Deutscher Titel" && unchanged.getString("content") == "Deutscher Inhalt")
    assert(version(unchanged) == "0")
  }
  test("empty drafts cannot be published and published content cannot be emptied") {
    val browser = admin()
    val id = post()
    val empty = create(browser, id, input(content = ""))
    val url = s"${path(id)}/${empty.getString("id")}"
    assert(browser.write(s"$url/publish", "POST", """{"version":0}""").statusCode() == 409)
    val filled = data(browser.write(url, "PATCH", """{"version":0,"content":"Jetzt mit Inhalt"}"""))
    val published = command(browser, id, filled, "publish")
    assert(browser.write(url, "PATCH", s"""{"version":${version(published)},"content":""}""").statusCode() == 400)
    assert(data(browser.send(publicPath(id))).getJsonObject("translation").getString("content") == "Jetzt mit Inhalt")
  }
  test("a translation cannot be changed through another parent's URL") {
    val browser = admin()
    val id = post()
    val saved = create(browser, id)
    val other = post()
    assert(browser.write(s"${path(other)}/${saved.getString("id")}", "PATCH",
      """{"version":0,"title":"Wrong parent"}""").statusCode() == 404)
    val unchanged = data(browser.send(s"${path(id)}/de"))
    assert(unchanged.getString("title") == "Deutscher Titel" && version(unchanged) == "0")
    val sameParent = s"editorial/posts/${id.toString.toUpperCase}/translations/${saved.getString("id")}"
    val updated = data(browser.write(sameParent, "PATCH", """{"version":0,"title":"Correct parent"}"""))
    assert(version(updated) == "1")
    val reloaded = data(browser.send(s"${path(id)}/de"))
    assert(reloaded.getString("title") == "Correct parent" && version(reloaded) == version(updated))
  }
  test("editorial translations require ADMIN and CSRF") {
    val browser = admin()
    val id = post()
    assert(new Browser().send(s"${path(id)}/de").statusCode() == 401)
    val reader = admin("READER")
    assert(reader.send(s"${path(id)}/de").statusCode() == 403)
    assert(reader.write(s"${path(id)}/de", "POST", input()).statusCode() == 403)
    assert(browser.send(s"${path(id)}/de", "POST", input()).statusCode() == 403)
  }
  test("concurrent creation has one winner and one readable conflict") {
    val browser = admin()
    val id = post()
    val token = browser.token
    val pool = Executors.newFixedThreadPool(2)
    try {
      val jobs = (1 to 2).map(_ => pool.submit(new Callable[HttpResponse[String]] {
        override def call() = browser.send(s"${path(id)}/de", "POST", input(), token)
      }))
      assert(jobs.map(_.get().statusCode()).sorted == Seq(200, 409))
    } finally pool.close()
  }
  test("localized search, ordering, totals and page links use the selected text") {
    val browser = admin()
    val marker = UUID.randomUUID().toString.take(8)
    val first = post(title = s"Z English $marker", summary = s"hiddenenglish$marker")
    val second = post(title = s"A English $marker")
    command(browser, first, create(browser, first, input(s"A Deutsch $marker")), "publish")
    create(browser, second, input(s"Private German $marker"))
    val visitor = new Browser()
    val page = json(visitor.send(s"blog/posts?locale=de&q=$marker&sort=title&limit=1"))
    assert(page.value.get("size").value == "2")
    assert(rows(page).head.getString("title") == s"A Deutsch $marker")
    assert(rows(page).head.getString("contentLocale") == "de")
    val links = page.value.get("$links").asInstanceOf[JsonArray].value.asScala.map(_.asInstanceOf[JsonObject])
    assert(links.find(_.getString("rel") == "next").get.getString("url").contains("locale=de"))
    val tail = json(visitor.send(s"blog/posts?locale=de&q=$marker&sort=title&limit=1&offset=1"))
    assert(rows(tail).head.getString("title") == s"A English $marker")
    assert(json(visitor.send(s"blog/posts?locale=de&q=hiddenenglish$marker")).value.get("size").value == "0")
    assert(json(visitor.send(s"blog/posts?locale=en&q=hiddenenglish$marker")).value.get("size").value == "1")
    assert(json(visitor.send(s"blog/posts?locale=de&q=Private%20German%20$marker")).value.get("size").value == "0")
  }
  test("unsupported locales fail consistently on list and detail") {
    val visitor = new Browser()
    assert(visitor.send("blog/posts?locale=fr").statusCode() == 400)
    assert(visitor.send(publicPath(post(), "fr")).statusCode() == 400)
  }
  test("translated images survive draft cleanup but require both publications for public bytes") {
    val browser = admin()
    val id = post()
    val image = browser.upload()
    sql("update public.blog_media set created_at = current_timestamp - interval '2 days' where id = ?", image)
    val saved = create(browser, id, input(content = s"![Beschreibung](/service/media/$image)"))
    val visitor = new Browser()
    assert(visitor.send(s"media/$image").statusCode() == 404)
    val cleanup = browser.write("_test/media/cleanup?limit=100", "POST", "")
    assert(cleanup.statusCode() == 200 && cleanup.body() == "0")
    val published = command(browser, id, saved, "publish")
    assert(visitor.send(s"media/$image").statusCode() == 200)
    sql("update public.blog_post set status = 'DRAFT', published_at = null where id = ?", id)
    assert(visitor.send(s"media/$image").statusCode() == 404)
    sql("update public.blog_post set status = 'PUBLISHED', published_at = current_timestamp where id = ?", id)
    command(browser, id, published, "retract")
    assert(visitor.send(s"media/$image").statusCode() == 404)
    val current = data(browser.send(s"${path(id)}/de"))
    data(browser.write(s"${path(id)}/${current.getString("id")}", "PATCH",
      s"""{"version":${version(current)},"content":"Kein Bild mehr"}"""))
    val removed = browser.write("_test/media/cleanup?limit=100", "POST", "")
    assert(removed.statusCode() == 200 && removed.body() == "1")
  }
}
