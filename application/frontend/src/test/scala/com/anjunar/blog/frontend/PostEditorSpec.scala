package com.anjunar.blog.frontend

import org.scalatest.funsuite.AnyFunSuite
import ui.json.JsonMapper

import scala.concurrent.{ExecutionContext, Promise}
import scala.scalajs.js

class PostEditorSpec extends AnyFunSuite {
  private given ExecutionContext = ExecutionContext.parasitic
  private val id = "8a1c1582-e841-4a27-a506-1a630337df48"
  private def link(rel: String = "update"): ApiLink =
    new ApiLink(rel, "/service/editorial/posts/" + id, if (rel == "create") "POST" else "PATCH")

  private def post(version: Int = 0): BlogPost =
    JsonMapper.deserialize[BlogPost](js.JSON.parse(
      s"""{"id":"$id","version":$version,"slug":"first-post","title":"First title",
         |"content":"Original content","summary":"Original summary","status":"DRAFT"}""".stripMargin))

  test("mapped partial writes preserve version zero, explicit null and empty content") {
    val value = post()
    value.summary.set("")
    value.content.set("")
    value.status.set("PUBLISHED")
    value.publishedAt.set(Some("2026-09-27T10:00:00Z"))
    val body = value.writeBody()
    assert(body.version.asInstanceOf[Int] == 0)
    assert(body.summary == null)
    assert(body.content.asInstanceOf[String] == "")
    assert(js.isUndefined(body.title) && js.isUndefined(body.slug))
    assert(js.isUndefined(body.status) && js.isUndefined(body.publishedAt))
  }

  test("a new post omits its empty ID and unloaded list data cannot be saved") {
    val value = new BlogPost()
    intercept[IllegalArgumentException](value.writeBody())
    value.title.set("New draft")
    value.slug.set("new-draft")
    value.content.set("")
    val body = value.writeBody()
    assert(js.isUndefined(body.id) && js.isUndefined(body.version))
    assert(body.title.asInstanceOf[String] == "New draft")
  }

  test("merge accepts server values only for unchanged fields and advances every baseline") {
    val value = post()
    value.title.set("Submitted title")
    val sent = value.snapshot
    value.title.set("Still typing")
    val saved = post(1)
    saved.title.set("Submitted title")
    saved.summary.set(null)
    value.mergeSaved(saved, sent)
    assert(value.title.get == "Still typing" && value.summary.get == null)
    assert(value.id.get == id && value.version.get == 1 && value.isDirty)
    val next = value.writeBody()
    assert(next.title.asInstanceOf[String] == "Still typing" && next.version.asInstanceOf[Int] == 1)
    assert(js.isUndefined(next.summary))
  }

  test("pending saves suppress duplicates and retain text typed after submission") {
    val pending = Promise[BlogPostData]()
    var calls = 0
    var body: js.Dynamic = null
    val value = post()
    val actions = new PostEditorActions(new BlogPostData(value, Seq(link())), (_, request) => {
      calls += 1; body = request; pending.future
    })
    value.title.set("Sent title")
    actions.save(() => ())
    value.title.set("Newer title")
    actions.save(() => ())
    assert(calls == 1 && body.title.asInstanceOf[String] == "Sent title" && actions.busy.get)
    val saved = post(1)
    saved.title.set("Sent title")
    pending.success(new BlogPostData(saved, Seq(link())))
    assert(value.title.get == "Newer title" && value.version.get == 1)
    assert(!actions.busy.get && actions.notice.get == SaveNotice.NewerEdits && actions.dirty.get)
    actions.dispose()
  }

  test("late field errors are not attached to values already corrected by the user") {
    val pending = Promise[BlogPostData]()
    val value = post()
    val actions = new PostEditorActions(new BlogPostData(value, Seq(link())), (_, _) => pending.future)
    actions.save(() => ())
    value.title.set("Corrected title")
    val problem = new ProblemDetails(status = 400, errors = Seq(
      new FieldError(Seq("title"), "Old title rejected"),
      new FieldError(Seq("summary"), "Summary rejected")))
    pending.failure(new HttpFailure(400, Some(problem)))
    assert(actions.errors.get.map(_.path) == Seq(Seq("summary")))
    assert(!actions.blocked.get && actions.notice.get == SaveNotice.Invalid)
    actions.dispose()
  }

  test("a malformed null error list cannot strand the form in its saving state") {
    val pending = Promise[BlogPostData]()
    val actions = new PostEditorActions(new BlogPostData(post(), Seq(link())), (_, _) => pending.future)
    actions.save(() => ())
    pending.failure(new HttpFailure(400, Some(new ProblemDetails(status = 400, errors = null))))
    assert(!actions.busy.get && !actions.blocked.get && actions.notice.get == SaveNotice.Invalid)
    actions.dispose()
  }

  test("version conflicts keep edits and prevent a blind retry") {
    val pending = Promise[BlogPostData]()
    val value = post()
    var calls = 0
    val actions = new PostEditorActions(new BlogPostData(value, Seq(link())), (_, _) => {
      calls += 1; pending.future
    })
    value.title.set("Local draft")
    actions.save(() => ())
    pending.failure(new HttpFailure(409))
    actions.save(() => ())
    assert(calls == 1 && actions.blocked.get && actions.notice.get == SaveNotice.Conflict)
    assert(value.title.get == "Local draft" && value.version.get == 0)
    actions.dispose()
  }

  test("disposal prevents a late save response from changing a departed form") {
    val pending = Promise[BlogPostData]()
    val value = post()
    var navigations = 0
    val actions = new PostEditorActions(new BlogPostData(value, Seq(link())), (_, _) => pending.future)
    actions.save(() => navigations += 1)
    actions.dispose()
    pending.success(new BlogPostData(post(1), Seq(link())))
    assert(value.version.get == 0 && navigations == 0)
  }

  test("a created post retains newer text, adopts its ID and uses update for the next save") {
    val first = Promise[BlogPostData]()
    val second = Promise[BlogPostData]()
    var relations = Seq.empty[String]
    val value = new BlogPost()
    value.title.set("First title"); value.slug.set("first-post"); value.content.set("Original content")
    val actions = new PostEditorActions(new BlogPostData(value, Seq(link("create"))), (action, _) => {
      relations :+= action.rel
      if (relations.size == 1) first.future else second.future
    })
    actions.save(() => ())
    value.title.set("Newer creation text")
    first.success(new BlogPostData(post(), Seq(link())))
    assert(value.id.get == id && value.title.get == "Newer creation text" && value.version.get == 0)
    actions.save(() => ())
    assert(relations == Seq("create", "update"))
    actions.dispose()
  }
}
