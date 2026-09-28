package com.anjunar.blog.frontend

import ui.core.component.AbstractComponent
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.DslLayer.render
import ui.core.layout.Condition.when
import ui.core.layout.Div.div
import ui.core.layout.TextComponent.text
import ui.core.render.Cursor
import ui.editor.Editor

/** One document renderer shared by the public post and the editorial preview. */
final class PostContent(post: BlogPost) extends AbstractComponent {
  val tagName = "div"

  override def compose(cursor: Cursor): Unit = render(this, cursor) {
    classes = "post-content"
    when(post.contentFormat.map(_ == "MARKDOWN")) {
      Editor.editor("content", standalone = true) { document ?=>
        classes = "post-document"
        Editor.editable = false
        Editor.showModeActions = false
        Editor.mediaUrlPolicy = PostMarkdown.mediaPolicy
        document.addDisposable(post.content.observe(value => Editor.value = Option(value).getOrElse("")))
      }
    }
    when(post.contentFormat.map(_ != "MARKDOWN")) {
      div { classes = "post-plain-text"; text(post.content.map(value => Option(value).getOrElse(""))) {} }
    }
  }
}
