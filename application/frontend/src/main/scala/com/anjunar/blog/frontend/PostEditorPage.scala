package com.anjunar.blog.frontend

import org.scalajs.dom
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
import ui.core.layout.Label.label
import ui.core.layout.Paragraph.paragraph
import ui.core.layout.TextComponent.text
import ui.core.render.Cursor
import ui.forms.ErrorResponse
import ui.forms.Form.form
import ui.forms.Input.input
import ui.forms.TextAreaInput.textAreaInput
import ui.router.Router
import ui.router.RouterLink.routerLink

import scala.concurrent.ExecutionContext
import scala.scalajs.js

final class PostEditorPage(initial: BlogPostData, service: EditorialService)
    (using ExecutionContext) extends AbstractComponent {
  import SaveNotice.*

  val tagName = "section"
  private val wasNew = initial.data.id.get.isEmpty
  private val actions = new PostEditorActions(initial, service.save)
  private val post = actions.post

  override def compose(cursor: Cursor): Unit = {
    val translations = I18nRuntime.current(using this).get
    addDisposable(() => actions.dispose())
    render(this, cursor) {
      classes = "post-editor"
      paragraph { classes = "eyebrow"; text(i18n"Editorial") {} }
      heading(1) { text(post.id.flatMap(value => translations.text(
        if (value.isEmpty) i18n"New post" else i18n"Edit post"))) {} }
      paragraph { text(i18n"Save your text here. Publication is managed from the preview.") {} }
      form(post) { mountedForm ?=>
        classes = "post-form"
        mountedForm.setAttribute("novalidate", "")
        mountedForm.addDisposable(actions.errors.observe(values =>
          mountedForm.setErrorResponses(values.map(value => ErrorResponse(value.message, value.path)))))
        mountedForm.onHandler("submit") { event =>
          event.preventDefault()
          if (!actions.busy.get && !actions.blocked.get) {
            mountedForm.clearErrors()
            actions.generalError.set("")
            val bindings = mountedForm.validateBindings()
            val validation = mountedForm.validate()
            if (bindings.nonEmpty) actions.notice.set(BindingFailed)
            else if (validation.nonEmpty) actions.notice.set(Invalid)
            else actions.save(() => {
              if (wasNew && !actions.dirty.get) Router.replace(s"/editorial/posts/${post.id.get}/edit")
            })
          }
        }
        div {
          classes = "post-field"
          label { fieldLabel ?=> fieldLabel.setAttribute("for", "post-title"); text(i18n"Title") {} }
          val control = input("title") { fieldInput ?=>
            id = "post-title"
            fieldInput.setAttribute("aria-describedby", "post-title-errors")
          }
          control.addDisposable(control.invalid.observe(value => control.setAttribute("aria-invalid", value.toString)))
          paragraph { id = "post-title-errors"; classes = "field-error"; text(control.errors.map((values: js.Array[String]) => values.mkString(", "))) {} }
        }
        div {
          classes = "post-field"
          label { fieldLabel ?=> fieldLabel.setAttribute("for", "post-slug"); text(i18n"Slug") {} }
          val control = input("slug") { fieldInput ?=>
            id = "post-slug"
            spellCheck = false
            fieldInput.setAttribute("aria-describedby", "post-slug-help post-slug-errors")
          }
          control.addDisposable(control.invalid.observe(value => control.setAttribute("aria-invalid", value.toString)))
          paragraph { id = "post-slug-help"; classes = "field-help"; text(i18n"Use lowercase words separated by hyphens. Changing this changes the public URL.") {} }
          paragraph { id = "post-slug-errors"; classes = "field-error"; text(control.errors.map((values: js.Array[String]) => values.mkString(", "))) {} }
        }
        div {
          classes = "post-field"
          label { fieldLabel ?=> fieldLabel.setAttribute("for", "post-summary"); text(i18n"Summary (optional)") {} }
          val control = textAreaInput("summary") { fieldInput ?=>
            id = "post-summary"
            fieldInput.setAttribute("rows", "3")
            fieldInput.setAttribute("aria-describedby", "post-summary-help post-summary-errors")
          }
          control.addDisposable(control.invalid.observe(value => control.setAttribute("aria-invalid", value.toString)))
          paragraph { id = "post-summary-help"; classes = "field-help"; text(i18n"Up to 300 characters. Leave it empty to clear the summary.") {} }
          paragraph { id = "post-summary-errors"; classes = "field-error"; text(control.errors.map((values: js.Array[String]) => values.mkString(", "))) {} }
        }
        div {
          classes = "post-field"
          label { fieldLabel ?=> fieldLabel.setAttribute("for", "post-content"); text(i18n"Content") {} }
          val control = textAreaInput("content") { fieldInput ?=>
            id = "post-content"
            classes = "post-content-input"
            fieldInput.setAttribute("rows", "12")
            fieldInput.setAttribute("aria-describedby", "post-content-help post-content-errors")
          }
          control.addDisposable(control.invalid.observe(value => control.setAttribute("aria-invalid", value.toString)))
          paragraph { id = "post-content-help"; classes = "field-help"; text(i18n"Plain text for now. A draft may be empty; a published post needs content.") {} }
          paragraph { id = "post-content-errors"; classes = "field-error"; text(control.errors.map((values: js.Array[String]) => values.mkString(", "))) {} }
        }
        when(actions.notice.map(_.isError)) {
          div {
            role = "alert"
            classes = "form-error"
            paragraph {
              text(actions.notice.flatMap(value => translations.text(value match {
                case Invalid => i18n"Review the fields and save again."
                case Conflict => i18n"This post changed elsewhere. Your edits are still here. Copy what you need before discarding and reloading."
                case SignedOut => i18n"Your session ended. Your edits are still here. Sign in again before reloading."
                case Forbidden => i18n"You can no longer save this post. Your edits are still here."
                case BindingFailed => i18n"The form could not bind its fields. Saving is unavailable."
                case _ => i18n"The save could not be confirmed. Check the editorial list before trying again."
              }))) {}
            }
            when(actions.generalError.map(_.nonEmpty)) { paragraph { text(actions.generalError) {} } }
          }
        }
        div {
          classes = "editorial-controls"
          button(i18n"Save post") {
            buttonType("submit")
            disabled = actions.busy.flatMap(busy => actions.blocked.map(blocked => busy || blocked))
          }
          when(actions.busy) { paragraph { role = "status"; text(i18n"Saving… You can keep writing.") {} } }
          when(actions.notice.map(value => value == Saved || value == NewerEdits)) {
            paragraph {
              role = "status"
              text(actions.notice.flatMap(value => translations.text(
                if (value == Saved) i18n"Saved." else i18n"Saved. Your newer edits still need saving."))) {}
            }
          }
        }
      }
      div {
        classes = "editorial-controls"
        when(post.id.map(_.nonEmpty)) {
          routerLink(s"/editorial/posts/${post.id.get}") { text(i18n"Open preview") {} }
          when(actions.blocked) {
            button(i18n"Discard my edits and reload") {
              buttonType("button")
              onClick { _ =>
                if (cursor.isBrowser) dom.window.location.assign(Router.requireCurrent.hrefFor(s"/editorial/posts/${post.id.get}/edit"))
              }
            }
          }
        }
        routerLink("/editorial") { text(i18n"Back to editorial") {} }
        routerLink("/account") { text(i18n"Your account") {} }
      }
    }
  }
}
