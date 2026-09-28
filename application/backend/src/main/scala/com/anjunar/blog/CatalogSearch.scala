package com.anjunar.blog

import com.anjunar.blog.hibernate.search.{AbstractSearch, Context, PredicateProvider, SortProvider}
import com.anjunar.blog.hibernate.search.annotations.{RestPredicate, RestSort}
import com.anjunar.json.mapper.schema.Link
import jakarta.enterprise.context.ApplicationScoped
import jakarta.json.bind.annotation.JsonbProperty
import jakarta.persistence.criteria.Order
import jakarta.ws.rs.{BadRequestException, DefaultValue, QueryParam}
import jakarta.ws.rs.core.UriBuilder

import java.util
import scala.annotation.meta.field
import scala.jdk.CollectionConverters.*

final class CatalogParams {
  @QueryParam("offset") @DefaultValue("0") var offset: String = "0"
  @QueryParam("limit") @DefaultValue("20") var limit: String = "20"

  def start: Int = Option(offset).flatMap(_.toIntOption).filter(_ >= 0)
    .getOrElse(throw new BadRequestException("offset must be a nonnegative integer"))
  def size: Int = Option(limit).flatMap(_.toIntOption).filter(value => value >= 1 && value <= 100)
    .getOrElse(throw new BadRequestException("limit must be between 1 and 100"))

  def links(path: String, total: Long, create: Boolean): util.List[Link] = {
    val from = start
    val count = size
    def page(rel: String, index: Int): Link =
      new Link(rel, UriBuilder.fromPath(path).queryParam("offset", index)
        .queryParam("limit", count).build().toASCIIString, "GET", "")
    Seq(Some(page("self", from)),
      Option.when(from > 0)(page("previous", math.max(0, from - count))),
      Option.when(from.toLong + count < total && from <= Int.MaxValue - count)(page("next", from + count)),
      Option.when(create)(new Link("create", path, "POST", "BlogTag"))).flatten.asJava
  }
}

final case class AuthorSearch(
    @(JsonbProperty @field) @(RestPredicate @field)(classOf[AuthorSearch.Available])
    available: Boolean = true,
    @(JsonbProperty @field) @(RestSort @field)(classOf[AuthorSearch.ByName])
    sort: String = "name",
    override val index: Int = 0,
    override val limit: Int = 20
) extends AbstractSearch

object AuthorSearch {
  @ApplicationScoped
  class Available extends PredicateProvider[Boolean, Account] {
    override def build(context: Context[Boolean, Account]): Unit = {
      require(context.value, "Only available authors can be selected")
      context.predicates.add(context.builder.equal(context.root.get(Account.schema.role), "ADMIN"))
      context.predicates.add(context.builder.equal(context.root.get(Account.schema.locked), false))
    }
  }

  @ApplicationScoped
  class ByName extends SortProvider[AuthorSearch, Account] {
    override def sort(context: Context[AuthorSearch, Account]): util.List[Order] =
      util.List.of(context.builder.asc(context.builder.lower(context.root.get(Account.schema.displayName))),
        context.builder.asc(context.root.get(Account.schema.id)))
  }
}

final case class TagSearch(
    @(JsonbProperty @field) @(RestSort @field)(classOf[TagSearch.ByName])
    sort: String = "name",
    override val index: Int = 0,
    override val limit: Int = 20
) extends AbstractSearch

object TagSearch {
  @ApplicationScoped
  class ByName extends SortProvider[TagSearch, BlogTag] {
    override def sort(context: Context[TagSearch, BlogTag]): util.List[Order] =
      util.List.of(context.builder.asc(context.builder.lower(context.root.get(BlogTag.schema.name))),
        context.builder.asc(context.root.get(BlogTag.schema.id)))
  }
}
