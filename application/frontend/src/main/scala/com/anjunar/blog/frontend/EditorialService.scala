package com.anjunar.blog.frontend

import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.URIUtils.encodeURIComponent

final class EditorialService(accounts: AccountService)(using ExecutionContext) {
  def list(offset: Int, limit: Int, signal: Option[dom.AbortSignal]): Future[BlogPostTable] =
    accounts.session(signal).flatMap { state =>
      state.links.find(_.rel == "editorial") match {
        case None => Future.failed(new HttpFailure(if (state.account.isEmpty) 401 else 403))
        case Some(link) =>
          val target = new dom.URL(link.path("GET"), dom.window.location.origin)
          target.searchParams.set("offset", offset.toString)
          target.searchParams.set("limit", limit.toString)
          HttpJson.get[BlogPostTable](target.pathname + target.search, signal).map { table =>
            require(table.size >= 0 && table.rows.forall(row => row.data != null), "Invalid editorial table")
            table
          }
      }
    }

  def detail(id: String, signal: Option[dom.AbortSignal]): Future[BlogPostData] =
    HttpJson.get[BlogPostData](s"/service/editorial/posts/${encodeURIComponent(id)}", signal).map(validate)

  def execute(link: ApiLink): Future[BlogPostData] = {
    // Validate before fetching CSRF or sending a command.
    val path = link.path("POST")
    accounts.session().flatMap(state =>
      HttpJson.post[BlogPostData](path, js.Dynamic.literal(), state.csrfToken)).map(validate)
  }

  private def validate(result: BlogPostData): BlogPostData = {
    require(result.data != null && result.data.content.get.nonEmpty, "Missing editorial detail")
    result
  }
}
