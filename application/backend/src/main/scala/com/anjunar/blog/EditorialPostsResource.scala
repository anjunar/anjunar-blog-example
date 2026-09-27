package com.anjunar.blog

import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.persistence.{EntityManager, LockModeType}
import jakarta.ws.rs.{BadRequestException, DefaultValue, GET, NotFoundException, POST, Path, PathParam, Produces, QueryParam}
import jakarta.ws.rs.core.MediaType

import java.lang
import java.time.Instant
import java.util.UUID
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

@Path("/editorial/posts")
@RolesAllowed(Array("ADMIN"))
@RequestScoped
@Produces(Array(MediaType.APPLICATION_JSON))
class EditorialPostsResource {
  @Inject var manager: EntityManager = uninitialized
  @Inject var access: PostAccess = uninitialized
  @Inject var links: PostLinks = uninitialized

  @GET
  @EntityGraph("BlogPost.list")
  def list(@QueryParam("offset") @DefaultValue("0") rawOffset: String,
      @QueryParam("limit") @DefaultValue("20") rawLimit: String): Table[Data[BlogPost]] = {
    val offset = rawOffset.toIntOption.getOrElse(throw new BadRequestException())
    val limit = rawLimit.toIntOption.getOrElse(throw new BadRequestException())
    if (offset < 0 || limit < 1 || limit > 100) throw new BadRequestException()
    val builder = manager.getCriteriaBuilder
    val query = builder.createQuery(classOf[BlogPost])
    val post = query.from(classOf[BlogPost])
    query.select(post).orderBy(builder.asc(post.get(BlogPost.schema.title)), builder.asc(post.get(BlogPost.schema.id)))
    val values = manager.createQuery(query).setFirstResult(offset).setMaxResults(limit).getResultList.asScala
    val countQuery = builder.createQuery(classOf[lang.Long])
    countQuery.select(builder.count(countQuery.from(classOf[BlogPost])))
    val size = manager.createQuery(countQuery).getSingleResult.longValue()
    val schema = Schema.forGraph(BlogPost.schema, manager.getEntityGraph("BlogPost.list"))
    new Table(values.map(post => new Data(post, schema, links.editorialPost(post))).asJava,
      size, links.page(offset, limit, size))
  }

  @GET @Path("/{id}")
  @EntityGraph("BlogPost.detail")
  def read(@PathParam("id") id: String): Data[BlogPost] = result(load(id, false))

  @POST @Path("/{id}/publish")
  @EntityGraph("BlogPost.detail")
  def publish(@PathParam("id") id: String): Data[BlogPost] = {
    val post = load(id, true)
    access.requireTransition(access.canPublish(post))
    post.publish(Instant.now())
    result(post)
  }

  @POST @Path("/{id}/retract")
  @EntityGraph("BlogPost.detail")
  def retract(@PathParam("id") id: String): Data[BlogPost] = {
    val post = load(id, true)
    access.requireTransition(access.canRetract(post))
    post.retract()
    result(post)
  }

  private def load(rawId: String, lock: Boolean): BlogPost = {
    val id = try UUID.fromString(rawId) catch { case _: IllegalArgumentException => throw new NotFoundException() }
    val post = manager.find(classOf[BlogPost], id,
      if (lock) LockModeType.PESSIMISTIC_WRITE else LockModeType.NONE)
    if (post == null || !access.canRead(post)) throw new NotFoundException()
    post
  }

  private def result(post: BlogPost): Data[BlogPost] =
    new Data(post, Schema.forGraph(BlogPost.schema, manager.getEntityGraph("BlogPost.detail")),
      links.editorialPost(post))
}
