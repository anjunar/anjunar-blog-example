package com.anjunar.blog.frontend

import ui.core.component.AbstractComponent
import ui.core.dsl.AttributeDsl.*
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.DslLayer.render
import ui.core.dsl.EventDsl.onClick
import ui.core.i18n.i18n
import ui.core.layout.Button.{button, buttonType}
import ui.core.layout.Heading.heading
import ui.core.layout.Paragraph.paragraph
import ui.core.render.Cursor
import ui.core.layout.TextComponent.text
import ui.router.RouterLink.routerLink

final class ErrorPage(status: Int, actions: BlogActions) extends AbstractComponent {
  val tagName = "section"

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      classes = "error-state"
      role = "alert"
      heading(1) {
        text(status match {
          case 401 => i18n"Sign in required"
          case 403 => i18n"Access denied"
          case 404 => i18n"Post not found"
          case 400 => i18n"Invalid page"
          case _ => i18n"Posts are unavailable"
        }) {}
      }
      paragraph {
        text(status match {
          case 401 => i18n"Sign in to open the editorial workspace."
          case 403 => i18n"Your account does not have access to this workspace."
          case 404 => i18n"This post is not available to read."
          case 400 => i18n"Open the latest posts to start again."
          case _ => i18n"We could not load the posts. Please try again."
        }) {}
      }
      if (status == 503) {
        button(i18n"Try again") {
          buttonType("button")
          onClick(_ => actions.retry())
        }
      }
      if (status == 401 || status == 403) {
        paragraph { routerLink("/account") { text(i18n"Your account") {} } }
      }
      routerLink("/") { text(i18n"Back to latest posts") {} }
    }
}
