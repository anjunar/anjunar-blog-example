package com.anjunar.blog.frontend

import ui.core.component.AbstractComponent
import ui.core.dsl.AttributeDsl
import ui.core.dsl.AttributeDsl.*
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.DslLayer.render
import ui.core.i18n.{I18nRuntime, i18n}
import ui.core.render.Cursor
import ui.core.state.{ListProperty, ReadOnlyProperty}
import ui.forms.{ComboBox, Control}
import ui.forms.ComboBox.comboBox
import ui.forms.Form.FormContext

import scala.scalajs.js

// ComboBox exposes one value to forms. This collection control binds its multi-selection
// to ListProperty and lets the parent form own collection validation and server errors.
final class TagSelection(choices: ReadOnlyProperty[js.Array[BlogTag]])
    extends AbstractComponent, Control[js.Array[BlogTag]] {
  val tagName = "div"
  val name = "tags"
  val valueProperty: ListProperty[BlogTag] = ListProperty()

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      classes = "tag-selection"
      comboBox[BlogTag]("tag-choices", standalone = true) { choice ?=>
        id = "post-tags"
        ariaLabelledBy = "post-tags-label"
        AttributeDsl.setAttribute("aria-describedby", "post-tags-help post-tags-errors")
        ComboBox.multiSelect = true
        ComboBox.converter = (tag: BlogTag) => tag.name.get
        ComboBox.identityBy = (tag: BlogTag) => tag.id.get
        ComboBox.selectionText = (values: Seq[BlogTag]) => values.map(_.name.get).mkString(", ")
        ComboBox.placeholder = I18nRuntime.current(using this).get.text(i18n"Choose tags").get
        choice.addDisposable(choices.observe { values =>
          ComboBox.items[BlogTag].setAll(values.map(tag =>
            valueProperty.find(_.id.get == tag.id.get).getOrElse(tag)))
        })
        choice.addDisposable(ListProperty.subscribeBidirectional(valueProperty, ComboBox.selection[BlogTag]))
        choice.addDisposable(editableProperty.observe(choice.editableProperty.set))
        choice.addDisposable(invalid.observe(value =>
          AttributeDsl.setAttribute("aria-invalid", value.toString)))
        choice.addDisposable(ComboBox.selection[BlogTag].observeWithoutInitial(_ => setDirty(true)))
      }
      val controller = FormContext.inject.getOrElse(throw new IllegalStateException("Tag selection requires a form"))
      controller.register(this)
      addDisposable(() => controller.unregister(this))
      addDisposable(valueProperty.observe(_ => validate()))
      addDisposable(dirtyProperty.observe(_ => validate()))
    }
}
