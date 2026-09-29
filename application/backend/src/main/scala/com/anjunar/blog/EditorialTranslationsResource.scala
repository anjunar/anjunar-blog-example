package com.anjunar.blog

import com.anjunar.json.mapper.PreparedChange
import com.anjunar.json.mapper.schema.Link
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.persistence.{EntityManager, LockModeType}
import jakarta.ws.rs.{Consumes, GET, NotFoundException, PATCH, POST, Path, PathParam, Produces}
import jakarta.ws.rs.core.MediaType

import java.util
import java.util.UUID
import scala.compiletime.uninitialized

@Path("/editorial/posts/{postId}/translations")
@RolesAllowed(Array("ADMIN"))
@RequestScoped
@Produces(Array(MediaType.APPLICATION_JSON))
class EditorialTranslationsResource {
  @Inject var manager: EntityManager = uninitialized
  @Inject var media: PostMedia = uninitialized

  @GET @Path("/de") @EntityGraph("BlogPostTranslation.detail")
  def read(@PathParam("postId") postId: String): Data[BlogPostTranslation] = {
    val post = parent(postId, lock = false)
    val translation = find(post).getOrElse {
      val value = new BlogPostTranslation()
      value.post = post
      value
    }
    result(translation)
  }

  @POST @Path("/de") @Consumes(Array(MediaType.APPLICATION_JSON))
  @EntityGraph("BlogPostTranslation.detail")
  def create(@PathParam("postId") postId: String, change: PreparedChange[BlogPostTranslation]): Data[BlogPostTranslation] = {
    val post = parent(postId, lock = true)
    if (find(post).nonEmpty) throw new ApiProblem(409, "This translation already exists. Reload before editing.", Problem.conflict)
    change.getEntity().post = post
    val translation = change.applyChanges()
    media.synchronize(translation)
    manager.persist(translation)
    result(translation)
  }

  @PATCH @Path("/{id}") @Consumes(Array(MediaType.APPLICATION_JSON))
  @EntityGraph("BlogPostTranslation.detail")
  def update(@PathParam("postId") postId: UUID,
      @PathParam("id") change: PreparedChange[BlogPostTranslation]): Data[BlogPostTranslation] = {
    val translation = change.getEntity()
    if (translation.post.id != postId) throw new NotFoundException()
    change.applyChanges()
    media.synchronize(translation)
    result(translation)
  }

  @POST @Path("/{id}/publish") @Consumes(Array(MediaType.APPLICATION_JSON))
  @EntityGraph("BlogPostTranslation.detail")
  def publish(@PathParam("postId") postId: UUID,
      @PathParam("id") change: PreparedChange[BlogPostTranslation]): Data[BlogPostTranslation] = {
    val translation = change.getEntity()
    if (translation.post.id != postId) throw new NotFoundException()
    if (translation.published || !PostDocument.hasContent(translation.content, "MARKDOWN"))
      throw new ApiProblem(409, "Only a saved draft with content can be published.", Problem.conflict)
    translation.published = true
    result(translation)
  }

  @POST @Path("/{id}/retract") @Consumes(Array(MediaType.APPLICATION_JSON))
  @EntityGraph("BlogPostTranslation.detail")
  def retract(@PathParam("postId") postId: UUID,
      @PathParam("id") change: PreparedChange[BlogPostTranslation]): Data[BlogPostTranslation] = {
    val translation = change.getEntity()
    if (translation.post.id != postId) throw new NotFoundException()
    if (!translation.published) throw new ApiProblem(409, "This translation is already a draft.", Problem.conflict)
    translation.published = false
    result(translation)
  }

  private def parent(raw: String, lock: Boolean): BlogPost = {
    val id = try UUID.fromString(raw) catch { case _: IllegalArgumentException => throw new NotFoundException() }
    Option(manager.find(classOf[BlogPost], id, if (lock) LockModeType.PESSIMISTIC_WRITE else LockModeType.NONE))
      .getOrElse(throw new NotFoundException())
  }

  private def find(post: BlogPost): Option[BlogPostTranslation] = {
    val builder = manager.getCriteriaBuilder
    val query = builder.createQuery(classOf[BlogPostTranslation])
    val translation = query.from(classOf[BlogPostTranslation])
    query.select(translation).where(
      builder.equal(translation.get(BlogPostTranslation.schema.post), post),
      builder.equal(translation.get(BlogPostTranslation.schema.locale), "de"))
    Option(manager.createQuery(query).getSingleResultOrNull)
  }

  private def result(value: BlogPostTranslation): Data[BlogPostTranslation] = {
    val base = s"/service/editorial/posts/${value.post.id}/translations"
    val links = new util.ArrayList[Link]()
    links.add(new Link("self", s"$base/de", "GET", "BlogPostTranslation"))
    if (value.id == null) links.add(new Link("create", s"$base/de", "POST", "BlogPostTranslation"))
    else {
      val path = s"$base/${value.id}"
      links.add(new Link("update", path, "PATCH", "BlogPostTranslation"))
      if (value.published) links.add(new Link("retract", s"$path/retract", "POST", "BlogPostTranslation"))
      else if (PostDocument.hasContent(value.content, "MARKDOWN"))
        links.add(new Link("publish", s"$path/publish", "POST", "BlogPostTranslation"))
    }
    new Data(value, Schema.forGraph(BlogPostTranslation.schema, manager.getEntityGraph("BlogPostTranslation.detail")), links)
  }
}
