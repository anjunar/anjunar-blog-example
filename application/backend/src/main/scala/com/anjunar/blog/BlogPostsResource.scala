package com.anjunar.blog

import jakarta.annotation.security.PermitAll

import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.ws.rs.{BeanParam, GET, NotFoundException, Path, PathParam, Produces}
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
  def list(@BeanParam parameters: PostSearchParams): Table[Data[BlogPostSummary]] = {
    given EntityManager = entityManager
    val search = parameters.search(editorial = false)
    val query = new BlogPostSearch(search)
    val schema = Schema.forGraph(BlogPost.schema, entityManager.getEntityGraph("BlogPost.list"))
    val rows = query.rows().asScala
      .map(post => new Data(post, schema, links.summary(post, editorial = false))).toList.asJava
    val total = query.count()
    new Table(rows, total, links.page(search, total, editorial = false))
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
