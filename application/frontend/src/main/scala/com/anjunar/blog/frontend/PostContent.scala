package com.anjunar.blog.frontend

import ui.core.component.AbstractComponent
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.DslLayer.render
import ui.core.layout.Condition.when
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.render.Cursor
import ui.core.state.ReadOnlyProperty
import ui.editor.Editor.*

/** One document renderer shared by the public post and the editorial preview. */
final class PostContent(content: ReadOnlyProperty[String], format: ReadOnlyProperty[String]) extends AbstractComponent {
  def this(post: BlogPost) = this(post.content, post.contentFormat)
  val tagName = "div"

  override def compose(cursor: Cursor): Unit = render(this, cursor) {
    classes = "post-content"
    when(format.map(_ == "MARKDOWN")) {
      editor("content", standalone = true) { document ?=>
        classes = "post-document"
        editable = false
        showModeActions = false
        mediaUrlPolicy = PostMarkdown.mediaPolicy
        document.addDisposable(content.observe(next => value = Option(next).getOrElse("")))
      }
    }
    when(format.map(_ != "MARKDOWN")) {
      div { classes = "post-plain-text"; text(content.map(value => Option(value).getOrElse(""))) {} }
    }
  }
}
