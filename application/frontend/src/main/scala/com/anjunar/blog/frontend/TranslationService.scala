package com.anjunar.blog.frontend

import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js

final class TranslationService(accounts: AccountService)(using ExecutionContext) {
  def load(link: ApiLink, signal: Option[dom.AbortSignal]): Future[TranslationData] =
    HttpJson.get[TranslationData](link.path("GET"), signal).map(validate)

  def send(link: ApiLink, body: js.Dynamic): Future[TranslationData] = {
    val method = link.rel match {
      case "update" => "PATCH"
      case "create" | "publish" | "retract" => "POST"
      case _ => throw new IllegalArgumentException("Unexpected translation operation")
    }
    val path = link.path(method)
    accounts.session().flatMap(state =>
      HttpJson.write[TranslationData](path, method, body, state.csrfToken)).map { response =>
        val value = validate(response)
        require(value.data.id.get.nonEmpty && value.data.version.get >= 0, "Missing translation version")
        value
      }
  }

  private def validate(value: TranslationData): TranslationData = {
    require(value.data != null && value.data.locale.get == "de", "Missing German translation")
    value
  }
}
