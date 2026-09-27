package com.anjunar.blog.frontend

import org.scalatest.funsuite.AnyFunSuite
import ui.json.JsonMapper

import scala.scalajs.js

class BlogPostJsonSpec extends AnyFunSuite {
  test("the list envelope preserves identity, zero version and fields selected by the graph") {
    val table = JsonMapper.deserialize[BlogPostTable](js.JSON.parse(
      """{"@type":"Table","size":1,"rows":[{"@type":"Data","data":{
        |"@type":"BlogPost","id":"post-id","version":0,"slug":"first-post",
        |"title":"First post","status":"PUBLISHED","publishedAt":"2026-09-27T10:15:42.123456Z"
        |},"schema":{"entries":[{"name":"title","type":"String"}]}}]}""".stripMargin
    ))
    val post = table.rows.head.data
    assert(table.size == 1L)
    assert(post.id.get == "post-id")
    assert(post.version.get == 0L)
    assert(post.slug.get == "first-post")
    assert(post.title.get == "First post")
    assert(post.status.get == "PUBLISHED")
    assert(post.publishedAt.get.contains("2026-09-27T10:15:42.123456Z"))
    assert(post.content.get.isEmpty)
    assert(post.summary.get.isEmpty)
  }

  test("detail content and optional summary map without changing editorial text") {
    val row = JsonMapper.deserialize[BlogPostData](js.JSON.parse(
      """{"data":{"content":"<script>literal text</script>","summary":"A summary","version":7}}"""
    ))
    assert(row.data.content.get.contains("<script>literal text</script>"))
    assert(row.data.summary.get.contains("A summary"))
    assert(row.data.version.get == 7L)
  }

  test("omitted rows keep the empty default and preserve a zero total") {
    val table = JsonMapper.deserialize[BlogPostTable](js.JSON.parse("""{"@type":"Table","size":0}"""))
    assert(table.rows.isEmpty)
    assert(table.size == 0L)
  }

  test("an empty page may still have a nonzero total") {
    val table = JsonMapper.deserialize[BlogPostTable](js.JSON.parse("""{"size":23}"""))
    assert(table.rows.isEmpty)
    assert(table.size == 23L)
  }

  test("explicit null optional values map to None") {
    val row = JsonMapper.deserialize[BlogPostData](js.JSON.parse(
      """{"data":{"summary":null,"publishedAt":null,"content":null}}"""
    ))
    assert(row.data.summary.get.isEmpty)
    assert(row.data.publishedAt.get.isEmpty)
    assert(row.data.content.get.isEmpty)
  }

  test("missing version is distinguishable from version zero") {
    val row = JsonMapper.deserialize[BlogPostData](js.JSON.parse("""{"data":{"title":"A post"}}"""))
    assert(row.data.version.get == -1L)
  }
}
