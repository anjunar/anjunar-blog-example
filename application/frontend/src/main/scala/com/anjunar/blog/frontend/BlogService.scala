package com.anjunar.blog.frontend

import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js.URIUtils.encodeURIComponent

final class BlogService(initial: InitialPageData = InitialPageData.live())(using ExecutionContext) {
  def list(search: PostSearch, signal: Option[dom.AbortSignal], locale: String = "en"): Future[BlogPostTable] =
    initial.get[BlogPostTable](s"/service/blog/posts?${search.queryString(includeDefaults = true)}&locale=$locale", signal)
      .map { table =>
        require(table.size >= 0 && table.rows != null, "Invalid post table")
        table.rows.foreach(row => require(row != null && row.data != null, "Missing post data"))
        table
      }(using ExecutionContext.parasitic)

  def detail(slug: String, signal: Option[dom.AbortSignal], locale: String = "en"): Future[BlogPost] =
    initial.get[BlogPostData](s"/service/blog/posts/${encodeURIComponent(slug)}?locale=$locale", signal)
      .map { result =>
        require(result.data != null && result.data.content.get != null, "Missing post detail")
        result.data
      }(using ExecutionContext.parasitic)
}
