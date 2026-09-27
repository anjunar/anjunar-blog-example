package com.anjunar.blog.frontend

import scala.scalajs.js.URIUtils.encodeURIComponent

final case class PostSearch(query: String = "", status: String = "", sort: String = "newest",
    offset: Int = 0, limit: Int = 20, editorial: Boolean = false) {
  def path: String = if (editorial) "/editorial" else "/"
  def filtered: Boolean = query.nonEmpty || status.nonEmpty

  def queryString(includeDefaults: Boolean = false): String = {
    val defaultSort = if (editorial) "title" else "newest"
    val values = Seq(
      Option.when(includeDefaults || offset > 0)("offset" -> offset.toString),
      Option.when(includeDefaults || limit != 20)("limit" -> limit.toString),
      Option.when(query.nonEmpty)("q" -> query),
      Option.when(status.nonEmpty)("status" -> status),
      Option.when(sort != defaultSort)("sort" -> sort)
    ).flatten
    values.map((name, value) => s"$name=${encodeURIComponent(value)}").mkString("&")
  }

  def url: String = {
    val query = queryString()
    if (query.isEmpty) path else s"$path?$query"
  }
}

object PostSearch {
  def parse(read: String => Option[String], editorial: Boolean): PostSearch = {
    val query = read("q").getOrElse("").trim
    val status = read("status").getOrElse("")
    val sort = read("sort").filter(_.nonEmpty).getOrElse(if (editorial) "title" else "newest")
    val offset = read("offset").getOrElse("0").toIntOption.filter(_ >= 0)
    val limit = read("limit").getOrElse("20").toIntOption.filter(value => value >= 1 && value <= 100)
    val invalidText = query.length > 100 || query.exists(c => c < ' ' || (c >= 127 && c <= 159))
    val statuses = if (editorial) Set("", "DRAFT", "PUBLISHED") else Set("", "PUBLISHED")
    if (invalidText || !statuses.contains(status) ||
        !Set("newest", "oldest", "title", "title-desc").contains(sort) || offset.isEmpty || limit.isEmpty)
      throw new HttpFailure(400)
    PostSearch(query, status, sort, offset.get, limit.get, editorial)
  }
}
