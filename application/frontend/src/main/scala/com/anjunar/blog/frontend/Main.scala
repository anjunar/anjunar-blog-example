package com.anjunar.blog.frontend

import org.scalajs.dom
import ui.core.component.Runtime
import ui.core.render.DomCursor

object Main {
  def main(args: Array[String]): Unit = {
    val root = dom.document.getElementById("app")
    require(root != null, "The page must contain an element with id='app'")
    Runtime.mount(new BlogPage, DomCursor.root(root))
  }
}
