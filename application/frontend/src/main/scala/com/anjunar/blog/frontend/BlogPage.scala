package com.anjunar.blog.frontend

import ui.core.component.AbstractComponent
import ui.core.dsl.AttributeDsl.*
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.DslLayer.{child, render}
import ui.core.i18n.{I18nConfig, I18nLocale, I18nResolver, I18nRuntime, MessageCatalog, i18n}
import ui.core.layout.Anchor.{anchor, href}
import ui.core.layout.Footer.footer
import ui.core.layout.Header.header
import ui.core.layout.Main.main
import ui.core.layout.Nav.nav
import ui.core.layout.Span.span
import ui.core.layout.TextComponent.text
import ui.core.render.Cursor
import ui.router.Router
import ui.router.RouterLink.routerLink

import scala.concurrent.ExecutionContext

final class BlogPage(service: BlogService, actions: BlogActions)(using ExecutionContext)
    extends AbstractComponent {
  val tagName = "div"

  private val translations = I18nRuntime.managed(I18nConfig(
    resolver = new I18nResolver(MessageCatalog.empty),
    supportedLocales = Seq(I18nLocale.En),
    defaultLocale = I18nLocale.En
  ))
  private val pages = new BlogRoutes(service, actions)

  override def compose(cursor: Cursor): Unit = {
    I18nRuntime.provide(translations)(using this)
    if (cursor.isBrowser) {
      val stopListening = AccountLink.listen()
      addDisposable(() => stopListening())
    }
    val router = new Router(pages.routes, cursor.browserUrl.getOrElse("/"), pages.config)
    Router.provide(router)(using this)

    render(this, cursor) {
      classes = "blog"
      anchor() {
        classes = "skip-link"
        href = "#main-content"
        text(i18n"Skip to content") {}
      }
      header {
        classes = "site-header"
        routerLink("/") {
          classes = "brand"
          text("Anjunar") {}
          span { classes = "brand-note"; text(i18n"Journal") {} }
        }
        nav {
          ariaLabel = translations.text(i18n"Main navigation")
          routerLink("/") { text(i18n"Latest posts") {} }
          routerLink("/account") { text(i18n"Account") {} }
          anchor() {
            href = "https://github.com/anjunar/anjunar-blog-example"
            text(i18n"Source code") {}
          }
        }
      }
      main {
        id = "main-content"
        tabIndex = -1
        child(router) {}
      }
      footer {
        classes = "site-footer"
        text(i18n"Anjunar Blog. Built in the open.") {}
        anchor() { href = "#main-content"; text(i18n"Back to top") {} }
      }
    }
  }
}
