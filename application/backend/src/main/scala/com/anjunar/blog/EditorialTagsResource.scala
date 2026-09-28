package com.anjunar.blog

import com.anjunar.blog.hibernate.search.HibernateSearch
import com.anjunar.json.mapper.{ErrorRequest, PreparedChange}
import com.anjunar.json.mapper.schema.Link
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.persistence.{EntityManager, FlushModeType}
import jakarta.ws.rs.{BeanParam, Consumes, ForbiddenException, GET, PATCH, POST, Path, PathParam, Produces}
import jakarta.ws.rs.core.{GenericEntity, MediaType, Response}

import java.lang
import java.util
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

@Path("/editorial/tags")
@RolesAllowed(Array("ADMIN"))
@RequestScoped
@Produces(Array(MediaType.APPLICATION_JSON))
class EditorialTagsResource {
  @Inject var manager: EntityManager = uninitialized
  @Inject var queries: HibernateSearch = uninitialized
  @Inject var caller: CallerAccess = uninitialized

  @GET @EntityGraph("BlogTag.detail")
  def list(@BeanParam parameters: CatalogParams): Table[Data[BlogTag]] = {
    val context = queries.searchContext(TagSearch(index = parameters.start, limit = parameters.size))
    val rows = queries.entities(parameters.start, parameters.size, classOf[BlogTag], classOf[BlogTag],
      context, (query, root, _, _) => query.select(root)).asScala.map(result).asJava
    val total = queries.count(classOf[BlogTag], context)
    new Table(rows, total, parameters.links("/service/editorial/tags", total, create = true))
  }

  @POST @Consumes(Array(MediaType.APPLICATION_JSON)) @EntityGraph("BlogTag.detail")
  def create(change: PreparedChange[BlogTag]): Response = {
    if (!caller.administrator) throw new ForbiddenException()
    val tag = change.applyChanges()
    requireFreeSlug(tag)
    manager.persist(tag)
    manager.flush()
    val body = new GenericEntity[Data[BlogTag]](result(tag)) {}
    Response.status(201).entity(body).build()
  }

  @PATCH @Path("/{id}") @Consumes(Array(MediaType.APPLICATION_JSON))
  @EntityGraph("BlogTag.detail")
  def update(@PathParam("id") change: PreparedChange[BlogTag]): Data[BlogTag] = {
    if (!caller.administrator) throw new ForbiddenException()
    val tag = change.applyChanges()
    requireFreeSlug(tag)
    manager.flush()
    result(tag)
  }

  private def requireFreeSlug(tag: BlogTag): Unit = {
    val builder = manager.getCriteriaBuilder
    val query = builder.createQuery(classOf[lang.Long])
    val other = query.from(classOf[BlogTag])
    val sameSlug = builder.equal(other.get(BlogTag.schema.slug), tag.slug)
    val conditions = if (tag.id == null) Seq(sameSlug)
      else Seq(sameSlug, builder.notEqual(other.get(BlogTag.schema.id), tag.id))
    query.select(builder.count(other)).where(conditions*)
    if (manager.createQuery(query).setFlushMode(FlushModeType.COMMIT).getSingleResult.longValue() > 0)
      throw new ApiProblem(409, "This slug is already used by another tag.", Problem.conflict,
        Seq(new ErrorRequest(util.List.of[Any]("slug"), "Choose an unused slug.")))
  }

  private def result(tag: BlogTag): Data[BlogTag] =
    new Data(tag, Schema.forGraph(BlogTag.schema, manager.getEntityGraph("BlogTag.detail")),
      util.List.of(new Link("update", s"/service/editorial/tags/${tag.id}", "PATCH", "BlogTag")))
}
