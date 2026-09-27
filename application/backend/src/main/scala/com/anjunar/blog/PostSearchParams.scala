package com.anjunar.blog

import jakarta.ws.rs.{BadRequestException, DefaultValue, QueryParam}

final class PostSearchParams {
  @QueryParam("q") var query: String = ""
  @QueryParam("status") var status: String = ""
  @QueryParam("sort") var sort: String = ""
  @QueryParam("offset") @DefaultValue("0") var offset: String = "0"
  @QueryParam("limit") @DefaultValue("20") var limit: String = "20"

  def search(editorial: Boolean): BlogPostSearch = {
    val text = Option(query).getOrElse("").trim
    if (text.length > 100 || text.exists(Character.isISOControl))
      throw new BadRequestException("q must contain at most 100 characters and no control characters")
    val selectedStatus = Option(status).getOrElse("") match {
      case "" => None
      case "DRAFT" if editorial => Some(BlogPostStatus.DRAFT)
      case "PUBLISHED" => Some(BlogPostStatus.PUBLISHED)
      case _ => throw new BadRequestException("Invalid status filter")
    }
    val selectedSort = Option(sort).filter(_.nonEmpty).getOrElse(if (editorial) "title" else "newest")
    if (!Set("newest", "oldest", "title", "title-desc").contains(selectedSort))
      throw new BadRequestException("Invalid sort order")
    val start = Option(offset).flatMap(_.toIntOption).filter(_ >= 0)
      .getOrElse(throw new BadRequestException("offset must be a nonnegative integer"))
    val size = Option(limit).flatMap(_.toIntOption).filter(value => value >= 1 && value <= 100)
      .getOrElse(throw new BadRequestException("limit must be between 1 and 100"))
    BlogPostSearch(text, if (editorial) selectedStatus else Some(BlogPostStatus.PUBLISHED), selectedSort, start, size)
  }
}
