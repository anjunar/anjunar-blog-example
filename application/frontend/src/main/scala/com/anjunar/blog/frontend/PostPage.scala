package com.anjunar.blog.frontend

import ui.core.component.AbstractComponent
import ui.core.dsl.AttributeDsl
import ui.core.dsl.AttributeDsl.*
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.DslLayer.{child, render}
import ui.core.i18n.i18n
import ui.core.layout.Div.div
import ui.core.layout.Condition.when
import ui.core.layout.Image
import ui.core.layout.Heading.heading
import ui.core.layout.Paragraph.paragraph
import ui.core.layout.TextComponent.text
import ui.core.render.Cursor
import ui.core.state.Property
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
        val authorName = post.author.flatMap(account =>
          if (account == null) Property("") else account.displayName)
        val hasName = authorName.map(name => Option(name).exists(value => !value.isBlank))
        when(hasName) { text(authorName) {} }
        when(hasName.map(!_)) { text(i18n"Editorial team") {} }
      }
      div {
        classes = "post-tags"
        foreach(post.tags) { tag => paragraph { classes = "post-tag"; text(tag.name) {} } }
      }
      if (Option(post.summary.get).exists(_.nonEmpty)) {
        paragraph { classes = "detail-summary"; text(post.summary.map(value => Option(value).getOrElse(""))) {} }
      }
      when(post.coverImage.map(_ != null)) {
        Image.image { picture ?=>
          classes = "post-cover"
          Image.src = post.coverImage.map(value => if (value == null) "" else value.source)
          Image.alt = post.coverAlt.map(value => Option(value).getOrElse(""))
          picture.addDisposable(post.coverImage.observe { value =>
            if (value != null) {
              AttributeDsl.setAttribute("width", value.width.get.toString)
              AttributeDsl.setAttribute("height", value.height.get.toString)
            }
          })
        }
      }
      child(new PostContent(post)) {}
    }
}
