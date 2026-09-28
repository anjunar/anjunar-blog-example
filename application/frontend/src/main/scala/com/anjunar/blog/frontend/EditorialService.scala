package com.anjunar.blog.frontend

import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.scalajs.js.URIUtils.encodeURIComponent

final class EditorialService(accounts: AccountService)(using ExecutionContext) {
  def list(search: PostSearch, signal: Option[dom.AbortSignal]): Future[BlogPostTable] =
    accounts.session(signal).flatMap { state =>
      state.links.find(_.rel == "editorial") match {
        case None => Future.failed(new HttpFailure(if (state.account.isEmpty) 401 else 403))
        case Some(link) =>
          val target = new dom.URL(link.path("GET"), dom.window.location.origin)
          target.search = search.queryString(includeDefaults = true)
          HttpJson.get[BlogPostTable](target.pathname + target.search, signal).map { table =>
            require(table.size >= 0 && table.rows.forall(row => row.data != null), "Invalid editorial table")
            table
          }
      }
    }

  def detail(id: String, signal: Option[dom.AbortSignal]): Future[BlogPostData] =
    HttpJson.get[BlogPostData](s"/service/editorial/posts/${encodeURIComponent(id)}", signal).map(validate)

  def newPost(signal: Option[dom.AbortSignal]): Future[BlogPostData] =
    list(PostSearch(sort = "title", limit = 1, editorial = true), signal).zip(accounts.session(signal)).map { (table, state) =>
      val link = table.links.find(_.rel == "create").getOrElse(throw new HttpFailure(403))
      link.path("POST")
      val post = new BlogPost()
      post.content.set("")
      post.content.setDefault("")
      post.contentFormat.set("MARKDOWN")
      post.author.set(state.account.orNull)
      post.author.setDefault(state.account.orNull)
      new BlogPostData(post, Seq(link))
    }

  def save(link: ApiLink, body: js.Dynamic): Future[BlogPostData] = {
    val expectedMethod = if (link.rel == "create") "POST" else {
      require(link.rel == "update", "Unexpected save relation")
      "PATCH"
    }
    val path = link.path(expectedMethod)
    accounts.session().flatMap(state =>
      HttpJson.write[BlogPostData](path, expectedMethod, body, state.csrfToken)).map { result =>
        val saved = validate(result)
        require(saved.data.id.get.nonEmpty && saved.data.version.get >= 0, "Missing saved identity or version")
        saved
      }
  }

  def execute(link: ApiLink): Future[BlogPostData] = {
    // Validate before fetching CSRF or sending a command.
    val path = link.path("POST")
    accounts.session().flatMap(state =>
      HttpJson.post[BlogPostData](path, js.Dynamic.literal(), state.csrfToken)).map(validate)
  }

  private def validate(result: BlogPostData): BlogPostData = {
    require(result.data != null, "Missing editorial detail")
    // The backend mapper omits empty strings. A draft detail may legitimately have an empty body.
    if (result.data.status.get == "DRAFT" && result.data.content.get == null)
      { result.data.content.set(""); result.data.content.setDefault("") }
    require(result.data.content.get != null, "Missing editorial content")
    result
  }
}
