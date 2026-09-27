package com.anjunar.blog

import com.anjunar.blog.hibernate.search.HibernateSearch

import com.anjunar.json.mapper.{ErrorRequest, PreparedChange}
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.persistence.{EntityManager, FlushModeType, LockModeType}
import jakarta.ws.rs.{BeanParam, Consumes, ForbiddenException, GET, NotFoundException, PATCH, POST, Path, PathParam, Produces}
import jakarta.ws.rs.core.{Context, GenericEntity, MediaType, Response, UriInfo}

import java.lang
import java.time.Instant
import java.util
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
  @Inject var queries: HibernateSearch = uninitialized
  @Context var uriInfo: UriInfo = uninitialized

  @GET
  @EntityGraph("BlogPost.list")
  def list(@BeanParam parameters: PostSearchParams): Table[Data[BlogPostSummary]] = {
    given EntityManager = manager
    val search = parameters.search(editorial = true)
    val context = queries.searchContext(search)
    val schema = Schema.forGraph(BlogPost.schema, manager.getEntityGraph("BlogPost.list"))
    val rows = queries.entities(search.index, search.limit, classOf[BlogPost],
      classOf[BlogPostSummary], context, BlogPostSummary.select).asScala
      .map(post => new Data(post, schema, links.summary(post, editorial = true))).asJava
    val total = queries.count(classOf[BlogPost], context)
    new Table(rows, total, links.page(search, total, editorial = true))
  }

  @POST
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @EntityGraph("BlogPost.detail")
  def create(change: PreparedChange[BlogPost]): Response = {
    if (!access.canEdit(change.getEntity())) throw new ForbiddenException()
    val post = change.applyChanges()
    requireFreeSlug(post)
    manager.persist(post)
    manager.flush()
    val body = new GenericEntity[Data[BlogPost]](result(post)) {}
    Response.created(uriInfo.getBaseUriBuilder.path("editorial/posts").path(post.id.toString).build()).entity(body).build()
  }

  @PATCH @Path("/{id}")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @EntityGraph("BlogPost.detail")
  def update(@PathParam("id") change: PreparedChange[BlogPost]): Data[BlogPost] = {
    if (!access.canEdit(change.getEntity())) throw new ForbiddenException()
    val post = change.applyChanges()
    requireFreeSlug(post)
    manager.flush()
    result(post)
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

  private def requireFreeSlug(post: BlogPost): Unit = {
    val builder = manager.getCriteriaBuilder
    val query = builder.createQuery(classOf[lang.Long])
    val other = query.from(classOf[BlogPost])
    val sameSlug = builder.equal(other.get(BlogPost.schema.slug), post.slug)
    val predicates = if (post.id == null) Seq(sameSlug)
      else Seq(sameSlug, builder.notEqual(other.get(BlogPost.schema.id), post.id))
    query.select(builder.count(other)).where(predicates*)
    if (manager.createQuery(query).setFlushMode(FlushModeType.COMMIT).getSingleResult.longValue() > 0)
      throw new ApiProblem(409, "This slug is already used by another post.", Problem.conflict,
        Seq(new ErrorRequest(util.List.of[Any]("slug"), "Choose an unused slug.")))
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
