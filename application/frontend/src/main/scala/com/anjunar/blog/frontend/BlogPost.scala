package com.anjunar.blog.frontend

import ui.core.state.{ListProperty, Property}
import ui.forms.validators.{NotBlank, Pattern, Size}
import ui.json.{JsonId, JsonIgnore, JsonMapper, JsonProperty}

import scala.annotation.meta.field
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

final class BlogPost {
  @JsonId
  val id: Property[String] = Property("")
  val version: Property[Long] = Property(-1L)
  @(NotBlank @field)()
  @(Size @field)(min = 3, max = 220)
  @(Pattern @field)("^[a-z0-9]+(?:-[a-z0-9]+)*$")
  val slug: Property[String] = Property("")
  @(NotBlank @field)()
  @(Size @field)(min = 3, max = 180)
  val title: Property[String] = Property("")
  // Null preserves the distinction between an omitted list field and an empty draft detail.
  @(Size @field)(max = 100000)
  val content: Property[String] = Property(null)
  @(Size @field)(max = 300)
  val summary: Property[String] = Property(null)
  @JsonIgnore(deserializable = true)
  val status: Property[String] = Property("DRAFT")
  @JsonIgnore(deserializable = true)
  val publishedAt: Property[Option[String]] = Property(None)

  val author: Property[Account] = Property(null)
  @(Size @field)(max = 20)
  val tags: ListProperty[BlogTag] = ListProperty()

  @JsonIgnore()
  def editableFields: Seq[Property[String]] = Seq(slug, title, content, summary)

  @JsonIgnore()
  def snapshot: PostSnapshot = PostSnapshot(slug.get, title.get, content.get, summary.get,
    Option(author.get).map(_.id.get), tags.toSeq.map(_.id.get).toSet)

  @JsonIgnore()
  def isDirty: Boolean = editableFields.exists(_.isDirty) || author.isDirty || tags.isDirty

  def writeBody(): js.Dynamic = {
    require(content.get != null, "Load a detail before editing")
    val body = JsonMapper.serialize(this)
    if (id.get.isEmpty) js.special.delete(body, "id")
    // The serializer emits dirty properties. A PATCH precondition is required even when clean.
    if (id.get.nonEmpty) body.updateDynamic("version")(version.get.toDouble)
    if (!js.isUndefined(body.summary) && body.summary.asInstanceOf[String] == "") body.updateDynamic("summary")(null)
    // The mapper owns field transport. Shared relationships use an ID-only input contract.
    def onlyId(reference: js.Dynamic): Unit =
      if (reference != null && !js.isUndefined(reference))
        js.Object.keys(reference.asInstanceOf[js.Object]).filter(_ != "id")
          .foreach(key => js.special.delete(reference, key))
    onlyId(body.author)
    if (!js.isUndefined(body.tags)) body.tags.asInstanceOf[js.Array[js.Dynamic]].foreach(onlyId)
    body
  }

  def mergeSaved(saved: BlogPost, submitted: PostSnapshot): Unit = {
    val before = submitted.values
    editableFields.zip(saved.editableFields).zipWithIndex.foreach { case ((current, fresh), index) =>
      val unchangedSinceSubmit = current.get == before(index)
      current.setDefault(fresh.get)
      if (unchangedSinceSubmit) current.set(fresh.get)
    }
    if (Option(author.get).map(_.id.get) == submitted.authorId) author.set(saved.author.get)
    val sameAuthor = Option(author.get).map(_.id.get) == Option(saved.author.get).map(_.id.get)
    author.setDefault(if (sameAuthor) author.get else saved.author.get)
    if (tags.toSeq.map(_.id.get).toSet == submitted.tagIds) tags.setAll(saved.tags.toSeq)
    // Reuse current objects for matching identities so a fresh response does not invent dirty state.
    val freshIds = saved.tags.toSeq.map(_.id.get).toSet
    val currentIds = tags.toSeq.map(_.id.get).toSet
    val baseline = tags.toSeq.filter(tag => freshIds.contains(tag.id.get)) ++
      saved.tags.toSeq.filterNot(tag => currentIds.contains(tag.id.get))
    tags.setDefaultValue(baseline.toJSArray)
    id.set(saved.id.get)
    id.setDefault(saved.id.get)
    version.set(saved.version.get)
    version.setDefault(saved.version.get)
    status.set(saved.status.get)
    publishedAt.set(saved.publishedAt.get)
  }
}

final case class PostSnapshot(slug: String, title: String, content: String, summary: String,
    authorId: Option[String] = None, tagIds: Set[String] = Set.empty) {
  def values: Seq[String] = Seq(slug, title, content, summary)
  def value(name: String): Option[Any] = name match {
    case "slug" => Some(slug)
    case "title" => Some(title)
    case "content" => Some(content)
    case "summary" => Some(summary)
    case "author" => Some(authorId)
    case "tags" => Some(tagIds)
    case _ => None
  }
}

final class BlogPostData(var data: BlogPost = null,
    @(JsonProperty @field)("$links") var links: Seq[ApiLink] = Seq.empty)

final class BlogPostTable(
    var rows: Seq[BlogPostData] = Seq.empty,
    var size: Long = -1L,
    @(JsonProperty @field)("$links") var links: Seq[ApiLink] = Seq.empty
)
