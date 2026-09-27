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
  ): Future[M] = request(path, dom.HttpMethod.GET, signal, None, None)

  def post[M](path: String, body: js.Dynamic, csrf: String)(using
      ExecutionContext, JsonSchema[M]
  ): Future[M] = request(path, dom.HttpMethod.POST, None, Some(body), Some(csrf))

  private def request[M](path: String, method: dom.HttpMethod, signal: Option[dom.AbortSignal],
      body: Option[js.Dynamic], csrf: Option[String])(using ExecutionContext, JsonSchema[M]): Future[M] = {
    val headers = new dom.Headers()
    headers.set("Accept", "application/json")
    val options = new dom.RequestInit {
      credentials = dom.RequestCredentials.`same-origin`
    }
    options.method = method
    options.headers = headers
    signal.foreach(value => options.signal = value)
    csrf.foreach(value => headers.set("X-CSRF-Token", value))
    body.foreach { value =>
      headers.set("Content-Type", "application/json")
      options.body = js.JSON.stringify(value)
    }

    dom.fetch(path, options).toFuture.flatMap { response =>
      if (!response.ok) Future.failed(new HttpFailure(response.status))
      else response.text().toFuture.map { body =>
        JsonMapper.deserialize[M](js.JSON.parse(body))
      }
    }
  }
}
