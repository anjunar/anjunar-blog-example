package com.anjunar.blog.frontend

import ui.core.component.AbstractComponent
import ui.core.dsl.AttributeDsl
import ui.core.dsl.AttributeDsl.*
import ui.core.dsl.ClassDsl.classes
import ui.core.dsl.DslLayer.render
import ui.core.dsl.EventDsl.{on, onClick}
import ui.core.i18n.i18n
import ui.core.layout.Button.{button, buttonType, disabled, disabled_=}
import ui.core.layout.Condition.when
import ui.core.layout.Heading.heading
import ui.core.layout.Label.label
import ui.core.layout.Paragraph.paragraph
import ui.core.layout.TextComponent.text
import ui.core.render.Cursor
import ui.forms.Form.{form, editable, editable_=}
import ui.forms.Input.{input, inputType, inputType_=}
import ui.router.RouterLink.routerLink

import scala.concurrent.ExecutionContext

final class RecoveryPage(endpoint: String, token: Option[String], accounts: AccountService)
    (using ExecutionContext) extends AbstractComponent {
  val tagName = "section"
  private val choosingPassword = endpoint == "confirm" || endpoint == "reset-password"
  private val registering = endpoint == "register" || endpoint == "confirm"
  private val actions = new RecoveryActions(endpoint, token, accounts)

  override def compose(cursor: Cursor): Unit = {
    addDisposable(() => actions.dispose())
    LanguageNavigation.protect(actions.busy, actions.done, actions.fields.email, actions.fields.password) {
      token.nonEmpty || actions.busy.get || (!actions.done.get &&
        (actions.fields.email.get.nonEmpty || actions.fields.password.get.nonEmpty))
    }(using this)
    render(this, cursor) {
      classes = "account-page"
      heading(1) {
        text(if (registering) {
          if (choosingPassword) i18n"Confirm your account" else i18n"Create an account"
        } else {
          if (choosingPassword) i18n"Choose a new password" else i18n"Forgot your password?"
        }) {}
      }
      if (choosingPassword && token.isEmpty) {
        paragraph { role = "alert"; text(i18n"Open the link from your email, or request a new one.") {} }
      } else {
        when(actions.error.map(_ != 0)) {
          paragraph {
            role = "alert"
            classes = "form-error"
            text(actions.error.map(code => code match {
              case 400 if choosingPassword => i18n"The link is invalid or has expired. Request a new one."
              case 400 => i18n"Enter a valid email address."
              case 429 => i18n"Too many attempts. Please wait a minute before trying again."
              case 403 => i18n"Your session changed. Please try again."
              case _ => i18n"The request failed. Please try again."
            })) {}
          }
        }
        when(actions.done.map(!_)) {
          paragraph {
            text(if (choosingPassword) i18n"Use a password with 15 to 128 characters."
              else if (registering) i18n"We will email a link so you can confirm your address and choose a password."
              else i18n"Enter your email address to request a password reset link.") {}
          }
          form(actions.fields) {
            classes = "sign-in-form"
            editable = actions.busy.map(!_)
            on("submit") { event => event.preventDefault(); actions.submit() }
            if (choosingPassword) {
              label {
                text(i18n"New password") {}
                input("password") {
                  inputType = "password"
                  autoComplete = "new-password"
                  AttributeDsl.setAttribute("required", "")
                  AttributeDsl.setAttribute("minlength", "15")
                  AttributeDsl.setAttribute("maxlength", "128")
                }
              }
            } else {
              label {
                text(i18n"Email") {}
                input("email") {
                  inputType = "email"
                  autoComplete = "email"
                  AttributeDsl.setAttribute("required", "")
                  AttributeDsl.setAttribute("maxlength", "254")
                }
              }
            }
            button(if (choosingPassword) i18n"Save password" else i18n"Send email") {
              buttonType("submit")
              disabled = actions.busy
            }
          }
        }
        when(actions.busy) {
          paragraph { role = "status"; text(i18n"Please wait…") {} }
        }
        when(actions.done) {
          paragraph {
            role = "status"
            text(if (choosingPassword) {
              if (registering) i18n"Your account is ready. You can now sign in."
              else i18n"Your password has been changed. Sign in again on your devices."
            } else i18n"If this address is eligible, an email will arrive shortly. Check your spam folder too.") {}
          }
        }
      }
      if (choosingPassword) paragraph {
        routerLink(if (registering) "/register" else "/forgot-password") {
          text(i18n"Request a new link") {}
        }
      }
      else when(actions.done) {
        button(i18n"Request a new link") {
          buttonType("button"); onClick(_ => actions.requestAgain())
        }
      }
      paragraph { routerLink("/account") { text(i18n"Back to sign in") {} } }
    }
  }
}
