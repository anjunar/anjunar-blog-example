package com.anjunar.blog.frontend

import org.scalajs.dom
import ui.json.JsonMapper

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.URIUtils.encodeURIComponent

final class MediaService(accounts: AccountService)(using ExecutionContext) {
  def upload(file: dom.File, signal: dom.AbortSignal): Future[Media] = {
    if (!Set("image/jpeg", "image/png").contains(file.`type`))
      return Future.failed(new HttpFailure(415))
    if (file.size < 1 || file.size > 5 * 1024 * 1024)
      return Future.failed(new HttpFailure(413))
    accounts.session(Some(signal)).flatMap { state =>
      val link = state.links.find(_.rel == "uploadImage").getOrElse(throw new HttpFailure(403))
      val path = link.path("POST")
      val headers = new dom.Headers()
      headers.set("Accept", "application/json, application/problem+json")
      headers.set("Content-Type", file.`type`)
      headers.set("X-File-Name", encodeURIComponent(file.name))
      headers.set("X-CSRF-Token", state.csrfToken)
      val options = new dom.RequestInit {
        method = dom.HttpMethod.POST
        credentials = dom.RequestCredentials.`same-origin`
        body = file
      }
      options.headers = headers
      options.signal = signal
      dom.fetch(path, options).toFuture.flatMap { response =>
        response.text().toFuture.flatMap { body =>
          if (!response.ok) Future.failed(HttpJson.failure(response.status, response.headers.get("Content-Type"), body))
          else {
            val result = JsonMapper.deserialize[MediaData](js.JSON.parse(body))
            require(result.data != null && result.data.width.get > 0 && result.data.height.get > 0 &&
              result.data.byteSize.get > 0, "Missing image metadata")
            result.data.source
            Future.successful(result.data)
          }
        }
      }
    }
  }
}
