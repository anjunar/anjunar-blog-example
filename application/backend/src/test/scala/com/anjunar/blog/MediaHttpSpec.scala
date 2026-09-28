package com.anjunar.blog

import com.anjunar.json.mapper.intermediate.JsonParser
import com.anjunar.json.mapper.intermediate.model.JsonObject
import dev.resteasy.embedded.server.UndertowCdiEmbeddedServer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

import java.io.ByteArrayInputStream
import java.net.{CookieManager, CookiePolicy, InetAddress, ServerSocket, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets.UTF_8
import java.sql.DriverManager
import java.time.Duration
import java.util.UUID
import java.util.concurrent.{Callable, Executors, TimeUnit}
import scala.collection.mutable
import scala.util.Using

class MediaHttpSpec extends AnyFunSuite with BeforeAndAfterAll {
  private var server: UndertowCdiEmbeddedServer = null
  private var port = 0
  private val accounts = mutable.Buffer.empty[UUID]
  private val posts = mutable.Buffer.empty[UUID]
  private val clients = mutable.Buffer.empty[HttpClient]
  private val password = "a long media test passphrase"
  private lazy val hash = PasswordHash.create(password)

  private def connection() = {
    val config = DatabaseConfig.load()
    DriverManager.getConnection(config.url, config.user, config.password)
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    port = try socket.getLocalPort finally socket.close()
    server = ApplicationMain.start(port, security = SecurityConfig(false))
  }

  override protected def afterAll(): Unit = try {
    clients.foreach(_.close())
    if (server != null) server.stop()
  } finally {
    Using.resource(connection()) { c =>
      for ((table, field, ids) <- Seq(("blog_post_media", "post_id", posts), ("blog_post", "id", posts), ("blog_media", "owner_id", accounts), ("blog_account", "id", accounts)))
        Using.resource(c.prepareStatement(s"delete from public.$table where $field = ?")) { q =>
          ids.foreach { id => q.setObject(1, id); q.executeUpdate() }
        }
    }
    super.afterAll()
  }

  private final class Browser(role: Option[String] = Some("ADMIN"), protocol: HttpClient.Version = HttpClient.Version.HTTP_2) {
    val id: UUID = UUID.randomUUID()
    val client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
      .connectTimeout(Duration.ofSeconds(5)).version(protocol).build()
    clients += client
    private val email = s"media-$id@example.test"
    role.foreach { value =>
      accounts += id
      Using.resource(connection()) { c =>
        Using.resource(c.prepareStatement(
          "insert into public.blog_account (id,version,email,role,password_hash,authentication_version,locked) values (?,0,?,?,?,0,false)")) { q =>
          q.setObject(1, id); q.setString(2, email); q.setString(3, value); q.setString(4, hash); q.executeUpdate()
        }
      }
      val login = json("auth/login", "POST", s"""{"email":"$email","password":"$password"}""")
      assert(login.statusCode() == 200, text(login))
    }

    def csrf: String = obj(raw("auth/session")).getString("csrfToken")

    def raw(path: String, method: String = "GET", bytes: Array[Byte] = Array.emptyByteArray,
        contentType: String = "application/json", token: Option[String] = None, chunked: Boolean = false,
        extra: Map[String, String] = Map.empty): HttpResponse[Array[Byte]] = {
      val request = HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port/service/$path"))
        .timeout(Duration.ofSeconds(20)).header("Accept", "*/*")
      if (method == "POST" || method == "PATCH") request.header("Content-Type", contentType)
      token.foreach(value => request.header("X-CSRF-Token", value))
      extra.foreach((name, value) => request.setHeader(name, value))
      val body = if (chunked) HttpRequest.BodyPublishers.ofInputStream(() => new ByteArrayInputStream(bytes))
        else HttpRequest.BodyPublishers.ofByteArray(bytes)
      client.send(request.method(method, body).build(), HttpResponse.BodyHandlers.ofByteArray())
    }

    def json(path: String, method: String = "GET", body: String = ""): HttpResponse[Array[Byte]] =
      raw(path, method, body.getBytes(UTF_8), token = Option.when(method != "GET")(csrf))

    def upload(bytes: Array[Byte] = TestImages.bytes(), contentType: String = "image/png",
        chunked: Boolean = false, extra: Map[String, String] = Map.empty): HttpResponse[Array[Byte]] =
      raw("editorial/media", "POST", bytes, contentType, Some(csrf), chunked, extra)

    def image(): JsonObject = {
      val response = upload(extra = Map("X-File-Name" -> "..%2Fcover.png"))
      assert(response.statusCode() == 201, text(response))
      obj(response).getJsonObject("data")
    }
  }

  private def text(response: HttpResponse[Array[Byte]]): String = new String(response.body(), UTF_8)
  private def obj(response: HttpResponse[Array[Byte]]): JsonObject =
    JsonParser.parse(text(response)).asInstanceOf[JsonObject]
  private def ref(media: JsonObject): String = s"""{"id":"${media.getString("id")}"}"""
  private def stored(id: String): Boolean = Using.resource(connection()) { c =>
    Using.resource(c.prepareStatement("select 1 from public.blog_media where id = ?")) { q =>
      q.setObject(1, UUID.fromString(id))
      Using.resource(q.executeQuery())(_.next())
    }
  }
  private def age(id: String): Unit = Using.resource(connection()) { c =>
    Using.resource(c.prepareStatement("update public.blog_media set created_at = now() - interval '2 days' where id = ?")) { q =>
      q.setObject(1, UUID.fromString(id)); q.executeUpdate()
    }
  }
  private def create(browser: Browser, suffix: String = ""): JsonObject = {
    val response = browser.json("editorial/posts", "POST",
      s"""{"slug":"image-${UUID.randomUUID()}","title":"A post with an image","content":"A complete post"$suffix}""")
    assert(response.statusCode() == 201, text(response))
    val result = obj(response).getJsonObject("data")
    posts += UUID.fromString(result.getString("id"))
    result
  }
  private def postPath(post: JsonObject): String = s"editorial/posts/${post.getString("id")}"

  private def document(browser: Browser, post: JsonObject, content: String, format: String = "MARKDOWN"): HttpResponse[Array[Byte]] = {
    val current = obj(browser.json(postPath(post))).getJsonObject("data")
    val encoded = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    browser.json(postPath(post), "PATCH",
      s"""{"version":${current.value.get("version").value},"contentFormat":"$format","content":"$encoded"}""")
  }

  test("embedded images follow publication, preserve references on partial edits and become collectible after removal") {
    val owner = new Browser()
    val media = owner.image()
    val post = create(owner)
    val mediaId = media.getString("id")
    val source = s"media/$mediaId"
    val saved = document(owner, post, s"![Blue pixels](/service/$source)")
    assert(saved.statusCode() == 200, text(saved))
    assert(obj(saved).getJsonObject("data").getString("contentFormat") == "MARKDOWN")
    assert(!text(saved).contains("inlineMedia"))
    val public = new Browser(None)
    assert(public.raw(source).statusCode() == 404)
    age(mediaId)
    assert(text(owner.json("_test/media/cleanup?limit=100", "POST")) == "0")
    assert(stored(mediaId))
    assert(owner.json(postPath(post), "PATCH", """{"version":1,"title":"A renamed Markdown post"}""").statusCode() == 200)
    assert(owner.json(postPath(post) + "/publish", "POST").statusCode() == 200)
    assert(public.raw(source).statusCode() == 200)
    val detail = public.json(s"blog/posts/${post.getString("slug")}")
    assert(obj(detail).getJsonObject("data").getString("contentFormat") == "MARKDOWN")
    assert(!text(detail).contains("inlineMedia"))
    assert(owner.json(postPath(post) + "/retract", "POST").statusCode() == 200)
    assert(public.raw(source).statusCode() == 404)
    assert(document(owner, post, "The image was removed.").statusCode() == 200)
    assert(text(owner.json("_test/media/cleanup?limit=100", "POST")) == "1")
    assert(!stored(mediaId))
  }

  test("private references, missing references and client-supplied derived relations reject the whole change") {
    val owner = new Browser()
    val stranger = new Browser()
    val media = owner.image()
    val post = create(stranger)
    for (id <- Seq(media.getString("id"), UUID.randomUUID().toString)) {
      val response = document(stranger, post, s"![Private](/service/media/$id)")
      assert(response.statusCode() == 400, text(response))
    }
    val injected = stranger.json(postPath(post), "PATCH",
      """{"version":0,"title":"Must roll back","inlineMedia":[]}""")
    assert(injected.statusCode() == 400, text(injected))
    val unchanged = obj(stranger.json(postPath(post))).getJsonObject("data")
    assert(unchanged.getString("content") == "A complete post")
    assert(unchanged.value.get("version").value == "0")
    for (invalid <- Seq("<script>bad</script>", "[click](javascript:alert)", "![](/service/media/" + media.getString("id") + ")")) {
      val result = document(owner, create(owner), invalid)
      assert(result.statusCode() == 400, text(result))
    }
  }

  test("image syntax inside a code example does not protect an unused upload from cleanup") {
    val owner = new Browser()
    val media = owner.image()
    val post = create(owner)
    val mediaId = media.getString("id")
    assert(document(owner, post, s"`![Example](/service/media/$mediaId)`").statusCode() == 200)
    age(mediaId)
    assert(text(owner.json("_test/media/cleanup?limit=100", "POST")) == "1")
    assert(!stored(mediaId))
    // The reference is still only code after its example upload has disappeared.
    assert(document(owner, post, s"`![Example](/service/media/$mediaId)`").statusCode() == 200)
  }

  test("upload stores bounded pixels and returns metadata without owner or binary JSON") {
    val owner = new Browser()
    val response = owner.upload(extra = Map("X-File-Name" -> "..%2Fcover.png"))
    assert(response.statusCode() == 201, text(response))
    val media = obj(response).getJsonObject("data")
    assert(media.getString("name") == "cover.png")
    assert(media.value.get("width").value == "4" && media.value.get("height").value == "3")
    assert(!text(response).contains("ownerId") && !media.value.containsKey("data"))
    val public = new Browser(None)
    assert(public.raw(s"media/${media.getString("id")}").statusCode() == 404)
    assert(new Browser().raw(s"media/${media.getString("id")}").statusCode() == 404)
    val reads = MediaWriteProbe.binaryWrites.get()
    val read = owner.raw(s"media/${media.getString("id")}")
    assert(read.statusCode() == 200 && read.headers().firstValue("Content-Type").orElse("") == "image/png")
    assert(read.headers().firstValue("Cache-Control").orElse("") == "no-store")
    assert(read.headers().firstValue("X-Content-Type-Options").orElse("") == "nosniff")
    assert(MediaWriteProbe.binaryWrites.get() > reads)
    val head = owner.raw(s"media/${media.getString("id")}", "HEAD")
    assert(head.statusCode() == 200 && head.body().isEmpty)
    assert(head.headers().firstValue("Content-Length") == read.headers().firstValue("Content-Length"))
  }

  test("only administrators with CSRF can upload") {
    assert(new Browser(None).upload().statusCode() == 401)
    assert(new Browser(Some("READER")).upload().statusCode() == 403)
    val admin = new Browser()
    assert(admin.raw("editorial/media", "POST", TestImages.bytes(), "image/png").statusCode() == 403)
  }

  test("invalid type, body, dimensions and oversized chunked uploads do not persist") {
    val admin = new Browser(protocol = HttpClient.Version.HTTP_1_1)
    for ((bytes, mime, status) <- Seq(
      ("<svg/>".getBytes(UTF_8), "image/svg+xml", 415),
      (Array.emptyByteArray, "image/png", 400),
      (TestImages.bytes(), "image/jpeg", 400),
      (TestImages.bytes(width = 8001, height = 1), "image/png", 400))) {
      val response = admin.upload(bytes, mime)
      assert(response.statusCode() == status, text(response))
    }
    assert(admin.upload(new Array[Byte](ImageContent.MaxBytes + 1), chunked = true).statusCode() == 413)
    val http2 = new Browser()
    assert(http2.upload(new Array[Byte](ImageContent.MaxBytes + 1), chunked = true).statusCode() == 413)
    val padded = TestImages.bytes() ++ new Array[Byte](ImageContent.MaxBytes - TestImages.bytes().length)
    assert(http2.upload(padded).statusCode() == 201)
    Using.resource(connection()) { c =>
      Using.resource(c.prepareStatement("select count(*) from public.blog_media where owner_id = ?")) { q =>
        q.setObject(1, admin.id)
        Using.resource(q.executeQuery()) { rows => rows.next(); assert(rows.getInt(1) == 0) }
      }
    }
  }

  test("a cover is private in drafts, public on publication and private again after retraction") {
    val owner = new Browser()
    val media = owner.image()
    val post = create(owner, s""","coverImage":${ref(media)},"coverAlt":"Blue test pixels" """)
    val imagePath = s"media/${media.getString("id")}"
    val public = new Browser(None)
    assert(public.raw(imagePath).statusCode() == 404)
    assert(new Browser().raw(imagePath).statusCode() == 200) // Editors can inspect an attached draft cover.
    assert(owner.json(postPath(post) + "/publish", "POST").statusCode() == 200)
    assert(public.raw(imagePath).statusCode() == 200)
    val detail = obj(public.json(s"blog/posts/${post.getString("slug")}")).getJsonObject("data")
    assert(detail.getJsonObject("coverImage").getString("id") == media.getString("id"))
    assert(!detail.getJsonObject("coverImage").value.containsKey("ownerId"))
    assert(detail.getString("coverAlt") == "Blue test pixels")
    assert(owner.json(postPath(post) + "/retract", "POST").statusCode() == 200)
    assert(public.raw(imagePath).statusCode() == 404)
  }

  test("ownership, ID-only input and alt text protect the whole update") {
    val owner = new Browser()
    val stranger = new Browser()
    val image = owner.image()
    val post = create(stranger)
    val target = postPath(post)
    for (input <- Seq(
      s""""coverImage":${ref(image)},"coverAlt":"Private image" """,
      s""""coverImage":{"id":"${UUID.randomUUID()}"},"coverAlt":"Missing image" """)) {
      val response = stranger.json(target, "PATCH", s"""{"version":0,"title":"Must roll back",$input}""")
      assert(response.statusCode() == 400, text(response))
    }
    val own = stranger.image()
    assert(stranger.json(target, "PATCH",
      s"""{"version":0,"title":"Must roll back","coverImage":{"id":"${own.getString("id")}","name":"Nested edit"}}""")
      .statusCode() == 400)
    assert(stranger.json(target, "PATCH", s"""{"version":0,"coverImage":${ref(own)}}""").statusCode() == 400)
    val unchanged = obj(stranger.json(target)).getJsonObject("data")
    assert(unchanged.getString("title") == post.getString("title") && unchanged.value.get("version").value == "0")
    assert(!unchanged.value.containsKey("coverImage"))
  }

  test("omission preserves the image, null clears it and stale versions cannot replace it") {
    val owner = new Browser()
    val image = owner.image()
    val post = create(owner, s""","coverImage":${ref(image)},"coverAlt":"A cover" """)
    val target = postPath(post)
    val renamed = owner.json(target, "PATCH", """{"version":0,"title":"Updated title"}""")
    assert(renamed.statusCode() == 200 && obj(renamed).getJsonObject("data").getJsonObject("coverImage") != null)
    assert(owner.json(target, "PATCH", """{"version":0,"coverImage":null}""").statusCode() == 409)
    assert(owner.json(target, "PATCH", """{"version":1,"coverImage":null}""").statusCode() == 200)
    assert(!obj(owner.json(target)).getJsonObject("data").value.containsKey("coverImage"))
    assert(stored(image.getString("id")))
  }

  test("metadata writer failure rolls back both image metadata and bytes") {
    val owner = new Browser()
    val response = owner.upload(extra = Map("X-Test-Fail-Upload" -> "true"))
    assert(response.statusCode() == 500, text(response))
    Using.resource(connection()) { c =>
      Using.resource(c.prepareStatement("select count(*) from public.blog_media where owner_id = ?")) { q =>
        q.setObject(1, owner.id)
        Using.resource(q.executeQuery()) { rows => rows.next(); assert(rows.getInt(1) == 0) }
      }
    }
  }

  test("cleanup keeps young uploads and every referenced image, including draft covers") {
    val owner = new Browser()
    val old = owner.image()
    val linked = owner.image()
    val young = owner.image()
    age(old.getString("id")); age(linked.getString("id"))
    create(owner, s""","coverImage":${ref(linked)},"coverAlt":"Keep this draft cover" """)
    val removed = owner.json("_test/media/cleanup?limit=100", "POST")
    assert(removed.statusCode() == 200 && text(removed) == "1", text(removed))
    assert(!stored(old.getString("id")) && stored(linked.getString("id")) && stored(young.getString("id")))
  }

  test("cleanup rechecks references after waiting for an attachment's media-row lock") {
    val owner = new Browser()
    val media = owner.image()
    age(media.getString("id"))
    val post = create(owner)
    val pool = Executors.newSingleThreadExecutor()
    val token = owner.csrf
    try Using.resource(connection()) { c =>
      c.setAutoCommit(false)
      Using.resource(c.prepareStatement("select id from public.blog_media where id = ? for update")) { q =>
        q.setObject(1, UUID.fromString(media.getString("id")))
        Using.resource(q.executeQuery())(_.next())
      }
      Using.resource(c.prepareStatement("update public.blog_post set cover_image_id = ?, cover_alt = 'Concurrent cover' where id = ?")) { q =>
        q.setObject(1, UUID.fromString(media.getString("id"))); q.setObject(2, UUID.fromString(post.getString("id")))
        q.executeUpdate()
      }
      val pending = pool.submit(new Callable[HttpResponse[Array[Byte]]] {
        override def call() = owner.raw("_test/media/cleanup?limit=100", "POST", token = Some(token))
      })
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
      var waiting = false
      while (!waiting && System.nanoTime() < deadline) {
        Using.resource(connection()) { check =>
          Using.resource(check.createStatement()) { q =>
            Using.resource(q.executeQuery("select count(*) from pg_stat_activity where wait_event_type = 'Lock' and query like '%blog_media%'")) { rows =>
              rows.next(); waiting = rows.getInt(1) > 0
            }
          }
        }
        if (!waiting) Thread.sleep(10)
      }
      assert(waiting, "Cleanup should wait for the media-row lock")
      c.commit()
      val result = pending.get(10, TimeUnit.SECONDS)
      assert(result.statusCode() == 200 && text(result) == "0", text(result))
      assert(stored(media.getString("id")))
    } finally pool.shutdownNow()
  }
}
