package com.anjunar.blog.frontend

import org.scalajs.dom
import ui.core.component.AbstractComponent
import ui.core.dsl.AttributeDsl.*
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.DslLayer.{child, render}
import ui.core.dsl.EventDsl.onClick
import ui.core.i18n.{I18nLocale, I18nRuntime, i18n}
import ui.core.layout.Anchor.{anchor, href}
import ui.core.layout.Button.{button, buttonType, disabled, disabled_=}
import ui.core.layout.Condition.when
import ui.core.layout.Div.div
import ui.core.layout.Footer.footer
import ui.core.layout.Header.header
import ui.core.layout.Main.main
import ui.core.layout.Nav.nav
import ui.core.layout.Paragraph.paragraph
import ui.core.layout.Span.span
import ui.core.layout.TextComponent.text
import ui.core.render.Cursor
import ui.router.Router
import ui.router.RouterLink.routerLink
import ui.viewport.Viewport.viewport

import scala.concurrent.ExecutionContext

final class BlogPage(service: BlogService, actions: BlogActions, requestUrl: Option[String] = None,
    publicOrigin: String = "")(using ExecutionContext)
    extends AbstractComponent {
  val tagName = "div"
  private val pages = new BlogRoutes(service, actions)
  private val languageNavigation = new LanguageNavigation()
  private var router: Router = null

  def responseStatus: Int = if (router == null) 503 else router.responseStatus.get

  override def compose(cursor: Cursor): Unit = {
    import ui.core.dsl.AttributeDsl.{setAttribute as attr}
    PageHead.provide(if (cursor.isBrowser) PageHead.browserOrigin else publicOrigin, cursor)(using this)
    val initialUrl = requestUrl.orElse(cursor.browserUrl).getOrElse("/")
    val translations = I18nRuntime.managed(BlogI18n.config, initialUrl)
    I18nRuntime.provide(translations)(using this)
    LanguageNavigation.provide(languageNavigation)(using this)
    if (cursor.isBrowser) {
      val stopListening = AccountLink.listen()
      addDisposable(() => stopListening())
      // The static document root is outside this component's DSL tree.
      addDisposable(translations.locale.observe(locale =>
        dom.document.documentElement.asInstanceOf[dom.HTMLElement].lang = locale.code))
    }
    router = new Router(pages.routes, initialUrl, pages.config)
    Router.provide(router)(using this)

    def changeLanguage(next: I18nLocale): Unit =
      if (!languageNavigation.blocked.get && translations.locale.get != next) {
        val state = router.state.get
        // Account links may already have removed their secret fragment from the browser URL.
        val suffix = if (cursor.isBrowser) dom.window.location.search + dom.window.location.hash
          else state.search + state.hash
        router.navigate(router.localizedPath(state.path, next) + suffix)
      }

    render(this, cursor) {
      classes = "blog"
      anchor() {
        classes = "skip-link"
        href = "#main-content"
        text(i18n"Skip to content") {}
      }
      header {
        classes = "site-header"
        routerLink("/") { link ?=>
          classes = "brand"
          link.addDisposable(translations.locale.observe(locale => href = router.localizedPath("/", locale)))
          text("Anjunar") {}
          span { classes = "brand-note"; text(i18n"Journal") {} }
        }
        div {
          classes = "site-navigation"
          nav {
            ariaLabel = i18n"Main navigation"
            routerLink("/") { link ?=>
              link.addDisposable(translations.locale.observe(locale => href = router.localizedPath("/", locale)))
              text(i18n"Latest posts") {}
            }
            routerLink("/account") { link ?=>
              link.addDisposable(translations.locale.observe(locale => href = router.localizedPath("/account", locale)))
              text(i18n"Account") {}
            }
            anchor() {
              href = "https://github.com/anjunar/anjunar-blog-example"
              text(i18n"Source code") {}
            }
          }
          nav {
            classes = "language-navigation"
            ariaLabel = i18n"Language"
            button("English") {
              buttonType("button")
              lang = "en"
              ariaLabel = i18n"Switch to English"
              ariaPressed = translations.locale.map(_ == BlogI18n.English)
              attr("aria-describedby", "language-switch-help")
              disabled = languageNavigation.blocked.flatMap(blocked =>
                translations.locale.map(locale => blocked || locale == BlogI18n.English))
              onClick(_ => changeLanguage(BlogI18n.English))
            }
            button("Deutsch") {
              buttonType("button")
              lang = "de"
              ariaLabel = i18n"Switch to German"
              ariaPressed = translations.locale.map(_ == BlogI18n.German)
              attr("aria-describedby", "language-switch-help")
              disabled = languageNavigation.blocked.flatMap(blocked =>
                translations.locale.map(locale => blocked || locale == BlogI18n.German))
              onClick(_ => changeLanguage(BlogI18n.German))
            }
          }
          paragraph {
            id = "language-switch-help"
            classes = "language-help"
            when(languageNavigation.blocked) {
              text(i18n"Finish or clear this form before switching language.") {}
            }
          }
        }
      }
      main {
        id = "main-content"
        tabIndex = -1
        viewport { child(router) {} }
      }
      footer {
        classes = "site-footer"
        text(i18n"Anjunar Blog. Built in the open.") {}
        anchor() { href = "#main-content"; text(i18n"Back to top") {} }
      }
    }
  }
}
