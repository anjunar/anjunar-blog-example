package com.anjunar.blog

import com.anjunar.blog.hibernate.search.HibernateSearch

import jakarta.annotation.security.PermitAll

import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.ws.rs.{BeanParam, GET, NotFoundException, Path, PathParam, Produces, QueryParam}
import jakarta.ws.rs.core.MediaType

import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

@PermitAll
@Path("/blog/posts")
@Produces(Array(MediaType.APPLICATION_JSON))
@RequestScoped
class BlogPostsResource {
  @Inject var localization: PostLocalization = uninitialized
  @Inject var links: PostLinks = uninitialized
  @Inject var queries: HibernateSearch = uninitialized
  @Inject
  var entityManager: EntityManager = uninitialized

  @GET
  @EntityGraph("BlogPost.list")
  def list(@BeanParam parameters: PostSearchParams): Table[Data[BlogPostSummary]] = {
    given EntityManager = entityManager
    val search = parameters.search(editorial = false)
    val context = queries.searchContext(search)
    val schema = Schema.forGraph(BlogPost.schema, entityManager.getEntityGraph("BlogPost.list"), Set("contentLocale"))
    val rows = queries.entities(search.index, search.limit, classOf[BlogPost],
      classOf[BlogPostSummary], context, (query, root, selection, builder) =>
        BlogPostSummary.selectLocalized(query, root, selection, builder, search.locale)).asScala
      .map(post => new Data(post, schema, links.summary(post, editorial = false, locale = search.locale))).toList.asJava
    val total = queries.count(classOf[BlogPost], context)
    new Table(rows, total, links.page(search, total, editorial = false))
  }

  @GET
  @Path("/{slug}")
  @EntityGraph("BlogPost.detail")
  def read(@PathParam("slug") slug: String, @QueryParam("locale") requestedLocale: String): Data[BlogPost] = {
    given EntityManager = entityManager
    val locale = PostLocale.parse(requestedLocale)
    val post = BlogPost.findPublishedBySlug(slug).getOrElse(throw new NotFoundException())
    localization.select(post, locale)
    val schema = Schema.forGraph(BlogPost.schema, entityManager.getEntityGraph("BlogPost.detail"),
      Set("translation", "contentLocale", "availableLocales"))
    new Data(post, schema, links.publicPost(post, locale))
  }
}
