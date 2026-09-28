package com.anjunar.blog.frontend

import org.scalatest.funsuite.AnyFunSuite
import ui.json.JsonMapper

import scala.scalajs.js

class PostMarkdownSpec extends AnyFunSuite {
  private val id = "8a1c1582-e841-4a27-a506-1a630337df48"

  test("image policy accepts only local media identities") {
    assert(PostMarkdown.mediaPolicy.resolve(s"/service/media/$id").exists(_.src == s"/service/media/$id"))
    Seq(s"https://example.test/service/media/$id", s"//example.test/service/media/$id",
      "data:image/png;base64,AA", "/service/media/not-an-id", s"/service/media/$id?download",
      s"/service/media/$id#fragment").foreach(source => assert(PostMarkdown.mediaPolicy.resolve(source).isEmpty))
  }

  test("old detail responses remain plain text and conversion sends both fields") {
    val post = JsonMapper.deserialize[BlogPost](js.JSON.parse(
      s"""{"id":"$id","version":0,"title":"Old post","slug":"old-post","content":"*literal* <tag>"}"""))
    assert(post.contentFormat.get == "PLAIN_TEXT")
    assert(!post.isDirty)
    post.content.set(PostMarkdown.fromPlainText(post.content.get))
    post.contentFormat.set("MARKDOWN")
    val body = post.writeBody()
    assert(body.contentFormat.asInstanceOf[String] == "MARKDOWN")
    assert(body.content.asInstanceOf[String] == "\\*literal\\* &lt;tag&gt;")
    assert(body.version.asInstanceOf[Int] == 0)
  }

  test("a late plain-text save cannot undo conversion or newer document input") {
    val post = JsonMapper.deserialize[BlogPost](js.JSON.parse(
      s"""{"id":"$id","version":0,"title":"Old post","slug":"old-post","content":"Original"}"""))
    post.content.set("Submitted")
    val submitted = post.snapshot
    post.contentFormat.set("MARKDOWN")
    post.content.set("**Newer document**")
    val saved = JsonMapper.deserialize[BlogPost](js.JSON.parse(
      s"""{"id":"$id","version":1,"title":"Old post","slug":"old-post","content":"Submitted","contentFormat":"PLAIN_TEXT"}"""))
    post.mergeSaved(saved, submitted)
    assert(post.content.get == "**Newer document**")
    assert(post.contentFormat.get == "MARKDOWN")
    assert(post.version.get == 1 && post.isDirty)
    val next = post.writeBody()
    assert(next.contentFormat.asInstanceOf[String] == "MARKDOWN")
    assert(next.content.asInstanceOf[String] == "**Newer document**")
  }
}
