package com.anjunar.blog.frontend

import ui.core.component.AbstractComponent
import ui.core.dsl.AttributeDsl.*
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.DslLayer.{child, render}
import ui.core.dsl.EventDsl.onClick
import ui.core.i18n.{I18n, I18nRuntime, i18n}
import ui.core.layout.Article
import ui.core.layout.Button.{button, buttonType}
import ui.core.layout.Condition.when
import ui.core.layout.Div.div
import ui.core.layout.Heading.heading
import ui.core.layout.Li.li
import ui.core.layout.Nav.nav
import ui.core.layout.Paragraph.paragraph
import ui.core.layout.Section.section
import ui.core.layout.TextComponent.text
import ui.core.layout.Ul.ul
import ui.core.render.Cursor
import ui.core.state.ListProperty
import ui.core.statement.Foreach.foreach
import ui.router.RouterLink.routerLink

import scala.scalajs.js
import scala.scalajs.js.URIUtils.encodeURIComponent

final class PostListPage(table: BlogPostTable, offset: Int, pageSize: Int, actions: BlogActions)
    extends AbstractComponent {
  val tagName = "div"
  private val posts = ListProperty(js.Array(table.rows.map(_.data)*))

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      section {
        classes = "introduction"
        ariaLabelledBy = "page-title"
        paragraph { classes = "eyebrow"; text(i18n"The Anjunar journal") {} }
        heading(1) {
          id = "page-title"
          text(i18n"From idea to working software.") {}
        }
        paragraph {
          classes = "introduction-copy"
          text(i18n"Notes on Scala, the web, and the decisions in between.") {}
        }
      }
      section {
        id = "posts"
        ariaLabelledBy = "posts-title"
        div {
          classes = "list-heading"
          div {
            heading(2) { id = "posts-title"; text(i18n"Latest posts") {} }
            paragraph {
              classes = "list-note"
              text(i18n"Showing ${I18n.named("count", table.rows.size)} of ${I18n.named("total", table.size)} posts") {}
            }
          }
          if (table.rows.nonEmpty) {
            button(i18n"Show summaries") {
              buttonType("button")
              ariaPressed = actions.showSummaries
              ariaControls = "post-list"
              onClick(_ => actions.toggleSummaries())
            }
          }
        }
        if (table.rows.isEmpty) {
          paragraph {
            classes = "empty-state"
            text(if (table.size == 0) i18n"No posts have been published yet."
              else i18n"There are no posts on this page.") {}
          }
          if (offset > 0) {
            routerLink("/") { text(i18n"Back to latest posts") {} }
          }
        } else {
          ul {
            id = "post-list"
            classes = "post-list"
            role = "list"
            foreach(posts) { post =>
              li {
                child(new Article) {
                  classes = "post"
                  ariaLabelledBy = s"post-${post.id.get}"
                  paragraph {
                    classes = "post-date"
                    text(post.publishedAt.map(_.fold("")(_.take(10)))) {}
                  }
                  div {
                    classes = "post-copy"
                    heading(3) {
                      id = s"post-${post.id.get}"
                      routerLink(s"/posts/${encodeURIComponent(post.slug.get)}") {
                        text(post.title) {}
                      }
                    }
                    when(actions.showSummaries) {
                      if (Option(post.summary.get).exists(_.nonEmpty)) {
                        paragraph {
                          classes = "post-summary"
                          text(post.summary.map(value => Option(value).getOrElse(""))) {}
                        }
                      }
                    }
                  }
                }
              }
            }
          }
          nav {
            classes = "pagination"
            ariaLabel = I18nRuntime.current.get.text(i18n"Post pages")
            if (offset > 0) {
              routerLink(s"/?offset=${math.max(0, offset - pageSize)}") {
                text(i18n"Newer posts") {}
              }
            }
            if (offset.toLong + pageSize < table.size && offset <= Int.MaxValue - pageSize) {
              routerLink(s"/?offset=${offset + pageSize}") {
                text(i18n"Older posts") {}
              }
            }
          }
        }
      }
    }
}
