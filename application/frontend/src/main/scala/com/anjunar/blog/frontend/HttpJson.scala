package com.anjunar.blog.frontend

import org.scalajs.dom
import ui.json.{JsonMapper, JsonSchema}

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.util.Try

final class HttpFailure(val status: Int, val problem: Option[ProblemDetails] = None)
    extends RuntimeException(s"HTTP $status")

object HttpJson {
  private[frontend] def failure(status: Int, contentType: String, body: String): HttpFailure = {
    val problem = Option(contentType).filter(_.takeWhile(_ != ';').trim.equalsIgnoreCase("application/problem+json"))
      .flatMap(_ => Try(JsonMapper.deserialize[ProblemDetails](js.JSON.parse(body))).toOption)
      .filter(value => value != null && value.status == status && Option(value.title).exists(_.nonEmpty))
    new HttpFailure(status, problem)
  }

  def get[M](path: String, signal: Option[dom.AbortSignal])(using
      ExecutionContext, JsonSchema[M]
  ): Future[M] = request(path, dom.HttpMethod.GET, signal, None, None)

  def post[M](path: String, body: js.Dynamic, csrf: String)(using
      ExecutionContext, JsonSchema[M]
  ): Future[M] = request(path, dom.HttpMethod.POST, None, Some(body), Some(csrf))

  def write[M](path: String, method: String, body: js.Dynamic, csrf: String)(using
      ExecutionContext, JsonSchema[M]): Future[M] = {
    val httpMethod = method match {
      case "POST" => dom.HttpMethod.POST
      case "PATCH" => dom.HttpMethod.PATCH
      case _ => throw new IllegalArgumentException("Unexpected write method")
    }
    request(path, httpMethod, None, Some(body), Some(csrf))
  }

  private def request[M](path: String, method: dom.HttpMethod, signal: Option[dom.AbortSignal],
      body: Option[js.Dynamic], csrf: Option[String])(using ExecutionContext, JsonSchema[M]): Future[M] = {
    val headers = new dom.Headers()
    headers.set("Accept", "application/json, application/problem+json")
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
      if (!response.ok) response.text().toFuture.flatMap(body =>
        Future.failed(failure(response.status, response.headers.get("Content-Type"), body)))
      else response.text().toFuture.map { body =>
        JsonMapper.deserialize[M](js.JSON.parse(body))
      }
    }
  }
}
