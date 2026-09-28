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
import ui.core.statement.Foreach.foreach
import ui.forms.ErrorResponse
import ui.forms.Form.{form, editable, editable_=}
import ui.forms.Input.input
import ui.router.RouterLink.routerLink

import scala.concurrent.ExecutionContext
import scala.scalajs.js

final class RelationshipPage(initial: CatalogData, service: CatalogService)
    (using ExecutionContext) extends AbstractComponent {
  val tagName = "section"
  private val catalog = new CatalogState(initial, service)

  override def compose(cursor: Cursor): Unit = {
    addDisposable(() => catalog.dispose())
    render(this, cursor) {
      classes = "relationship-page"
      heading(1) { text(i18n"Authors and tags") {} }
      paragraph { text(i18n"Set public author names and maintain the tags shared by your posts.") {} }
      when(catalog.saved) { paragraph { role = "status"; text(i18n"Changes saved.") {} } }
      when(catalog.failed) { paragraph { role = "alert"; text(i18n"More entries could not be loaded. Try again.") {} } }
      heading(2) { text(i18n"Authors") {} }
      paragraph { text(i18n"Only active administrators can be selected as authors. Existing posts keep their attribution.") {} }
      foreach(catalog.authors) { row => child(new AuthorEditor(row, service, catalog.recordAuthor)) {} }
      when(catalog.nextAuthors.map(_.nonEmpty)) {
        button(i18n"Load more authors") { buttonType("button"); disabled = catalog.busy; onClick(_ => catalog.moreAuthors()) }
      }
      heading(2) { text(i18n"Tags") {} }
      catalog.createTag.foreach { link =>
        child(new TagEditor(new BlogTagData(new BlogTag(), Seq(link)), service, catalog.recordTag)) {}
      }
      foreach(catalog.tags) { row => child(new TagEditor(row, service, catalog.recordTag)) {} }
      when(catalog.nextTags.map(_.nonEmpty)) {
        button(i18n"Load more tags") { buttonType("button"); disabled = catalog.busy; onClick(_ => catalog.moreTags()) }
      }
      paragraph { routerLink("/editorial") { text(i18n"Back to editorial") {} } }
    }
  }
}

final class AuthorEditor(initial: AuthorData, service: CatalogService, saved: AuthorData => Unit)
    (using ExecutionContext) extends AbstractComponent {
  val tagName = "section"
  private val account = initial.data
  private val update = initial.links.find(_.rel == "update").getOrElse(throw new HttpFailure(403))
  private val actions = new MetadataSave[AuthorData](body => service.saveAuthor(update, body), saved)

  override def compose(cursor: Cursor): Unit = {
    val translations = I18nRuntime.current(using this).get
    addDisposable(() => actions.dispose())
    render(this, cursor) {
      classes = "catalog-editor"
      LanguageNavigation.protect(actions.busy, account.displayName, account.version) {
        actions.busy.get || account.displayName.isDirty
      }
      form(account) { mounted ?=>
        ariaLabel = translations.text(i18n"Author details")
        editable = actions.busy.map(!_)
        mounted.addDisposable(actions.fields.observe(values =>
          mounted.setErrorResponses(values.map(value => ErrorResponse(value.message, value.path)))))
        on("submit") { event =>
          event.preventDefault()
          mounted.clearErrors()
          if (mounted.validateBindings().isEmpty && mounted.validate().isEmpty) actions(account.writeBody())
        }
        label { AttributeDsl.setAttribute("for", s"author-name-${account.id.get}"); text(i18n"Public name") {} }
        val control = input("displayName") {
          id = s"author-name-${account.id.get}"
          AttributeDsl.setAttribute("aria-describedby", s"author-errors-${account.id.get}")
        }
        paragraph { id = s"author-errors-${account.id.get}"; classes = "field-error"; text(control.errors.map((values: js.Array[String]) => values.mkString(", "))) {} }
        when(actions.error.map(_.nonEmpty)) {
          paragraph {
            role = "alert"
            text(actions.error.flatMap(value => translations.text(value match {
              case Some(400) => i18n"Review the public name."
              case Some(409) => i18n"This author changed. Reload before saving again."
              case _ => i18n"The save could not be confirmed. Reload to check the author."
            }))) {}
          }
        }
        button(i18n"Save author") { buttonType("submit"); disabled = actions.busy.flatMap(busy => actions.blocked.map(blocked => busy || blocked)) }
        when(actions.blocked) {
          button(i18n"Reload authors and tags") {
            buttonType("button")
            onClick(_ => if (cursor.isBrowser) dom.window.location.reload())
          }
        }
      }
    }
  }
}

