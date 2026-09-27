package com.anjunar.blog.frontend

import org.scalajs.dom
import ui.core.component.AbstractComponent
import ui.core.dsl.AttributeDsl.*
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.DslLayer.render
import ui.core.i18n.{I18nRuntime, i18n}
import ui.core.layout.Heading.heading
import ui.core.layout.Li.li
import ui.core.layout.Nav.nav
import ui.core.layout.Paragraph.paragraph
import ui.core.layout.TextComponent.text
import ui.core.layout.Ul.ul
import ui.core.render.Cursor
import ui.core.state.ListProperty
import ui.core.statement.Foreach.foreach
import ui.router.RouterLink.routerLink

import scala.scalajs.js

final class EditorialListPage(table: BlogPostTable) extends AbstractComponent {
  val tagName = "section"
  private val rows = ListProperty(js.Array(table.rows*))

  override def compose(cursor: Cursor): Unit =
    render(this, cursor) {
      classes = "editorial-list"
      paragraph { classes = "eyebrow"; text(i18n"Your workspace") {} }
      heading(1) { text(i18n"Editorial") {} }
      paragraph { text(i18n"Preview drafts and manage publication.") {} }
      if (table.links.exists(_.rel == "create")) {
        paragraph { routerLink("/editorial/new") { text(i18n"New post") {} } }
      }
      if (table.rows.isEmpty) paragraph { text(i18n"No posts on this page.") {} }
      ul {
        classes = "editorial-posts"
        foreach(rows) { row =>
          li {
            heading(2) {
              routerLink(s"/editorial/posts/${row.data.id.get}") { text(row.data.title) {} }
            }
            paragraph {
              classes = "publication-status"
              text(if (row.data.status.get == "PUBLISHED") i18n"Published" else i18n"Draft") {}
            }
            paragraph { text(row.data.summary.map(value => Option(value).getOrElse(""))) {} }
          }
        }
      }
      nav {
        classes = "pagination"
        ariaLabel = I18nRuntime.current.get.text(i18n"Editorial pages")
        for (relation <- Seq("previous", "next"); link <- table.links.find(_.rel == relation)) {
          val url = new dom.URL(link.path("GET"), dom.window.location.origin)
          routerLink(s"/editorial${url.search}") {
            text(if (relation == "previous") i18n"Previous page" else i18n"Next page") {}
          }
        }
      }
      routerLink("/account") { text(i18n"Your account") {} }
    }
}
