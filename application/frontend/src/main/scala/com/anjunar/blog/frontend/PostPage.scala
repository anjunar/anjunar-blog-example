package com.anjunar.blog.frontend

import ui.core.component.AbstractComponent
import ui.core.dsl.AttributeDsl.*
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.DslLayer.render
import ui.core.i18n.{I18nRuntime, i18n}
import ui.core.layout.Div.div
import ui.core.layout.Heading.heading
import ui.core.layout.Paragraph.paragraph
import ui.core.layout.TextComponent.text
import ui.core.render.Cursor
import ui.core.statement.Foreach.foreach
import ui.router.RouterLink.routerLink

final class PostPage(post: BlogPost) extends AbstractComponent {
  val tagName = "article"

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      classes = "post-detail"
      ariaLabelledBy = "post-title"
      routerLink("/") { text(i18n"Back to latest posts") {} }
      paragraph {
        classes = "post-date"
        text(post.publishedAt.map(_.fold("")(_.take(10)))) {}
      }
      heading(1) { id = "post-title"; text(post.title) {} }
      paragraph {
        classes = "post-author"
        val translations = I18nRuntime.current(using this).get
        text(post.author.flatMap(account =>
          if (account == null) translations.text(i18n"Editorial team")
          else account.displayName.flatMap(name =>
            if (Option(name).exists(value => !value.isBlank)) account.displayName
            else translations.text(i18n"Editorial team")))) {}
      }
      div {
        classes = "post-tags"
        foreach(post.tags) { tag => paragraph { classes = "post-tag"; text(tag.name) {} } }
      }
      if (Option(post.summary.get).exists(_.nonEmpty)) {
        paragraph { classes = "detail-summary"; text(post.summary.map(value => Option(value).getOrElse(""))) {} }
      }
      div {
        classes = "post-content"
        // Chapter 19 introduces structured content. Today content is plain text.
        text(post.content.map(value => Option(value).getOrElse(""))) {}
      }
    }
}
