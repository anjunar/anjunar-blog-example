package com.anjunar.blog.frontend

import ui.core.state.Property
import ui.forms.validators.{NotBlank, Size}
import ui.json.{JsonId, JsonIgnore, JsonMapper, JsonProperty}

import scala.annotation.meta.field
import scala.scalajs.js

final class BlogPostTranslation {
  @JsonId val id: Property[String] = Property("")
  val version: Property[Long] = Property(-1L)
  @JsonIgnore(deserializable = true) val locale: Property[String] = Property("de")
  @(NotBlank @field)() @(Size @field)(min = 3, max = 180)
  val title: Property[String] = Property("")
  @(Size @field)(max = 300) val summary: Property[String] = Property(null)
  @(Size @field)(max = 100000) val content: Property[String] = Property("")
  @JsonIgnore(deserializable = true) val published: Property[Boolean] = Property(false)

  @JsonIgnore() def fields: Seq[Property[String]] = Seq(title, summary, content)
  @JsonIgnore() def snapshot: Seq[String] = fields.map(_.get)
  @JsonIgnore() def isDirty: Boolean = fields.exists(_.isDirty)

  def writeBody(): js.Dynamic = {
    val body = JsonMapper.serialize(this)
    if (id.get.isEmpty) js.special.delete(body, "id")
    else body.updateDynamic("version")(version.get.toDouble)
    if (!js.isUndefined(body.summary) && body.summary.asInstanceOf[String] == "") body.updateDynamic("summary")(null)
    body
  }

  def mergeSaved(saved: BlogPostTranslation, submitted: Seq[String]): Unit = {
    fields.zip(saved.fields).zip(submitted).foreach { case ((current, fresh), before) =>
      val unchanged = current.get == before
      current.setDefault(fresh.get)
      if (unchanged) current.set(fresh.get)
    }
    id.set(saved.id.get); id.setDefault(saved.id.get)
    version.set(saved.version.get); version.setDefault(saved.version.get)
    published.set(saved.published.get); published.setDefault(saved.published.get)
  }
}

final class TranslationData(var data: BlogPostTranslation = null,
    @(JsonProperty @field)("$links") var links: Seq[ApiLink] = Seq.empty)
