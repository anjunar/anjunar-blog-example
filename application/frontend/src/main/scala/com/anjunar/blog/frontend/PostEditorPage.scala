package com.anjunar.blog.frontend

import org.scalajs.dom
import ui.core.component.AbstractComponent
import ui.core.dsl.AttributeDsl
import ui.core.dsl.AttributeDsl.*
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.DslLayer.{child, render}
import ui.core.dsl.EventDsl.{on, onClick}
import ui.core.i18n.{I18nRuntime, i18n}
import ui.core.layout.Button.{button, buttonType, disabled, disabled_=}
import ui.core.layout.Condition.when
import ui.core.layout.Div.div
import ui.core.layout.Heading.heading
import ui.core.layout.Label.label
import ui.core.layout.Paragraph.paragraph
import ui.core.layout.TextComponent.text
import ui.core.render.Cursor
import ui.forms.{ComboBox, ErrorResponse}
import ui.forms.ComboBox.comboBox
import ui.forms.Form.form
import ui.forms.Input.input
import ui.forms.TextAreaInput.textAreaInput
import ui.router.Router
import ui.router.RouterLink.routerLink

import scala.concurrent.ExecutionContext
import scala.scalajs.js

final class PostEditorPage(initial: BlogPostData, service: EditorialService,
    initialCatalog: Option[CatalogData] = None, catalogService: CatalogService = null)
    (using ExecutionContext) extends AbstractComponent {
  import SaveNotice.*

  val tagName = "section"
  private val wasNew = initial.data.id.get.isEmpty
  private val actions = new PostEditorActions(initial, service.save)
  private val post = actions.post
  private val catalog = initialCatalog.map(new CatalogState(_, catalogService))

  override def compose(cursor: Cursor): Unit = {
    val translations = I18nRuntime.current(using this).get
    addDisposable(() => actions.dispose())
    catalog.foreach(value => addDisposable(() => value.dispose()))
    render(this, cursor) {
      classes = "post-editor"
      paragraph { classes = "eyebrow"; text(i18n"Editorial") {} }
      heading(1) { text(post.id.flatMap(value => translations.text(
        if (value.isEmpty) i18n"New post" else i18n"Edit post"))) {} }
      paragraph { text(i18n"Save your text here. Publication is managed from the preview.") {} }
      form(post) { mountedForm ?=>
        classes = "post-form"
        AttributeDsl.setAttribute("novalidate", "")
        mountedForm.addDisposable(actions.errors.observe(values =>
          mountedForm.setErrorResponses(values.map(value => ErrorResponse(value.message, value.path)))))
        on("submit") { event =>
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
          label { AttributeDsl.setAttribute("for", "post-title"); text(i18n"Title") {} }
          val control = input("title") { fieldInput ?=>
            id = "post-title"
            AttributeDsl.setAttribute("aria-describedby", "post-title-errors")
            fieldInput.addDisposable(fieldInput.invalid.observe(value =>
              AttributeDsl.setAttribute("aria-invalid", value.toString)))
          }
          paragraph { id = "post-title-errors"; classes = "field-error"; text(control.errors.map((values: js.Array[String]) => values.mkString(", "))) {} }
        }
        div {
          classes = "post-field"
          label { AttributeDsl.setAttribute("for", "post-slug"); text(i18n"Slug") {} }
          val control = input("slug") { fieldInput ?=>
            id = "post-slug"
            spellCheck = false
            AttributeDsl.setAttribute("aria-describedby", "post-slug-help post-slug-errors")
            fieldInput.addDisposable(fieldInput.invalid.observe(value =>
              AttributeDsl.setAttribute("aria-invalid", value.toString)))
          }
          paragraph { id = "post-slug-help"; classes = "field-help"; text(i18n"Use lowercase words separated by hyphens. Changing this changes the public URL.") {} }
          paragraph { id = "post-slug-errors"; classes = "field-error"; text(control.errors.map((values: js.Array[String]) => values.mkString(", "))) {} }
        }
        div {
          classes = "post-field"
          label { AttributeDsl.setAttribute("for", "post-summary"); text(i18n"Summary (optional)") {} }
          val control = textAreaInput("summary") { fieldInput ?=>
            id = "post-summary"
            AttributeDsl.setAttribute("rows", "3")
            AttributeDsl.setAttribute("aria-describedby", "post-summary-help post-summary-errors")
            fieldInput.addDisposable(fieldInput.invalid.observe(value =>
              AttributeDsl.setAttribute("aria-invalid", value.toString)))
          }
          paragraph { id = "post-summary-help"; classes = "field-help"; text(i18n"Up to 300 characters. Leave it empty to clear the summary.") {} }
          paragraph { id = "post-summary-errors"; classes = "field-error"; text(control.errors.map((values: js.Array[String]) => values.mkString(", "))) {} }
        }
        div {
          classes = "post-field"
          label { AttributeDsl.setAttribute("for", "post-content"); text(i18n"Content") {} }
          val control = textAreaInput("content") { fieldInput ?=>
            id = "post-content"
            classes = "post-content-input"
            AttributeDsl.setAttribute("rows", "12")
            AttributeDsl.setAttribute("aria-describedby", "post-content-help post-content-errors")
            fieldInput.addDisposable(fieldInput.invalid.observe(value =>
              AttributeDsl.setAttribute("aria-invalid", value.toString)))
          }
          paragraph { id = "post-content-help"; classes = "field-help"; text(i18n"Plain text for now. A draft may be empty; a published post needs content.") {} }
          paragraph { id = "post-content-errors"; classes = "field-error"; text(control.errors.map((values: js.Array[String]) => values.mkString(", "))) {} }
        }
        catalog.foreach { options =>
          div {
            classes = "post-field"
            label { id = "post-author-label"; text(i18n"Author") {} }
            val control = comboBox[Account]("author") { choice ?=>
              id = "post-author"
              ariaLabelledBy = "post-author-label"
              AttributeDsl.setAttribute("aria-describedby", "post-author-help post-author-errors")
              ComboBox.converter = (account: Account) =>
                Option(account.displayName.get).filter(_.nonEmpty)
                  .getOrElse(translations.text(i18n"Unnamed author").get)
              ComboBox.identityBy = (account: Account) => account.id.get
              ComboBox.placeholder = translations.text(i18n"Choose an author").get
              choice.addDisposable(options.authors.observe { rows =>
                ComboBox.items[Account].setAll(rows.map { row =>
                  Option(post.author.get).filter(_.id.get == row.data.id.get).getOrElse(row.data)
                })
              })
              choice.addDisposable(choice.invalid.observe(value =>
                AttributeDsl.setAttribute("aria-invalid", value.toString)))
            }
            paragraph { id = "post-author-help"; classes = "field-help"; text(i18n"Only the public name appears with the post.") {} }
            paragraph { id = "post-author-errors"; classes = "field-error"; text(control.errors.map((values: js.Array[String]) => values.mkString(", "))) {} }
            button(i18n"Clear author") { buttonType("button"); onClick(_ => post.author.set(null)) }
            when(options.nextAuthors.map(_.nonEmpty)) {
              button(i18n"Load more authors") { buttonType("button"); disabled = options.busy; onClick(_ => options.moreAuthors()) }
            }
          }
          div {
            classes = "post-field"
            label { id = "post-tags-label"; text(i18n"Tags") {} }
            val control = child(new TagSelection(options.tags.map(_.map(_.data)))) {}
            paragraph { id = "post-tags-help"; classes = "field-help"; text(i18n"Choose up to 20 shared tags. Removing a selection keeps the tag available to other posts.") {} }
            paragraph { id = "post-tags-errors"; classes = "field-error"; text(control.errors.map((values: js.Array[String]) => values.mkString(", "))) {} }
            button(i18n"Clear tags") { buttonType("button"); onClick(_ => post.tags.clear()) }
            when(options.nextTags.map(_.nonEmpty)) {
              button(i18n"Load more tags") { buttonType("button"); disabled = options.busy; onClick(_ => options.moreTags()) }
            }
          }
          when(options.failed) { paragraph { role = "alert"; text(i18n"More choices could not be loaded. Try the load button again.") {} } }
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
