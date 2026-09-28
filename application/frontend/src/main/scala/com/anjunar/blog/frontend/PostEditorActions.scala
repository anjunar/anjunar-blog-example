package com.anjunar.blog.frontend

import ui.core.state.Property

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.util.{Failure, Success, Try}

enum SaveNotice {
  case Idle, Saved, NewerEdits, Invalid, Conflict, SignedOut, Forbidden, Unconfirmed, BindingFailed

  def isError: Boolean = this match {
    case Idle | Saved | NewerEdits => false
    case _ => true
  }
}

final class PostEditorActions(initial: BlogPostData,
    send: (ApiLink, js.Dynamic) => Future[BlogPostData])(using ExecutionContext) {
  val post: BlogPost = initial.data
  val links: Property[Seq[ApiLink]] = Property(initial.links)
  val busy: Property[Boolean] = Property(false)
  val blocked: Property[Boolean] = Property(false)
  val dirty: Property[Boolean] = Property(post.isDirty)
  import SaveNotice.*

  val notice: Property[SaveNotice] = Property(Idle)
  val errors: Property[Seq[FieldError]] = Property(Seq.empty)
  val generalError: Property[String] = Property("")
  private var disposed = false
  private def changed(): Unit = {
    dirty.set(post.isDirty)
    if (notice.get == Saved || notice.get == NewerEdits) notice.set(Idle)
  }
  private val subscriptions = post.editableFields.map(_.observeWithoutInitial(_ => changed())) ++ Seq(
    post.author.observeWithoutInitial(_ => changed()), post.tags.observeWithoutInitial(_ => changed()),
    post.coverImage.observeWithoutInitial(_ => changed()))

  def save(onSaved: () => Unit): Unit = {
    if (disposed || busy.get || blocked.get) return
    val relation = if (post.id.get.isEmpty) "create" else "update"
    links.get.find(_.rel == relation).foreach { link =>
      val submitted = post.snapshot
      // Serialize now, before session lookup; later keystrokes belong to the next save.
      val body = post.writeBody()
      busy.set(true)
      notice.set(Idle)
      errors.set(Seq.empty)
      generalError.set("")
      Future.fromTry(Try(send(link, body))).flatten.onComplete { result =>
        if (!disposed) {
          result match {
            case Success(saved) =>
              post.mergeSaved(saved.data, submitted)
              links.set(saved.links)
              dirty.set(post.isDirty)
              blocked.set(!saved.links.exists(_.rel == "update"))
              notice.set(if (dirty.get) NewerEdits else Saved)
              busy.set(false)
              onSaved()
            case Failure(error) =>
              val http = error match { case value: HttpFailure => Some(value); case _ => None }
              val fields = http.flatMap(_.problem).toSeq.flatMap(value => Option(value.errors).getOrElse(Seq.empty))
                .filter(value => value != null && value.path != null && value.message != null)
              val applicable = fields.filter(value => value.path.size == 1 &&
                submitted.value(value.path.head).nonEmpty &&
                submitted.value(value.path.head) == post.snapshot.value(value.path.head))
              errors.set(applicable)
              generalError.set(fields.filter(value => value.path.size != 1 ||
                submitted.value(value.path.head).isEmpty).map(_.message).mkString(" "))
              val status = http.map(_.status).getOrElse(0)
              val fieldConflict = status == 409 && fields.exists(_.path == Seq("slug"))
              val retryable = status == 400 || fieldConflict
              blocked.set(!retryable)
              notice.set(if (retryable) Invalid else status match {
                case 409 => Conflict
                case 401 => SignedOut
                case 403 => Forbidden
                case _ => Unconfirmed
              })
              busy.set(false)
          }
        }
      }
    }
  }

  def dispose(): Unit = {
    disposed = true
    subscriptions.foreach(_.dispose())
  }
}
