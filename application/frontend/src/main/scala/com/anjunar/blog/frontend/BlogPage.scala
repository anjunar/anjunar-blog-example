package com.anjunar.blog.frontend

import ui.core.component.AbstractComponent
import ui.core.dsl.AttributeDsl.*
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.DslLayer.{child, render}
import ui.core.dsl.EventDsl.onClick
import ui.core.i18n.{I18nConfig, I18nLocale, I18nResolver, I18nRuntime, MessageCatalog, i18n}
import ui.core.layout.Anchor.{anchor, href}
import ui.core.layout.Article
import ui.core.layout.Button.{button, buttonType}
import ui.core.layout.Condition.when
import ui.core.layout.Div.div
import ui.core.layout.Footer.footer
import ui.core.layout.Header.header
import ui.core.layout.Heading.heading
import ui.core.layout.Li.li
import ui.core.layout.Main.main
import ui.core.layout.Nav.nav
import ui.core.layout.Paragraph.paragraph
import ui.core.layout.Section.section
import ui.core.layout.Span.span
import ui.core.layout.TextComponent.text
import ui.core.layout.Ul.ul
import ui.core.render.Cursor
import ui.core.state.{ListProperty, Property}
import ui.core.statement.Foreach.foreach

import scala.scalajs.js

final class BlogPage extends AbstractComponent {
  val tagName = "div"

  private val showSummaries = Property(true)
  private val newestFirst = Property(true)
  private val posts = ListProperty(js.Array[PostPreview]())
  private val translations = I18nRuntime.managed(I18nConfig(
    resolver = new I18nResolver(MessageCatalog.empty),
    supportedLocales = Seq(I18nLocale.En),
    defaultLocale = I18nLocale.En
  ))

  override def compose(cursor: Cursor): Unit = {
    I18nRuntime.provide(translations)(using this)
    addDisposable(newestFirst.observe { newest =>
      val ordered = ExamplePosts.all.sortBy { post =>
        val timestamp = js.Date.parse(post.publishedAt)
        (if (newest) -timestamp else timestamp, post.id)
      }
      posts.setAll(ordered)
    })

    render(this, cursor) {
      classes = "blog"
      anchor() {
        classes = "skip-link"
        href = "#main-content"
        text(i18n"Skip to content") {}
      }
      header {
        classes = "site-header"
        anchor() {
          classes = "brand"
          href = "/"
          text("Anjunar") {}
          span { classes = "brand-note"; text(i18n"Journal") {} }
        }
        nav {
          ariaLabel = translations.text(i18n"Main navigation")
          anchor() { href = "#posts"; text(i18n"Latest posts") {} }
          anchor() {
            href = "https://github.com/anjunar/anjunar-blog-example"
            text(i18n"Source code") {}
          }
        }
      }
      main {
        id = "main-content"
        tabIndex = -1
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
              paragraph { classes = "list-note"; text(i18n"Three notes from the build.") {} }
            }
            div {
              classes = "list-controls"
              button(i18n"Show summaries") {
                buttonType("button")
                ariaPressed = showSummaries
                ariaControls = "post-list"
                onClick(_ => showSummaries.set(!showSummaries.get))
              }
              button(newestFirst.flatMap(newest =>
                translations.text(if (newest) i18n"Newest first" else i18n"Oldest first"))) {
                buttonType("button")
                ariaLabel = newestFirst.flatMap(newest =>
                  translations.text(if (newest) i18n"Sort oldest first" else i18n"Sort newest first"))
                ariaControls = "post-list"
                onClick(_ => newestFirst.set(!newestFirst.get))
              }
            }
          }
          ul {
            id = "post-list"
            classes = "post-list"
            role = "list"
            foreach(posts) { post =>
              li {
                child(new Article) {
                  classes = "post"
                  ariaLabelledBy = s"post-${post.slug}"
                  paragraph {
                    classes = "post-date"
                    text(post.publishedAt.take(10)) {}
                  }
                  div {
                    classes = "post-copy"
                    heading(3) {
                      id = s"post-${post.slug}"
                      text(post.title) {}
                    }
                    when(showSummaries) {
                      paragraph { classes = "post-summary"; text(post.summary) {} }
                    }
                  }
                }
              }
            }
          }
          paragraph {
            classes = "example-note"
            text(i18n"Example posts from the Anjunar Blog Tutorial.") {}
          }
        }
      }
      footer {
        classes = "site-footer"
        text(i18n"Anjunar Blog. Built in the open.") {}
        anchor() { href = "#main-content"; text(i18n"Back to top") {} }
      }
    }
  }
}
