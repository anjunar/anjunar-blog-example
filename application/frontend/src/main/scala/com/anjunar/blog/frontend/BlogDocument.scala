package com.anjunar.blog.frontend

import ui.core.document.{DocumentHead, HeadEntry}
import ui.core.dsl.AttributeDsl.*
import ui.core.dsl.DslLayer.{child, render}
import ui.core.layout.Body.body
import ui.core.layout.Div.div
import ui.core.layout.Head.head
import ui.core.layout.Html
import ui.core.render.Cursor

import scala.concurrent.ExecutionContext

final class BlogDocument(url: String)(using ExecutionContext) extends Html {
  private val documentHead = new DocumentHead
  private val page = new BlogPage(new BlogService, new BlogActions(() => ()), Some(url))

  def responseStatus: Int = page.responseStatus

  override def compose(cursor: Cursor): Unit = {
    DocumentHead.provide(documentHead)(using this)
    documentHead.bind(
      HeadEntry.charset(),
      HeadEntry.meta("viewport", "width=device-width, initial-scale=1"),
      HeadEntry.title("Anjunar Journal"),
      HeadEntry.link("icon", "data:,"),
      HeadEntry("style:main", "link", Seq("rel" -> "stylesheet", "href" -> "/style.css")),
      HeadEntry("style:editor", "link", Seq("rel" -> "stylesheet", "href" -> "/editor.css")),
      HeadEntry.script("client", "/main.js", "type" -> "module")
    )(using this)
    render(this, cursor) {
      lang = if (url.takeWhile(_ != '?').split("/").lift(1).contains("de")) "de" else "en"
      head {}
      body {
        div {
          id = "app"
          child(page) {}
        }
      }
    }
  }
}
