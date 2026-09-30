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
  final case class Response(status: Int, contentType: String, body: String)

  def getResponse(path: String, signal: Option[dom.AbortSignal])(using ExecutionContext): Future[Response] =
    send(path, dom.HttpMethod.GET, signal, None, None)

  def decode[M](response: Response)(using JsonSchema[M]): M = {
    if (response.status < 200 || response.status >= 300)
      throw failure(response.status, response.contentType, response.body)
    JsonMapper.deserialize[M](js.JSON.parse(response.body))
  }

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
      body: Option[js.Dynamic], csrf: Option[String])(using ExecutionContext, JsonSchema[M]): Future[M] =
    send(path, method, signal, body, csrf).map(decode[M])(using ExecutionContext.parasitic)

  private def send(path: String, method: dom.HttpMethod, signal: Option[dom.AbortSignal],
      body: Option[js.Dynamic], csrf: Option[String])(using ExecutionContext): Future[Response] = {
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
      response.text().toFuture.map { body =>
        Response(response.status, Option(response.headers.get("Content-Type")).getOrElse(""), body)
      }
    }
  }
}
