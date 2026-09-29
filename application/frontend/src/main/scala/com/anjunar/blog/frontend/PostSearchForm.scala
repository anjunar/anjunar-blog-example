package com.anjunar.blog.frontend

import ui.core.component.AbstractComponent
import ui.core.dsl.AttributeDsl
import ui.core.dsl.AttributeDsl.*
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.DslLayer.render
import ui.core.dsl.EventDsl.on
import ui.core.i18n.i18n
import ui.core.layout.Button.{button, buttonType}
import ui.core.layout.Condition.when
import ui.core.layout.Div.div
import ui.core.layout.Label.label
import ui.core.layout.Paragraph.paragraph
import ui.core.layout.TextComponent.text
import ui.core.render.Cursor
import ui.core.state.Property
import ui.forms.Form.form
import ui.forms.Input.{input, inputType, inputType_=}
import ui.forms.SelectInput.selectInput
import ui.forms.SelectOption
import ui.forms.validators.Size
import ui.router.Router
import ui.router.RouterLink.routerLink

import scala.annotation.meta.field
import scala.scalajs.js

// Search controls are route state, separate from the editable post model.
final class PostSearchFields(search: PostSearch) {
  @(Size @field)(max = 100)
  val query: Property[String] = Property(search.query)
  val status: Property[String] = Property(search.status)
  val sort: Property[String] = Property(search.sort)
  val limit: Property[String] = Property(search.limit.toString)

  def submitted(editorial: Boolean): PostSearch = {
    val values = Map("q" -> query.get, "status" -> status.get, "sort" -> sort.get, "limit" -> limit.get)
    // A new search always starts at offset zero.
    PostSearch.parse(values.get, editorial)
  }
}

final class PostSearchForm(search: PostSearch) extends AbstractComponent {
  val tagName = "div"
  private val fields = new PostSearchFields(search)
  private val invalid = Property(false)

  override def compose(cursor: Cursor): Unit = {
    LanguageNavigation.protect(fields.query, fields.status, fields.sort, fields.limit) {
      fields.query.isDirty || fields.status.isDirty || fields.sort.isDirty || fields.limit.isDirty
    }(using this)
    render(this, cursor) {
      classes = "post-search"
      form(fields) { mountedForm ?=>
        classes = "search-form"
        role = "search"
        AttributeDsl.setAttribute("novalidate", "")
        on("submit") { event =>
          event.preventDefault()
          invalid.set(false)
          val bindings = mountedForm.validateBindings()
          val errors = mountedForm.validate()
          if (bindings.nonEmpty || errors.nonEmpty) invalid.set(true)
          else {
            try Router.navigate(fields.submitted(search.editorial).url)
            catch { case _: HttpFailure => invalid.set(true) }
          }
        }
        div {
          classes = "search-query"
          label {
            AttributeDsl.setAttribute("for", "post-query")
            text(i18n"Search posts") {}
          }
          val control = input("query") { fieldInput ?=>
            id = "post-query"
            inputType = "search"
            AttributeDsl.setAttribute("maxlength", "100")
            AttributeDsl.setAttribute("aria-describedby", "search-help search-errors")
            fieldInput.addDisposable(fieldInput.invalid.observe(value =>
              AttributeDsl.setAttribute("aria-invalid", value.toString)))
          }
          paragraph {
            id = "search-help"
            classes = "field-help"
            text(i18n"Search title, slug or summary.") {}
          }
          paragraph {
            id = "search-errors"
            classes = "field-error"
            text(control.errors.map((values: js.Array[String]) => values.mkString(", "))) {}
          }
        }
        if (search.editorial) {
          div {
            label {
              AttributeDsl.setAttribute("for", "post-status")
              text(i18n"Publication status") {}
            }
            selectInput("status", Seq(
              SelectOption("", i18n"All statuses"),
              SelectOption("DRAFT", i18n"Draft"),
              SelectOption("PUBLISHED", i18n"Published")
            )) { id = "post-status" }
          }
        }
        div {
          label {
            AttributeDsl.setAttribute("for", "post-sort")
            text(i18n"Sort by") {}
          }
          selectInput("sort", Seq(
            SelectOption("newest", i18n"Newest first"),
            SelectOption("oldest", i18n"Oldest first"),
            SelectOption("title", i18n"Title A–Z"),
            SelectOption("title-desc", i18n"Title Z–A")
          )) { id = "post-sort" }
        }
        div {
          label {
            AttributeDsl.setAttribute("for", "post-limit")
            text(i18n"Posts per page") {}
          }
          selectInput("limit", (Seq(10, 20, 50, 100) :+ search.limit).distinct.sorted.map(value =>
            SelectOption(value.toString, Property(value.toString)))) { id = "post-limit" }
        }
        div {
          classes = "search-buttons"
          button(i18n"Search") { buttonType("submit") }
          routerLink(search.path) { text(i18n"Reset search") {} }
        }
        when(invalid) {
          paragraph { role = "alert"; text(i18n"Check the search fields and try again.") {} }
        }
      }
    }
  }
}
