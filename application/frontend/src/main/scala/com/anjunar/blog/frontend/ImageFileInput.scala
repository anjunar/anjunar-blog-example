package com.anjunar.blog.frontend

import org.scalajs.dom
import ui.core.component.AbstractComponent
import ui.core.dsl.{AttributeDsl, PropertyDsl}
import ui.core.dsl.DslLayer.render
import ui.core.dsl.EventDsl.on
import ui.core.render.Cursor
import ui.core.state.ReadOnlyProperty

// A native file input has browser-owned File objects, not a string form value.
final class ImageFileInput(blocked: ReadOnlyProperty[Boolean], selected: dom.File => Unit)
    extends AbstractComponent {
  val tagName = "input"

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      AttributeDsl.setAttribute("id", "post-cover-file")
      AttributeDsl.setAttribute("type", "file")
      AttributeDsl.setAttribute("accept", "image/jpeg,image/png")
      AttributeDsl.setAttribute("aria-describedby", "post-cover-help post-cover-errors")
      addDisposable(blocked.observe(value => PropertyDsl.setProperty("disabled", value)))
      on("change") { event =>
        if (!blocked.get) event.raw match {
          case raw: dom.Event => raw.target match {
            case input: dom.HTMLInputElement =>
              Option(input.files).filter(_.length > 0).foreach(files => selected(files(0)))
            case _ => ()
          }
          case _ => ()
        }
        PropertyDsl.setProperty("value", "")
      }
    }
}