final class TagEditor(initial: BlogTagData, service: CatalogService, saved: BlogTagData => Unit)
    (using ExecutionContext) extends AbstractComponent {
  val tagName = "section"
  private val tag = initial.data
  private val creating = tag.id.get.isEmpty
  private val operation = initial.links.find(_.rel == (if (creating) "create" else "update"))
    .getOrElse(throw new HttpFailure(403))
  private val suffix = if (creating) "new" else tag.id.get
  private val actions = new MetadataSave[BlogTagData](body => service.saveTag(operation, body), value => {
    saved(value)
    if (creating) {
      tag.name.set(""); tag.name.setDefault("")
      tag.slug.set(""); tag.slug.setDefault("")
    }
  })

  override def compose(cursor: Cursor): Unit = {
    val translations = I18nRuntime.current(using this).get
    addDisposable(() => actions.dispose())
    render(this, cursor) {
      classes = "catalog-editor"
      LanguageNavigation.protect(actions.busy, tag.name, tag.slug, tag.version) {
        actions.busy.get || tag.name.isDirty || tag.slug.isDirty
      }
      heading(3) { text(if (creating) translations.text(i18n"New tag") else tag.name) {} }
      form(tag) { mounted ?=>
        ariaLabel = if (creating) translations.text(i18n"New tag") else tag.name
        editable = actions.busy.map(!_)
        mounted.addDisposable(actions.fields.observe(values =>
          mounted.setErrorResponses(values.map(value => ErrorResponse(value.message, value.path)))))
        on("submit") { event =>
          event.preventDefault()
          mounted.clearErrors()
          if (mounted.validateBindings().isEmpty && mounted.validate().isEmpty) actions(tag.writeBody())
        }
        label { AttributeDsl.setAttribute("for", s"tag-name-$suffix"); text(if (creating) i18n"New tag name" else i18n"Tag name") {} }
        val nameControl = input("name") {
          id = s"tag-name-$suffix"
          AttributeDsl.setAttribute("aria-describedby", s"tag-name-errors-$suffix")
        }
        paragraph { id = s"tag-name-errors-$suffix"; classes = "field-error"; text(nameControl.errors.map((values: js.Array[String]) => values.mkString(", "))) {} }
        label { AttributeDsl.setAttribute("for", s"tag-slug-$suffix"); text(if (creating) i18n"New tag slug" else i18n"Tag slug") {} }
        val slugControl = input("slug") {
          id = s"tag-slug-$suffix"
          spellCheck = false
          AttributeDsl.setAttribute("aria-describedby", s"tag-slug-errors-$suffix")
        }
        paragraph { id = s"tag-slug-errors-$suffix"; classes = "field-error"; text(slugControl.errors.map((values: js.Array[String]) => values.mkString(", "))) {} }
        when(actions.error.map(_.nonEmpty)) {
          paragraph {
            role = "alert"
            text(actions.error.flatMap(value => translations.text(value match {
              case Some(400) => i18n"Review the tag fields."
              case Some(409) => i18n"The tag conflicts with saved data. Review the fields or reload."
              case _ => i18n"The save could not be confirmed. Reload to check the tag."
            }))) {}
          }
        }
        button(if (creating) i18n"Create tag" else i18n"Save tag") {
          buttonType("submit")
          disabled = actions.busy.flatMap(busy => actions.blocked.map(blocked => busy || blocked))
        }
        when(actions.blocked) {
          button(i18n"Reload authors and tags") {
            buttonType("button")
            onClick(_ => if (cursor.isBrowser) dom.window.location.reload())
          }
        }
      }
    }
  }
}
