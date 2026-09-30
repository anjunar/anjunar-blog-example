package com.anjunar.blog.frontend

import org.scalatest.funsuite.AnyFunSuite
import ui.json.JsonMapper

import scala.concurrent.{ExecutionContext, Promise}
import scala.scalajs.js

class TranslationSpec extends AnyFunSuite {
  private given ExecutionContext = ExecutionContext.parasitic
  private def model(version: Int = 0): BlogPostTranslation =
    JsonMapper.deserialize[BlogPostTranslation](js.JSON.parse(
      s"""{"id":"translation-id","version":$version,"locale":"de","title":"Deutscher Titel","summary":"Kurzfassung","content":"Inhalt","published":false}"""))
  private def link(rel: String = "update"): ApiLink =
    new ApiLink(rel, "/service/editorial/posts/post-id/translations/translation-id",
      if (rel == "update") "PATCH" else "POST")

  test("partial translation writes retain version zero and exclude publication and locale") {
    val value = model()
    value.summary.set("")
    value.published.set(true)
    value.locale.set("en")
    val body = value.writeBody()
    assert(body.version.asInstanceOf[Int] == 0 && body.summary == null)
    assert(js.isUndefined(body.published) && js.isUndefined(body.locale) && js.isUndefined(body.title))
  }

  test("public detail maps the selected entity without overwriting its English source") {
    val value = JsonMapper.deserialize[BlogPost](js.JSON.parse(
      """{"id":"post-id","version":0,"title":"English","content":"Source","contentLocale":"de","availableLocales":["en","de"],
        |"translation":{"id":"translation-id","version":0,"title":"Deutsch","content":"Übersetzt","published":true}}""".stripMargin))
    assert(value.title.get == "English" && value.translation.get.title.get == "Deutsch")
    assert(value.translation.get.summary.get == null && value.availableLocales.get.toSeq == Seq("en", "de"))
    value.title.set("Changed source")
    val body = value.writeBody()
    assert(js.isUndefined(body.translation) && js.isUndefined(body.contentLocale) && js.isUndefined(body.availableLocales))
  }

  test("new creation sends changed values without an ID and adopts server version and links") {
    val pending = Promise[TranslationData]()
    val value = new BlogPostTranslation()
    value.title.set("Neuer Entwurf")
    value.content.set("Inhalt")
    var body: js.Dynamic = null
    val actions = new TranslationActions(new TranslationData(value, Seq(link("create"))), (_, request) => {
      body = request; pending.future
    })
    actions.save()
    assert(js.isUndefined(body.id) && body.title.asInstanceOf[String] == "Neuer Entwurf")
    pending.success(new TranslationData(model(), Seq(link(), link("publish"))))
    assert(value.id.get == "translation-id" && value.version.get == 0 && !actions.dirty.get)
    assert(actions.links.get.exists(_.rel == "publish"))
    actions.dispose()
  }

  test("saving snapshots input, prevents duplicates and preserves newer typing") {
    val pending = Promise[TranslationData]()
    val value = model()
    var calls = 0
    var body: js.Dynamic = null
    val actions = new TranslationActions(new TranslationData(value, Seq(link())), (_, request) => {
      calls += 1; body = request; pending.future
    })
    value.title.set("Gesendeter Titel")
    actions.save()
    value.title.set("Neuerer Titel")
    actions.save()
    assert(calls == 1 && body.title.asInstanceOf[String] == "Gesendeter Titel")
    val saved = model(1); saved.title.set("Gesendeter Titel")
    pending.success(new TranslationData(saved, Seq(link())))
    assert(value.title.get == "Neuerer Titel" && value.version.get == 1)
    assert(actions.notice.get == SaveNotice.NewerEdits && actions.dirty.get)
    actions.dispose()
  }

  test("publication waits for saved input and sends only the version") {
    val pending = Promise[TranslationData]()
    val value = model()
    var body: js.Dynamic = null
    val actions = new TranslationActions(new TranslationData(value, Seq(link("publish"))), (_, request) => {
      body = request; pending.future
    })
    value.title.set("Noch nicht gespeichert")
    actions.run("publish")
    assert(body == null)
    value.title.setDefault(value.title.get)
    actions.dirty.set(false)
    actions.run("publish")
    assert(js.Object.keys(body.asInstanceOf[js.Object]).toSeq == Seq("version") && body.version.asInstanceOf[Int] == 0)
    val saved = model(1); saved.published.set(true)
    pending.success(new TranslationData(saved, Seq(link("retract"))))
    assert(value.published.get && actions.links.get.head.rel == "retract")
    actions.dispose()
  }

  test("late field errors skip corrected fields and conflicts preserve edits while blocking retries") {
    val pending = Promise[TranslationData]()
    val value = model()
    val actions = new TranslationActions(new TranslationData(value, Seq(link())), (_, _) => pending.future)
    actions.save()
    value.title.set("Schon korrigiert")
    pending.failure(new HttpFailure(400, Some(new ProblemDetails(status = 400, errors = Seq(
      new FieldError(Seq("title"), "Invalid"), new FieldError(Seq("summary"), "Invalid summary"))))))
    assert(actions.errors.get.map(_.path) == Seq(Seq("summary")) && !actions.blocked.get)
    actions.dispose()
    val conflict = Promise[TranslationData]()
    var calls = 0
    val blocked = new TranslationActions(new TranslationData(value, Seq(link())), (_, _) => {
      calls += 1; conflict.future
    })
    blocked.save()
    conflict.failure(new HttpFailure(409))
    blocked.save()
    assert(calls == 1 && blocked.blocked.get && value.title.get == "Schon korrigiert")
    blocked.dispose()
  }

  test("disposal ignores a late response") {
    val pending = Promise[TranslationData]()
    val value = model()
    val actions = new TranslationActions(new TranslationData(value, Seq(link())), (_, _) => pending.future)
    actions.save()
    actions.dispose()
    pending.success(new TranslationData(model(1), Seq.empty))
    assert(value.version.get == 0)
  }
}
