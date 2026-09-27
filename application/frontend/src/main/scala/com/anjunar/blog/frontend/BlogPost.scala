package com.anjunar.blog.frontend

import ui.core.state.Property
import ui.json.JsonId

final class BlogPost {
  @JsonId
  val id: Property[String] = Property("")
  val version: Property[Long] = Property(-1L)
  val slug: Property[String] = Property("")
  val title: Property[String] = Property("")
  // The list graph omits content. Only the detail request supplies it.
  val content: Property[Option[String]] = Property(None)
  val summary: Property[Option[String]] = Property(None)
  val status: Property[String] = Property("DRAFT")
  val publishedAt: Property[Option[String]] = Property(None)
}

final class BlogPostData(var data: BlogPost = null)

final class BlogPostTable(
    var rows: Seq[BlogPostData] = Seq.empty,
    var size: Long = -1L
)
