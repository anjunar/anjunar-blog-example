package com.anjunar.blog.frontend

import org.scalajs.dom
import ui.core.state.{ListProperty, Property}

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.util.{Failure, Success, Try}

final case class CatalogData(authors: AuthorTable, tags: BlogTagTable)

final class CatalogService(accounts: AccountService)(using ExecutionContext) {
  def load(signal: Option[dom.AbortSignal]): Future[Option[CatalogData]] =
    accounts.session(signal).flatMap { session =>
      (session.links.find(_.rel == "authors"), session.links.find(_.rel == "tags")) match {
        case (Some(authors), Some(tags)) =>
          authorPage(authors, signal).zip(tagPage(tags, signal))
            .map((authors, tags) => Some(CatalogData(authors, tags)))
        case _ => Future.successful(None)
      }
    }

  def authorPage(link: ApiLink, signal: Option[dom.AbortSignal]): Future[AuthorTable] =
    HttpJson.get[AuthorTable](link.path("GET"), signal).map { table =>
      require(table.size >= 0 && table.rows.forall(row => row.data != null), "Invalid author table")
      table
    }

  def tagPage(link: ApiLink, signal: Option[dom.AbortSignal]): Future[BlogTagTable] =
    HttpJson.get[BlogTagTable](link.path("GET"), signal).map { table =>
      require(table.size >= 0 && table.rows.forall(row => row.data != null), "Invalid tag table")
      table
    }

  def saveAuthor(link: ApiLink, body: js.Dynamic): Future[AuthorData] = {
    val path = link.path("PATCH")
    accounts.session().flatMap(state => HttpJson.write[AuthorData](path, "PATCH", body, state.csrfToken))
  }

  def saveTag(link: ApiLink, body: js.Dynamic): Future[BlogTagData] = {
    val method = if (link.rel == "create") "POST" else {
      require(link.rel == "update", "Unexpected tag operation")
      "PATCH"
    }
    val path = link.path(method)
    accounts.session().flatMap(state => HttpJson.write[BlogTagData](path, method, body, state.csrfToken))
  }
}

final class CatalogState(initial: CatalogData, service: CatalogService)(using ExecutionContext) {
  val authors: ListProperty[AuthorData] = ListProperty(js.Array(initial.authors.rows*))
  val tags: ListProperty[BlogTagData] = ListProperty(js.Array(initial.tags.rows*))
  val nextAuthors: Property[Option[ApiLink]] = Property(initial.authors.links.find(_.rel == "next"))
  val nextTags: Property[Option[ApiLink]] = Property(initial.tags.links.find(_.rel == "next"))
  val busy: Property[Boolean] = Property(false)
  val failed: Property[Boolean] = Property(false)
  val saved: Property[Boolean] = Property(false)
  val createTag: Option[ApiLink] = initial.tags.links.find(_.rel == "create")
  private val abort = new dom.AbortController()
  private var disposed = false

  def moreAuthors(): Unit = nextAuthors.get.foreach { link =>
    if (!busy.get) {
      busy.set(true); failed.set(false)
      service.authorPage(link, Some(abort.signal)).onComplete {
        case Success(page) if !disposed =>
          page.rows.filterNot(row => authors.exists(_.data.id.get == row.data.id.get)).foreach(authors += _)
          nextAuthors.set(page.links.find(_.rel == "next")); busy.set(false)
        case Failure(_) if !disposed => failed.set(true); busy.set(false)
        case _ => ()
      }
    }
  }

  def moreTags(): Unit = nextTags.get.foreach { link =>
    if (!busy.get) {
      busy.set(true); failed.set(false)
      service.tagPage(link, Some(abort.signal)).onComplete {
        case Success(page) if !disposed =>
          page.rows.filterNot(row => tags.exists(_.data.id.get == row.data.id.get)).foreach(tags += _)
          nextTags.set(page.links.find(_.rel == "next")); busy.set(false)
        case Failure(_) if !disposed => failed.set(true); busy.set(false)
        case _ => ()
      }
    }
  }

  def recordAuthor(value: AuthorData): Unit = {
    authors.indexWhere(_.data.id.get == value.data.id.get) match {
      case -1 => authors += value
      case index => authors.update(index, value)
    }
    saved.set(true)
  }

  def recordTag(value: BlogTagData): Unit = {
    tags.indexWhere(_.data.id.get == value.data.id.get) match {
      case -1 => tags += value
      case index => tags.update(index, value)
    }
    saved.set(true)
  }

  def dispose(): Unit = { disposed = true; abort.abort() }
}

final class MetadataSave[T](send: js.Dynamic => Future[T], accepted: T => Unit)(using ExecutionContext) {
  val busy: Property[Boolean] = Property(false)
  val blocked: Property[Boolean] = Property(false)
  val error: Property[Option[Int]] = Property(None)
  val fields: Property[Seq[FieldError]] = Property(Seq.empty)
  private var disposed = false

  def apply(body: js.Dynamic): Unit = {
    if (disposed || busy.get || blocked.get) return
    busy.set(true); error.set(None); fields.set(Seq.empty)
    Future.fromTry(Try(send(body))).flatten.onComplete {
      case Success(value) if !disposed => busy.set(false); accepted(value)
      case Failure(failure) if !disposed =>
        val http = failure match { case value: HttpFailure => Some(value); case _ => None }
        val errors = http.flatMap(_.problem).toSeq.flatMap(value => Option(value.errors).getOrElse(Seq.empty))
        fields.set(errors)
        val status = http.map(_.status).getOrElse(0)
        error.set(Some(status))
        blocked.set(status != 400 && !(status == 409 && errors.nonEmpty))
        busy.set(false)
      case _ => ()
    }
  }

  def dispose(): Unit = { disposed = true }
}
