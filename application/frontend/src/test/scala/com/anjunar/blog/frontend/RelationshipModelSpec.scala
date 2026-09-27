package com.anjunar.blog.frontend

import org.scalatest.funsuite.AnyFunSuite
import ui.core.component.{AbstractComponent, Runtime}
import ui.core.dsl.DslLayer.{child, render}
import ui.core.i18n.{I18nConfig, I18nLocale, I18nResolver, I18nRuntime, MessageCatalog}
import ui.core.render.{Cursor, SsrCursor}
import ui.core.state.ListProperty
import ui.forms.{ErrorResponse, Form}
import ui.forms.Form.form
import ui.json.JsonMapper

import scala.concurrent.{ExecutionContext, Promise}
import scala.scalajs.js

class RelationshipModelSpec extends AnyFunSuite {
  private given ExecutionContext = ExecutionContext.parasitic
  private def account(id: String): Account =
    JsonMapper.deserialize[Account](js.JSON.parse(s"""{"id":"$id","version":0,"displayName":"Author $id"}"""))
  private def tag(id: String): BlogTag =
    JsonMapper.deserialize[BlogTag](js.JSON.parse(s"""{"id":"$id","version":0,"name":"Tag $id","slug":"tag-$id"}"""))
  private def post(): BlogPost =
    JsonMapper.deserialize[BlogPost](js.JSON.parse(
      """{"id":"post","version":0,"title":"First title","slug":"first-post","content":"Text",
        |"author":{"id":"one","version":0,"displayName":"Author one"},
        |"tags":[{"id":"scala","version":0,"slug":"scala","name":"Scala"}]}""".stripMargin))

  test("nested data maps into entities and clean relationships are omitted from PATCH") {
    val value = post()
    assert(value.author.get.id.get == "one" && value.tags.head.name.get == "Scala")
    assert(!value.isDirty)
    val body = value.writeBody()
    assert(js.isUndefined(body.author) && js.isUndefined(body.tags))
  }

  test("changed references send only IDs even when their nested metadata is dirty") {
    val value = post()
    val author = account("two")
    author.displayName.set("Must not be written through a post")
    author.email.set("private@example.test")
    val selected = tag("other")
    selected.name.set("Must not edit the shared tag")
    value.author.set(author)
    value.tags.setAll(Seq(selected))
    val body = value.writeBody()
    assert(js.JSON.stringify(body.author) == """{"id":"two"}""")
    assert(js.JSON.stringify(body.tags) == """[{"id":"other"}]""")
  }

  test("clearing relationships preserves explicit null and an empty array") {
    val value = post()
    value.author.set(null)
    value.tags.clear()
    val body = value.writeBody()
    assert(body.author == null && !js.isUndefined(body.author))
    assert(js.JSON.stringify(body.tags) == "[]")
  }

  test("acknowledgements preserve a newer author and tag selection and advance the baseline") {
    val value = post()
    value.author.set(account("two")); value.tags.setAll(Seq(tag("submitted")))
    val submitted = value.snapshot
    value.author.set(account("three")); value.tags.setAll(Seq(tag("newer")))
    val saved = post()
    saved.author.set(account("two")); saved.tags.setAll(Seq(tag("submitted"))); saved.version.set(1L)
    value.mergeSaved(saved, submitted)
    assert(value.author.get.id.get == "three" && value.tags.head.id.get == "newer")
    assert(value.version.get == 1L && value.isDirty)
    assert(value.writeBody().version.asInstanceOf[Int] == 1)
  }

  test("acknowledged selections adopt server metadata and become clean") {
    val value = post()
    value.author.set(account("two")); value.tags.setAll(Seq(tag("other")))
    val saved = post()
    saved.author.set(account("two")); saved.tags.setAll(Seq(tag("other"))); saved.version.set(1L)
    value.mergeSaved(saved, value.snapshot)
    assert(!value.isDirty)
    val body = value.writeBody()
    assert(js.isUndefined(body.author) && js.isUndefined(body.tags))
  }

  test("late relationship errors do not attach to a newer selection") {
    val pending = Promise[BlogPostData]()
    val value = post()
    val actions = new PostEditorActions(new BlogPostData(value,
      Seq(new ApiLink("update", "/service/editorial/posts/post", "PATCH"))), (_, _) => pending.future)
    actions.save(() => ())
    value.author.set(account("two"))
    pending.failure(new HttpFailure(400, Some(new ProblemDetails(status = 400, errors = Seq(
      new FieldError(Seq("author"), "Old author rejected"), new FieldError(Seq("tags"), "Too many tags"))))))
    assert(actions.errors.get.map(_.path) == Seq(Seq("tags")))
    assert(actions.dirty.get && !actions.blocked.get)
    actions.dispose()
  }

  test("the collection control binds ListProperty, model constraints and server errors") {
    val value = post()
    var mountedForm: Form[BlogPost] = null
    var selection: TagSelection = null
    val choices = ListProperty(js.Array(tag("scala"), tag("other")))
    val root = Runtime.mount(new AbstractComponent {
      val tagName = "div"
      override def compose(cursor: Cursor): Unit = {
        I18nRuntime.provide(I18nRuntime.managed(I18nConfig(
          resolver = new I18nResolver(MessageCatalog.empty), supportedLocales = Seq(I18nLocale.En),
          defaultLocale = I18nLocale.En)))(using this)
        render(this, cursor) {
          mountedForm = form(value) { selection = child(new TagSelection(choices)) {} }
        }
      }
    }, new SsrCursor())
    try {
      assert(mountedForm.validateBindings().isEmpty)
      assert(selection.valueProperty.head.id.get == "scala")
      selection.valueProperty.setAll(Seq(tag("other")))
      assert(value.tags.head.id.get == "other")
      value.tags.setAll((1 to 21).map(index => tag(index.toString)))
      assert(mountedForm.validate().nonEmpty)
      value.tags.clear()
      mountedForm.setErrorResponses(Seq(ErrorResponse("Unavailable tag", Seq("tags"))))
      assert(selection.errors.toSeq == Seq("Unavailable tag"))
    } finally Runtime.unmount(root)
  }
}
