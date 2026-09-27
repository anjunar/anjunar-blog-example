package com.anjunar.blog

import jakarta.annotation.security.PermitAll

import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.ws.rs.{BadRequestException, DefaultValue, GET, NotFoundException, Path, PathParam, Produces, QueryParam}
import jakarta.ws.rs.core.MediaType

import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

@PermitAll
@Path("/blog/posts")
@Produces(Array(MediaType.APPLICATION_JSON))
@RequestScoped
class BlogPostsResource {
  @Inject var links: PostLinks = uninitialized
  @Inject
  var entityManager: EntityManager = uninitialized

  @GET
  @EntityGraph("BlogPost.list")
  def list(@QueryParam("offset") @DefaultValue("0") rawOffset: String,
      @QueryParam("limit") @DefaultValue("20") rawLimit: String): Table[Data[BlogPost]] = {
    val offset = rawOffset.toIntOption.getOrElse(throw new BadRequestException("offset must be an integer"))
    val limit = rawLimit.toIntOption.getOrElse(throw new BadRequestException("limit must be an integer"))
    if (offset < 0 || limit < 1 || limit > 100)
      throw new BadRequestException("offset must be nonnegative and limit must be between 1 and 100")

    given EntityManager = entityManager
    val schema = Schema.forGraph(BlogPost.schema, entityManager.getEntityGraph("BlogPost.list"))
    val rows = BlogPost.listPublished(offset, limit).asScala
      .map(post => new Data(post, schema, links.publicPost(post))).toList.asJava
    new Table(rows, BlogPost.countPublished())
  }

  @GET
  @Path("/{slug}")
  @EntityGraph("BlogPost.detail")
  def read(@PathParam("slug") slug: String): Data[BlogPost] = {
    given EntityManager = entityManager
    val post = BlogPost.findPublishedBySlug(slug).getOrElse(throw new NotFoundException())
    val schema = Schema.forGraph(BlogPost.schema, entityManager.getEntityGraph("BlogPost.detail"))
    new Data(post, schema, links.publicPost(post))
  }
}
