package com.anjunar.blog.frontend

import ui.core.component.AbstractComponent
import ui.core.dsl.AttributeDsl.*
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.DslLayer.render
import ui.core.dsl.EventDsl.onClick
import ui.core.i18n.{I18nRuntime, i18n}
import ui.core.layout.Button.{button, buttonType, disabled, disabled_=}
import ui.core.layout.Condition.when
import ui.core.layout.Div.div
import ui.core.layout.Heading.heading
import ui.core.layout.Paragraph.paragraph
import ui.core.layout.TextComponent.text
import ui.core.render.Cursor
import ui.router.RouterLink.routerLink

import scala.concurrent.ExecutionContext

final class EditorialPostPage(initial: BlogPostData, service: EditorialService, retry: () => Unit)
    (using ExecutionContext) extends AbstractComponent {
  val tagName = "section"
  private val actions = new EditorialActions(initial, service)

  override def compose(cursor: Cursor): Unit = {
    val translations = I18nRuntime.current(using this).get
    addDisposable(() => actions.dispose())
    render(this, cursor) {
      classes = "editorial-detail"
      paragraph { classes = "eyebrow"; text(i18n"Editorial preview") {} }
      heading(1) { text(actions.current.flatMap(_.data.title)) {} }
      paragraph {
        classes = "publication-status"
        role = "status"
        ariaLabel = translations.text(i18n"Publication status")
        text(actions.current.flatMap(value => translations.text(
          if (value.data.status.get == "PUBLISHED") i18n"Published" else i18n"Draft"))) {}
      }
      paragraph { text(actions.current.flatMap(_.data.summary.map(value => Option(value).getOrElse("")))) {} }
      div {
        classes = "editorial-controls"
        when(actions.current.map(_.links.exists(_.rel == "update"))) {
          routerLink(s"/editorial/posts/${initial.data.id.get}/edit") { text(i18n"Edit post") {} }
        }
        when(actions.current.map(_.links.exists(_.rel == "publish"))) {
          button(i18n"Publish") { buttonType("button"); disabled = actions.busy; onClick(_ => actions.run("publish")) }
        }
        when(actions.current.map(_.links.exists(_.rel == "retract"))) {
          button(i18n"Retract") { buttonType("button"); disabled = actions.busy; onClick(_ => actions.run("retract")) }
        }
        when(actions.current.map(_.links.exists(_.rel == "public"))) {
          routerLink(s"/posts/${initial.data.slug.get}") { text(i18n"Open public post") {} }
        }
      }
      when(actions.error.map(_ != 0)) {
        paragraph {
          role = "alert"
          text(actions.error.flatMap(code => translations.text(code match {
            case 401 => i18n"Your session ended. Sign in again."
            case 403 => i18n"This action is no longer allowed. Reload to check your access."
            case 409 => i18n"The post changed. Reload before trying again."
            case _ => i18n"The action could not be confirmed. Reload to check the post."
          }))) {}
        }
        div {
          classes = "editorial-controls"
          routerLink("/account") { text(i18n"Your account") {} }
          button(i18n"Reload post") { buttonType("button"); onClick(_ => retry()) }
        }
      }
      when(actions.busy) { paragraph { role = "status"; text(i18n"Saving…") {} } }
      paragraph { classes = "post-content"; text(actions.current.flatMap(_.data.content.map(value => Option(value).getOrElse("")))) {} }
      routerLink("/editorial") { text(i18n"Back to editorial") {} }
    }
  }
}
