package com.anjunar.blog.frontend

import org.scalatest.funsuite.AnyFunSuite
import ui.json.JsonSchema

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.URIUtils.decodeURIComponent

class InitialPageDataSpec extends AnyFunSuite {
  private given JsonSchema[BlogPostData] = JsonSchema.derived[BlogPostData]
  private val request = "/service/blog/posts/example?locale=de"
  private val body = """{"data":{"id":"post-id","version":0,"title":"Original","content":"English",
    "contentLocale":"de","translation":{"title":"Deutsch","content":"Übersetzt"}}}"""
  private def snapshot(status: Int = 200, value: String = body): PageSnapshot =
    new PageSnapshot(url = "/de/posts/example", request = request, status = status,
      contentType = "application/json", body = value)

  test("initial JSON is mapped synchronously without losing selected or omitted entity fields") {
    val data = InitialPageData.replay(InitialPageData.encode(snapshot()), "/de/posts/example")
    val loaded = data.get[BlogPostData](request, None)
    assert(loaded.value.exists(_.isSuccess))
    val post = loaded.value.get.get.data
    assert(post.id.get == "post-id" && post.version.get == 0)
    assert(post.title.get == "Original" && post.translation.get.title.get == "Deutsch")
    assert(post.contentLocale.get == "de" && post.summary.get == null)
  }

  test("a snapshot is single-use and keyed by the complete API URL including locale") {
    val encoded = InitialPageData.encode(snapshot())
    val data = InitialPageData.replay(encoded, "/de/posts/example")
    intercept[IllegalStateException](data.get[BlogPostData](request.replace("de", "en"), None))
    assert(data.get[BlogPostData](request, None).value.get.isSuccess)
    intercept[IllegalStateException](data.get[BlogPostData](request, None))
  }

  test("not-found and unavailable responses are already failed when the router inspects them") {
    for (status <- Seq(404, 503)) {
      val data = InitialPageData.replay(InitialPageData.encode(snapshot(status, "")), "/de/posts/example")
      val failure = data.get[BlogPostData](request, None).value.get.failed.get
      assert(failure.isInstanceOf[HttpFailure] && failure.asInstanceOf[HttpFailure].status == status)
    }
  }

  test("page URL, format, private endpoints, invalid shape and oversized state are rejected") {
    intercept[IllegalArgumentException](InitialPageData.replay(InitialPageData.encode(snapshot()), "/en/posts/example"))
    val changed = snapshot()
    changed.format = 2
    intercept[IllegalArgumentException](InitialPageData.replay(InitialPageData.encode(changed), changed.url))
    changed.format = 1
    changed.request = "/service/auth/session"
    intercept[IllegalArgumentException](InitialPageData.replay(InitialPageData.encode(changed), changed.url))
    changed.request = request
    changed.status = 0
    intercept[IllegalArgumentException](InitialPageData.replay(InitialPageData.encode(changed), changed.url))
    intercept[Throwable](InitialPageData.replay("%broken", changed.url))
    intercept[IllegalArgumentException](InitialPageData.replay("x" * (8 * 1024 * 1024 + 1), changed.url))
  }

  test("state encoding preserves literal script terminators, quotes and Unicode") {
    val literal = """</script><script>window.injected=true</script> & " Ü"""
    val raw = js.JSON.stringify(js.Dynamic.literal(data = js.Dynamic.literal(content = literal)))
    val encoded = InitialPageData.encode(snapshot(value = raw))
    assert(!encoded.contains("<") && !encoded.contains(">") && !encoded.contains("\""))
    assert(decodeURIComponent(encoded).contains("Ü"))
    val data = InitialPageData.replay(encoded, "/de/posts/example")
    assert(data.get[BlogPostData](request, None).value.get.get.data.content.get == literal)
  }

  test("an empty snapshot represents a route that did not need an API request") {
    val initial = InitialPageData.capture("/de?limit=0")
    val data = InitialPageData.replay(initial.encoded.get, "/de?limit=0")
    intercept[IllegalStateException](data.get[BlogPostData](request, None))
  }
}
