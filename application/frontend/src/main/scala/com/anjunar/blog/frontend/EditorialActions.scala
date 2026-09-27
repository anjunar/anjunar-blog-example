package com.anjunar.blog.frontend

import ui.core.state.Property

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

final class EditorialActions(initial: BlogPostData, service: EditorialService)(using ExecutionContext) {
  val current: Property[BlogPostData] = Property(initial)
  val busy: Property[Boolean] = Property(false)
  val error: Property[Int] = Property(0)
  private var disposed = false

  def run(relation: String): Unit =
    if (!busy.get && !disposed) current.get.links.find(_.rel == relation).foreach { link =>
      busy.set(true)
      error.set(0)
      Future.fromTry(Try(service.execute(link))).flatten.onComplete { result =>
        if (!disposed) {
          result match {
            case Success(updated) => current.set(updated)
            case Failure(failure) =>
              // Never keep offering an action after its advertised state proved stale.
              current.set(new BlogPostData(current.get.data, Seq.empty))
              error.set(failure match {
                case http: HttpFailure => http.status
                case _ => 503
              })
          }
          busy.set(false)
        }
      }
    }

  def dispose(): Unit = disposed = true
}
