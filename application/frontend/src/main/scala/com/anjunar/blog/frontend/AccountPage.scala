package com.anjunar.blog.frontend

import ui.core.component.AbstractComponent
import ui.core.dsl.AttributeDsl
import ui.core.dsl.AttributeDsl.*
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.DslLayer.render
import ui.core.dsl.EventDsl.{on, onClick}
import ui.core.i18n.{I18nRuntime, i18n}
import ui.core.layout.Button.{button, buttonType, disabled, disabled_=}
import ui.core.layout.Condition.when
import ui.core.layout.Heading.heading
import ui.core.layout.Label.label
import ui.core.layout.Paragraph.paragraph
import ui.core.layout.TextComponent.text
import ui.core.render.Cursor
import ui.router.RouterLink.routerLink
import ui.forms.Form.{form, editable, editable_=}
import ui.forms.Input.{input, inputType, inputType_=}

import scala.concurrent.ExecutionContext

final class AccountPage(initial: Option[SessionState], service: AccountService, retry: () => Unit)
    (using ExecutionContext) extends AbstractComponent {
  val tagName = "section"
  private val actions = new AccountActions(initial.getOrElse(new SessionState()), service)

  override def compose(cursor: Cursor): Unit = {
    val translations = I18nRuntime.current(using this).get
    addDisposable(() => actions.dispose())
    render(this, cursor) {
      classes = "account-page"
      heading(1) { text(i18n"Your account") {} }
      if (initial.isEmpty) {
        paragraph { role = "alert"; text(i18n"Sign-in is unavailable. Please try again.") {} }
        button(i18n"Try again") { buttonType("button"); onClick(_ => retry()) }
      } else {
        when(actions.error.map(_ != 0)) {
          paragraph {
            classes = "form-error"
            role = "alert"
            text(actions.error.flatMap(code => translations.text(code match {
              case 401 => i18n"Invalid email or password."
              case 429 => i18n"Too many attempts. Please wait a minute before trying again."
              case 403 | 409 => i18n"Your session changed. Reload this page before trying again."
              case _ => i18n"The request failed. Please try again."
            }))) {}
          }
        }
        when(actions.session.map(_.account.isEmpty)) {
          paragraph { text(i18n"Sign in with your email and password.") {} }
          paragraph { routerLink("/register") { text(i18n"Create an account") {} } }
          paragraph { routerLink("/forgot-password") { text(i18n"Forgot your password?") {} } }
          form(actions.credentials) {
            classes = "sign-in-form"
            editable = actions.busy.map(!_)
            on("submit") { event => event.preventDefault(); actions.signIn() }
            label {
              text(i18n"Email") {}
              input("email") {
                inputType = "email"
                autoComplete = "username"
                AttributeDsl.setAttribute("required", "")
                AttributeDsl.setAttribute("maxlength", "254")
              }
            }
            label {
              text(i18n"Password") {}
              input("password") {
                inputType = "password"
                autoComplete = "current-password"
                AttributeDsl.setAttribute("required", "")
                AttributeDsl.setAttribute("maxlength", "128")
              }
            }
            button(i18n"Sign in") {
              buttonType("submit")
              disabled = actions.busy
            }
          }
        }
        when(actions.session.map(_.account.nonEmpty)) {
          paragraph { text(i18n"Signed in as") {} }
          paragraph {
            classes = "account-email"
            text(actions.session.map(_.account.map(_.email.get).getOrElse(""))) {}
          }
          paragraph {
            text(actions.session.flatMap(state => translations.text(
              if (state.account.exists(_.role.get == "ADMIN")) i18n"Administrator" else i18n"Reader"))) {}
          }
          when(actions.session.map(_.links.exists(_.rel == "editorial"))) {
            paragraph { routerLink("/editorial") { text(i18n"Open editorial") {} } }
          }
          button(i18n"Sign out") {
            buttonType("button")
            disabled = actions.busy
            onClick(_ => actions.signOut())
          }
        }
        when(actions.busy) {
          paragraph { role = "status"; text(i18n"Please wait…") {} }
        }
      }
    }
  }
}
