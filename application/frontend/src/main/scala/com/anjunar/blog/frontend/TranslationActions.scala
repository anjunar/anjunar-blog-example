package com.anjunar.blog.frontend

import ui.core.state.Property

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.util.{Failure, Success, Try}

final class TranslationActions(initial: TranslationData,
    send: (ApiLink, js.Dynamic) => Future[TranslationData])(using ExecutionContext) {
  val translation = initial.data
  val links = Property(initial.links)
  val busy = Property(false)
  val blocked = Property(false)
  val dirty = Property(translation.isDirty)
  val notice = Property(SaveNotice.Idle)
  val errors = Property(Seq.empty[FieldError])
  val generalError = Property("")
  private var disposed = false
  private val subscriptions = translation.fields.map(_.observeWithoutInitial { _ =>
    dirty.set(translation.isDirty)
    if (notice.get == SaveNotice.Saved || notice.get == SaveNotice.NewerEdits) notice.set(SaveNotice.Idle)
  })

  def save(): Unit = run(if (translation.id.get.isEmpty) "create" else "update")

  def run(relation: String): Unit = {
    if (disposed || busy.get || blocked.get) return
    val saving = relation == "create" || relation == "update"
    if (!saving && dirty.get) return
    links.get.find(_.rel == relation).foreach { link =>
      val submitted = translation.snapshot
      val body = if (saving) translation.writeBody()
        else js.Dynamic.literal(version = translation.version.get.toDouble)
      busy.set(true)
      notice.set(SaveNotice.Idle)
      errors.set(Seq.empty)
      generalError.set("")
      Future.fromTry(Try(send(link, body))).flatten.onComplete { result =>
        if (!disposed) {
          result match {
            case Success(saved) =>
              translation.mergeSaved(saved.data, submitted)
              links.set(saved.links)
              dirty.set(translation.isDirty)
              notice.set(if (dirty.get) SaveNotice.NewerEdits else SaveNotice.Saved)
            case Failure(error) =>
              val http = error match { case failure: HttpFailure => Some(failure); case _ => None }
              val fields = http.flatMap(_.problem).toSeq.flatMap(value => Option(value.errors).getOrElse(Seq.empty))
                .filter(value => value != null && value.path != null && value.message != null)
              generalError.set(fields.filter(value => value.path.size != 1 ||
                !Seq("title", "summary", "content").contains(value.path.head)).map(_.message).mkString(" "))
              errors.set(fields.filter { value =>
                val index = if (value != null && value.path != null && value.path.size == 1)
                  Seq("title", "summary", "content").indexOf(value.path.head) else -1
                index >= 0 && submitted(index) == translation.snapshot(index)
              })
              val status = http.map(_.status).getOrElse(0)
              blocked.set(status != 400)
              notice.set(status match {
                case 400 => SaveNotice.Invalid
                case 409 => SaveNotice.Conflict
                case 401 => SaveNotice.SignedOut
                case 403 => SaveNotice.Forbidden
                case _ => SaveNotice.Unconfirmed
              })
          }
          busy.set(false)
        }
      }
    }
  }

  def dispose(): Unit = {
    disposed = true
    subscriptions.foreach(_.dispose())
  }
}
