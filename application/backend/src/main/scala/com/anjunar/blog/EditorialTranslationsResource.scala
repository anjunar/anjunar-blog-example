package com.anjunar.blog

import com.anjunar.json.mapper.PreparedChange
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.persistence.{EntityManager, LockModeType}
import jakarta.ws.rs.{Consumes, GET, NotFoundException, PATCH, POST, Path, PathParam, Produces}
import jakarta.ws.rs.core.MediaType

import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

@Path("/editorial/posts/{postId}/translations")
@RolesAllowed(Array("ADMIN"))
@RequestScoped
@Produces(Array(MediaType.APPLICATION_JSON))
class EditorialTranslationsResource {
  @Inject var manager: EntityManager = uninitialized
  @Inject var media: PostMedia = uninitialized

  @GET @Path("/de") @EntityGraph("BlogPostTranslation.detail")
  def read(@PathParam("postId") post: BlogPost): Data[BlogPostTranslation] = {
    val translation = find(post).getOrElse {
      val value = new BlogPostTranslation()
      value.post = post
      value
    }
    result(translation)
  }

  @POST @Path("/de") @Consumes(Array(MediaType.APPLICATION_JSON))
  @EntityGraph("BlogPostTranslation.detail")
  def create(@PathParam("postId") post: BlogPost, change: PreparedChange[BlogPostTranslation]): Data[BlogPostTranslation] = {
    manager.lock(post, LockModeType.PESSIMISTIC_WRITE)
    if (find(post).nonEmpty) throw new ApiProblem(409, "This translation already exists. Reload before editing.", Problem.conflict)
    change.getEntity().post = post
    val translation = change.applyChanges()
    media.synchronize(translation)
    manager.persist(translation)
    result(translation)
  }

  @PATCH @Path("/{id}") @Consumes(Array(MediaType.APPLICATION_JSON))
  @EntityGraph("BlogPostTranslation.detail")
  def update(@PathParam("postId") post: BlogPost,
      @PathParam("id") change: PreparedChange[BlogPostTranslation]): Data[BlogPostTranslation] = {
    val translation = change.getEntity()
    if (translation.post.id != post.id) throw new NotFoundException()
    change.applyChanges()
    media.synchronize(translation)
    result(translation)
  }

  @POST @Path("/{id}/publish") @Consumes(Array(MediaType.APPLICATION_JSON))
  @EntityGraph("BlogPostTranslation.detail")
  def publish(@PathParam("postId") post: BlogPost,
      @PathParam("id") change: PreparedChange[BlogPostTranslation]): Data[BlogPostTranslation] = {
    val translation = change.getEntity()
    if (translation.post.id != post.id) throw new NotFoundException()
    if (translation.published || !PostDocument.hasContent(translation.content, "MARKDOWN"))
      throw new ApiProblem(409, "Only a saved draft with content can be published.", Problem.conflict)
    translation.published = true
    result(translation)
  }

  @POST @Path("/{id}/retract") @Consumes(Array(MediaType.APPLICATION_JSON))
  @EntityGraph("BlogPostTranslation.detail")
  def retract(@PathParam("postId") post: BlogPost,
      @PathParam("id") change: PreparedChange[BlogPostTranslation]): Data[BlogPostTranslation] = {
    val translation = change.getEntity()
    if (translation.post.id != post.id) throw new NotFoundException()
    if (!translation.published) throw new ApiProblem(409, "This translation is already a draft.", Problem.conflict)
    translation.published = false
    result(translation)
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
    val self = LinkBuilder.create[EditorialTranslationsResource](_.read(value.post)).withRel("self").build()
    val actions = if (value.id == null) {
      Seq(LinkBuilder.create[EditorialTranslationsResource](_.create(value.post, null)).build())
    } else {
      val update = LinkBuilder.create[EditorialTranslationsResource](_.update(value.post, null))
        .withVariable("id", value.id).build()
      val publication = if (value.published) {
        Some(LinkBuilder.create[EditorialTranslationsResource](_.retract(value.post, null))
          .withVariable("id", value.id).build())
      } else if (PostDocument.hasContent(value.content, "MARKDOWN")) {
        Some(LinkBuilder.create[EditorialTranslationsResource](_.publish(value.post, null))
          .withVariable("id", value.id).build())
      } else None
      Seq(update) ++ publication
    }
    val links = (Seq(self) ++ actions).filter(_ != null).asJava
    new Data(value, Schema.forGraph(BlogPostTranslation.schema, manager.getEntityGraph("BlogPostTranslation.detail")), links)
  }
}
