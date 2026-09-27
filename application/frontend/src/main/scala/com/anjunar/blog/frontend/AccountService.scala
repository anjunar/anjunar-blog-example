package com.anjunar.blog.frontend

import org.scalajs.dom
import ui.json.JsonMapper

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js

final class AccountService(using ExecutionContext) {
  def session(signal: Option[dom.AbortSignal] = None): Future[SessionState] =
    HttpJson.get[SessionState]("/service/auth/session", signal).map { state =>
      require(state.csrfToken.nonEmpty, "Missing session token")
      state
    }

  def login(email: String, password: String): Future[SessionState] = {
    val command = JsonMapper.serialize(new LoginCommand(email, password))
    session().flatMap(current =>
      HttpJson.post[SessionState]("/service/auth/login", command, current.csrfToken))
  }

  def logout(): Future[SessionState] =
    session().flatMap(current =>
      HttpJson.post[SessionState]("/service/auth/logout", js.Dynamic.literal(), current.csrfToken))
}
