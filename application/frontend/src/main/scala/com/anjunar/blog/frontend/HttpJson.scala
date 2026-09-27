package com.anjunar.blog.frontend

import org.scalajs.dom
import ui.json.{JsonMapper, JsonSchema}

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

final class HttpFailure(val status: Int) extends RuntimeException(s"HTTP $status")

object HttpJson {
  def get[M](path: String, signal: Option[dom.AbortSignal])(using
      ExecutionContext, JsonSchema[M]
  ): Future[M] = {
    val headers = new dom.Headers()
    headers.set("Accept", "application/json")
    val options = new dom.RequestInit {
      method = dom.HttpMethod.GET
      credentials = dom.RequestCredentials.`same-origin`
    }
    options.headers = headers
    signal.foreach(value => options.signal = value)

    dom.fetch(path, options).toFuture.flatMap { response =>
      // fetch resolves for HTTP errors too. Do not map an error body as a post.
      if (!response.ok) Future.failed(new HttpFailure(response.status))
      else response.text().toFuture.map { body =>
        JsonMapper.deserialize[M](js.JSON.parse(body))
      }
    }
  }
}
