package com.anjunar.blog.frontend

import org.scalajs.dom
import ui.core.component.Runtime
import ui.core.render.DomCursor

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.scalajs.js.JSConverters.*

object Main {
  @JSExportTopLevel("render")
  def render(url: String): js.Promise[js.Object] = {
    val document = new BlogDocument(url)
    Runtime.renderToStringAsync(cursor => Runtime.mount(document, cursor), timeoutMs = 10000).map { html =>
      js.Dynamic.literal(html = ("<!doctype html>" + html), status = document.responseStatus)
    }.toJSPromise
  }

  def boot(): Unit = {
    val root = dom.document.getElementById("app")
    require(root != null, "The page must contain an element with id='app'")
    // Chapter 22 remounts the page. Chapter 23 will reuse this tree through hydration.
    root.textContent = ""
    val service = new BlogService
    val actions = new BlogActions(() => dom.window.location.reload())
    Runtime.mount(new BlogPage(service, actions), DomCursor.root(root))
  }
}
