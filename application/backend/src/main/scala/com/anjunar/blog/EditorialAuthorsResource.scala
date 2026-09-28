package com.anjunar.blog

import com.anjunar.blog.hibernate.search.HibernateSearch
import com.anjunar.json.mapper.PreparedChange
import com.anjunar.json.mapper.schema.Link
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.ws.rs.{BeanParam, Consumes, ForbiddenException, GET, PATCH, Path, PathParam, Produces}
import jakarta.ws.rs.core.MediaType

import java.util
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

@Path("/editorial/authors")
@RolesAllowed(Array("ADMIN"))
@RequestScoped
@Produces(Array(MediaType.APPLICATION_JSON))
class EditorialAuthorsResource {
  @Inject var manager: EntityManager = uninitialized
  @Inject var queries: HibernateSearch = uninitialized
  @Inject var caller: CallerAccess = uninitialized

  @GET @EntityGraph("Account.author")
  def list(@BeanParam parameters: CatalogParams): Table[Data[Account]] = {
    val context = queries.searchContext(AuthorSearch(index = parameters.start, limit = parameters.size))
    val rows = queries.entities(parameters.start, parameters.size, classOf[Account], classOf[Account],
      context, (query, root, _, _) => query.select(root)).asScala.map(result).asJava
    val total = queries.count(classOf[Account], context)
    new Table(rows, total, parameters.links("/service/editorial/authors", total, create = false))
  }

  @PATCH @Path("/{id}") @Consumes(Array(MediaType.APPLICATION_JSON))
  @EntityGraph("Account.author")
  def update(@PathParam("id") change: PreparedChange[Account]): Data[Account] = {
    val account = change.getEntity()
    if (!caller.administrator || account.locked || account.role != "ADMIN") throw new ForbiddenException()
    change.applyChanges()
    manager.flush()
    result(account)
  }

  private def result(account: Account): Data[Account] =
    new Data(account, Schema.forGraph(Account.schema, manager.getEntityGraph("Account.author")),
      util.List.of(new Link("update", s"/service/editorial/authors/${account.id}", "PATCH", "Account")))
}
