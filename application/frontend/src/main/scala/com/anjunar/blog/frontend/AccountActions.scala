package com.anjunar.blog.frontend

import ui.core.state.Property

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

final class AccountActions(initial: SessionState, service: AccountService)(using ExecutionContext) {
  val credentials = new LoginCredentials()
  val session: Property[SessionState] = Property(initial)
  val busy: Property[Boolean] = Property(false)
  val error: Property[Int] = Property(0)
  private var disposed = false

  def signIn(): Unit =
    run(service.login(credentials.email.get, credentials.password.get))

  def signOut(): Unit = run(service.logout())

  private def run(operation: => Future[SessionState]): Unit =
    if (!busy.get && !disposed) {
      busy.set(true)
      error.set(0)
      val pending = operation
      credentials.password.set("")
      pending.onComplete { result =>
        if (!disposed) {
          result match {
            case Success(state) => session.set(state)
            case Failure(failure: HttpFailure) => error.set(failure.status)
            case Failure(_) => error.set(503)
          }
          busy.set(false)
        }
      }
    }

  def dispose(): Unit = {
    disposed = true
    credentials.password.set("")
  }
}
