package com.anjunar.blog.frontend

import com.anjunar.blog.frontend.HttpJson.Response
import org.scalajs.dom
import ui.core.state.Property
import ui.json.{JsonMapper, JsonSchema}

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.URIUtils.{decodeURIComponent, encodeURIComponent}
import scala.util.control.NonFatal

// This describes one public HTTP response, not another copy of the entity model.
final class PageSnapshot(
    var format: Int = 1,
    var url: String = "",
    var request: String = "",
    var status: Int = 0,
    var contentType: String = "",
    var body: String = ""
)

final class InitialPageData private (
    url: String,
    recording: Boolean,
    private var replaying: Boolean,
    private var pending: Option[PageSnapshot]
) {
  val encoded: Property[String] = Property(InitialPageData.encode(new PageSnapshot(url = url)))

  def get[M](path: String, signal: Option[dom.AbortSignal])(using
      ExecutionContext, JsonSchema[M]): Future[M] = {
    require(InitialPageData.publicRequest(path), "Only public post responses belong in initial state")
    val response = if (replaying) {
      val saved = pending.filter(_.request == path)
        .getOrElse(throw new IllegalStateException("Initial response does not match the route"))
      pending = None
      Future.successful(Response(saved.status, saved.contentType, saved.body))
    } else {
      HttpJson.getResponse(path, signal).recover {
        // Replay transport failure too: the first browser render must show the same error page.
        case NonFatal(_) => Response(503, "", "")
      }
    }
    response.map { value =>
      if (recording) encoded.set(InitialPageData.encode(
        new PageSnapshot(url = url, request = path, status = value.status,
          contentType = value.contentType, body = value.body)))
      HttpJson.decode[M](value)
    }(using ExecutionContext.parasitic)
  }

  // Initial state is not a cache for later navigation.
  def finish(): Unit = {
    replaying = false
    pending = None
  }
}

object InitialPageData {
  private val MaxEncodedLength = 8 * 1024 * 1024

  def live(): InitialPageData = new InitialPageData("", false, false, None)
  def capture(url: String): InitialPageData = new InitialPageData(url, true, false, None)

  def replay(encoded: String, url: String): InitialPageData = {
    require(encoded != null && encoded.length <= MaxEncodedLength, "Invalid initial state size")
    val saved = JsonMapper.deserialize[PageSnapshot](js.JSON.parse(decodeURIComponent(encoded)))
    require(saved != null && saved.format == 1 && saved.url == url, "Initial state belongs to another page")
    require(saved.request != null && saved.body != null && saved.contentType != null, "Incomplete initial state")
    val hasResponse = saved.request.nonEmpty
    require(if (hasResponse) publicRequest(saved.request) && saved.status >= 100 && saved.status <= 599
      else saved.status == 0 && saved.body.isEmpty && saved.contentType.isEmpty, "Invalid initial response")
    new InitialPageData(url, false, true, Option.when(hasResponse)(saved))
  }

  private[frontend] def publicRequest(path: String): Boolean =
    path != null && path.matches("/service/blog/posts(?:/[a-z0-9]+(?:-[a-z0-9]+)*)?(?:\\?[^#]*)?")

  private[frontend] def encode(snapshot: PageSnapshot): String =
    encodeURIComponent(js.JSON.stringify(JsonMapper.serialize(snapshot)))
}
