package com.anjunar.blog.frontend

import ui.core.component.AbstractComponent
import ui.core.dsl.AttributeDsl.*
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.DslLayer.render
import ui.core.i18n.i18n
import ui.core.layout.TextComponent.text
import ui.core.render.Cursor

final class LoadingPage extends AbstractComponent {
  val tagName = "p"

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      classes = "loading-state"
      role = "status"
      text(i18n"Loading posts…") {}
    }
}
