package com.anjunar.blog.frontend

import org.scalatest.funsuite.AnyFunSuite
import ui.json.JsonMapper

import scala.concurrent.{ExecutionContext, Promise}
import scala.scalajs.js

class MediaModelSpec extends AnyFunSuite {
  private given ExecutionContext = ExecutionContext.parasitic
  private val first = "896d0270-b13b-4735-bac6-3606a0407cc0"
  private val second = "512d47a1-9e51-4356-a50e-7d05b00f9fcd"
  private def image(id: String): Media =
    JsonMapper.deserialize[Media](js.JSON.parse(
      s"""{"id":"$id","version":0,"name":"Cover","contentType":"image/png","width":4,"height":3,"byteSize":72}"""))
  private def post(): BlogPost = {
    val value = JsonMapper.deserialize[BlogPost](js.JSON.parse(
      """{"id":"post","version":0,"title":"First title","slug":"first-post","content":"Text"}"""))
    value.coverImage.set(image(first)); value.coverImage.setDefault(value.coverImage.get)
    value.coverAlt.set("Original description"); value.coverAlt.setDefault(value.coverAlt.get)
    value
  }

  test("media metadata maps without leaking into ID-only post writes") {
    val value = post()
    assert(value.coverImage.get.source == s"/service/media/$first")
    assert(value.coverImage.get.width.get == 4 && !value.isDirty)
    assert(js.isUndefined(value.writeBody().coverImage))
    val selected = image(second)
    selected.name.set("Must not rename the media")
    value.coverImage.set(selected)
    assert(js.JSON.stringify(value.writeBody().coverImage) == s"""{"id":"$second"}""")
    value.coverImage.set(null)
    assert(value.writeBody().coverImage == null && !js.isUndefined(value.writeBody().coverImage))
  }

  test("image sources are derived from UUIDs and cannot supply arbitrary URLs") {
    for (invalid <- Seq("https://example.test/tracker", "../account", "", "not-a-uuid"))
      intercept[IllegalArgumentException](image(invalid).source)
  }

  test("a delayed save preserves a newer cover and description while advancing the version") {
    val value = post()
    val submitted = value.snapshot
    value.coverImage.set(image(second)); value.coverAlt.set("Newer description")
    val saved = post(); saved.version.set(1L)
    value.mergeSaved(saved, submitted)
    assert(value.coverImage.get.id.get == second && value.coverAlt.get == "Newer description")
    assert(value.version.get == 1L && value.isDirty)
    val next = value.snapshot
    saved.coverImage.set(image(second)); saved.coverAlt.set("Newer description")
    value.mergeSaved(saved, next)
    assert(!value.isDirty && js.isUndefined(value.writeBody().coverImage))
  }

  test("clearing or disposing an upload ignores its late acknowledgement") {
    for (dispose <- Seq(false, true)) {
      val value = post()
      val response = Promise[Media]()
      val actions = new MediaUploadActions(value, (_, _) => response.future)
      actions.upload(null)
      assert(actions.busy.get)
      if (dispose) actions.dispose() else actions.clear()
      response.success(image(second))
      assert(!actions.busy.get)
      assert(if (dispose) value.coverImage.get.id.get == first else value.coverImage.get == null)
      actions.dispose()
    }
  }

  test("upload failure keeps the selected cover and a successful retry replaces it") {
    val value = post()
    var response = Promise[Media]()
    val actions = new MediaUploadActions(value, (_, _) => response.future)
    actions.upload(null)
    response.failure(new HttpFailure(413))
    assert(actions.error.get.contains(413) && !actions.busy.get)
    assert(value.coverImage.get.id.get == first)
    response = Promise[Media]()
    actions.upload(null)
    response.success(image(second))
    assert(actions.error.get.isEmpty && value.coverImage.get.id.get == second)
    actions.dispose()
  }
}
