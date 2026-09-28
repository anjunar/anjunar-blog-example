package com.anjunar.blog.frontend

import org.scalajs.dom
import ui.core.state.Property

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

final class MediaUploadActions(post: BlogPost, send: (dom.File, dom.AbortSignal) => Future[Media])
    (using ExecutionContext) {
  val busy: Property[Boolean] = Property(false)
  val error: Property[Option[Int]] = Property(None)
  private var generation = 0
  private var disposed = false
  private var pending: Option[dom.AbortController] = None

  def upload(file: dom.File): Unit = {
    if (disposed || busy.get) return
    generation += 1
    val request = generation
    val controller = new dom.AbortController()
    pending = Some(controller)
    busy.set(true); error.set(None)
    Future.fromTry(Try(send(file, controller.signal))).flatten.onComplete {
      case Success(media) if !disposed && request == generation =>
        post.coverImage.set(media)
        pending = None
        busy.set(false)
      case Failure(failure) if !disposed && request == generation =>
        error.set(Some(failure match { case value: HttpFailure => value.status; case _ => 0 }))
        pending = None
        busy.set(false)
      case _ => ()
    }
  }

  def clear(): Unit = {
    cancel()
    post.coverImage.set(null)
    error.set(None)
  }

  private def cancel(): Unit = {
    generation += 1
    pending.foreach(_.abort())
    pending = None
    busy.set(false)
  }

  def dispose(): Unit = { disposed = true; cancel() }
}
