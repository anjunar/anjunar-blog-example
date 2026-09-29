package com.anjunar.blog.frontend

import org.scalajs.dom
import ui.core.component.AbstractComponent
import ui.core.dsl.AttributeDsl
import ui.core.dsl.AttributeDsl.*
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.DslLayer.{child, render}
import ui.core.dsl.EventDsl.{on, onClick}
import ui.core.i18n.i18n
import ui.core.layout.Button.{button, buttonType, disabled, disabled_=}
import ui.core.layout.Condition.when
import ui.core.layout.Div.div
import ui.core.layout.Heading.heading
import ui.core.layout.Label.label
import ui.core.layout.Paragraph.paragraph
import ui.core.layout.TextComponent.text
import ui.core.render.Cursor
import ui.core.state.Property
import ui.editor.Editor.*
import ui.editor.MediaUploadStatus
import ui.editor.plugins.*
import ui.forms.ErrorResponse
import ui.forms.Form.form
import ui.forms.Input.input
import ui.forms.TextAreaInput.textAreaInput
import ui.router.Router
import ui.router.RouterLink.routerLink

import scala.concurrent.ExecutionContext
import scala.scalajs.js

final class TranslationEditorPage(post: BlogPost, initial: TranslationData,
    service: TranslationService, media: MediaService)(using ExecutionContext) extends AbstractComponent {
  val tagName = "section"
  private val actions = new TranslationActions(initial, service.send)
  private val translation = actions.translation
  private val sourceMode = Property(false)
  private val upload = Property(MediaUploadStatus())

  override def compose(cursor: Cursor): Unit = {
    addDisposable(() => actions.dispose())
    LanguageNavigation.protect((translation.fields ++ Seq(actions.busy, actions.dirty, upload))*) {
      actions.dirty.get || actions.busy.get || upload.get.pending > 0
    }(using this)
    render(this, cursor) {
      classes = "translation-editor post-editor"
      heading(1) { text(i18n"German translation") {} }
      paragraph { text(i18n"The English source stays unchanged. Save a draft, then publish the translation separately.") {} }
      if (post.status.get != "PUBLISHED") {
        paragraph { text(i18n"The post itself is a draft. Neither language is public yet.") {} }
      }
      paragraph {
        role = "status"
        text(translation.published.map(value => if (value) i18n"Published" else i18n"Draft")) {}
      }
      div {
        classes = "translation-columns"
        div {
          classes = "translation-source"
          heading(2) { text(i18n"English source") {} }
          div {
            AttributeDsl.setAttribute("lang", "en")
            heading(3) { text(post.title) {} }
            paragraph { text(post.summary.map(value => Option(value).getOrElse(""))) {} }
            child(new PostContent(post)) {}
          }
        }
        form(translation) { mountedForm ?=>
          classes = "post-form"
          AttributeDsl.setAttribute("novalidate", "")
          mountedForm.addDisposable(actions.errors.observe(values =>
            mountedForm.setErrorResponses(values.map(value => ErrorResponse(value.message, value.path)))))
          on("submit") { event =>
            event.preventDefault()
            if (!actions.busy.get && !actions.blocked.get && upload.get.pending == 0) {
              mountedForm.clearErrors()
              actions.generalError.set("")
              if (mountedForm.validateBindings().nonEmpty) actions.notice.set(SaveNotice.BindingFailed)
              else if (mountedForm.validate().nonEmpty) actions.notice.set(SaveNotice.Invalid)
              else actions.save()
            }
          }
          div {
            classes = "post-field"
            label { AttributeDsl.setAttribute("for", "translation-title"); text(i18n"Title") {} }
            val control = input("title") { fieldInput ?=>
              fieldInput.addDisposable(fieldInput.invalid.observe(value => AttributeDsl.setAttribute("aria-invalid", value.toString)))
              id = "translation-title"
              AttributeDsl.setAttribute("lang", "de")
              AttributeDsl.setAttribute("aria-describedby", "translation-title-errors")
            }
            paragraph { id = "translation-title-errors"; classes = "field-error"; text(control.errors.map((values: js.Array[String]) => values.mkString(", "))) {} }
          }
          div {
            classes = "post-field"
            label { AttributeDsl.setAttribute("for", "translation-summary"); text(i18n"Summary (optional)") {} }
            val control = textAreaInput("summary") { fieldInput ?=>
              fieldInput.addDisposable(fieldInput.invalid.observe(value => AttributeDsl.setAttribute("aria-invalid", value.toString)))
              id = "translation-summary"
              AttributeDsl.setAttribute("lang", "de")
              AttributeDsl.setAttribute("rows", "3")
              AttributeDsl.setAttribute("aria-describedby", "translation-summary-errors")
            }
            paragraph { id = "translation-summary-errors"; classes = "field-error"; text(control.errors.map((values: js.Array[String]) => values.mkString(", "))) {} }
          }
          div {
            classes = "post-field"
            paragraph { id = "translation-content-label"; text(i18n"Content") {} }
            button(sourceMode.map(value => if (value) i18n"Visual editor" else i18n"Edit Markdown")) {
              buttonType("button")
              disabled = upload.map(_.pending > 0)
              onClick(_ => sourceMode.set(!sourceMode.get))
            }
            val control = editor("content") { fieldInput ?=>
              fieldInput.addDisposable(fieldInput.invalid.observe(value => AttributeDsl.setAttribute("aria-invalid", value.toString)))
              ariaLabelledBy = "translation-content-label"
              AttributeDsl.setAttribute("lang", "de")
              AttributeDsl.setAttribute("aria-describedby", "translation-content-errors")
              showModeActions = false
              markdownMode = sourceMode
              mediaUrlPolicy = PostMarkdown.mediaPolicy
              mediaUploader = PostMarkdown.uploader(media)
              onMediaStatus = status => upload.set(status)
              basePlugin()
              headingPlugin()
              listPlugin()
              linkPlugin()
              imagePlugin()
              codePlugin()
            }
            paragraph { id = "translation-content-errors"; classes = "field-error"; text(control.errors.map((values: js.Array[String]) => values.mkString(", "))) {} }
          }
          div {
            classes = "editorial-controls"
            button(i18n"Save translation") {
              buttonType("submit")
              disabled = actions.busy.flatMap(busy => actions.blocked.flatMap(blocked =>
                upload.map(value => busy || blocked || value.pending > 0)))
            }
            when(actions.links.map(_.exists(_.rel == "publish"))) {
              button(i18n"Publish translation") {
                buttonType("button")
                disabled = actions.busy.flatMap(busy => actions.dirty.flatMap(dirty =>
                  actions.blocked.flatMap(blocked => upload.map(value => busy || dirty || blocked || value.pending > 0))))
                onClick(_ => actions.run("publish"))
              }
            }
            when(actions.links.map(_.exists(_.rel == "retract"))) {
              button(i18n"Retract translation") {
                buttonType("button")
                disabled = actions.busy.flatMap(busy => actions.dirty.flatMap(dirty =>
                  actions.blocked.flatMap(blocked => upload.map(value => busy || dirty || blocked || value.pending > 0))))
                onClick(_ => actions.run("retract"))
              }
            }
          }
          when(actions.busy) { paragraph { role = "status"; text(i18n"Saving…") {} } }
          when(actions.notice.map(_ != SaveNotice.Idle)) {
            paragraph {
              role = "status"
              text(actions.notice.map(value => value match {
                case SaveNotice.Saved => i18n"Translation saved."
                case SaveNotice.NewerEdits => i18n"Saved. Your newer edits still need saving."
                case SaveNotice.Invalid | SaveNotice.BindingFailed => i18n"Check the highlighted fields."
                case SaveNotice.Conflict => i18n"The translation changed. Reload before saving again."
                case SaveNotice.SignedOut => i18n"Your session ended. Sign in again."
                case SaveNotice.Forbidden => i18n"This action is no longer allowed. Reload to check your access."
                case _ => i18n"The action could not be confirmed. Reload to check the post."
              })) {}
            }
          }
          when(actions.generalError.map(_.nonEmpty)) {
            paragraph { role = "alert"; text(actions.generalError) {} }
          }
          when(actions.blocked) {
            button(i18n"Discard my edits and reload") {
              buttonType("button")
              onClick { _ =>
                if (cursor.isBrowser) dom.window.location.assign(Router.requireCurrent.hrefFor(s"/editorial/posts/${post.id.get}/translations/de"))
              }
            }
          }
          when(actions.dirty.map(!_).flatMap(clean => actions.busy.map(busy => clean && !busy))) {
            routerLink(s"/editorial/posts/${post.id.get}") { text(i18n"Back to post") {} }
          }
        }
      }
    }
  }
}
