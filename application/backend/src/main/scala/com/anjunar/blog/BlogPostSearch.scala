package com.anjunar.blog

import com.anjunar.blog.hibernate.search.{AbstractSearch, Context, PredicateProvider, SortProvider}
import com.anjunar.blog.hibernate.search.annotations.{RestPredicate, RestSort}
import jakarta.enterprise.context.ApplicationScoped
import jakarta.json.bind.annotation.JsonbProperty
import jakarta.persistence.criteria.Order
import jakarta.ws.rs.core.UriBuilder

import java.lang
import java.util
import java.util.Locale
import scala.annotation.meta.field
import scala.jdk.CollectionConverters.*

final case class BlogPostSearch(
    @(JsonbProperty @field) query: String,
    @(JsonbProperty @field) @(RestPredicate @field)(classOf[BlogPostSearch.StatusPredicate])
    status: Option[BlogPostStatus],
    @(JsonbProperty @field) @(RestSort @field)(classOf[BlogPostSearch.PostSort])
    sort: String,
    offset: Int,
    override val limit: Int,
    locale: String = "en"
) extends AbstractSearch {
  @JsonbProperty @RestPredicate(classOf[BlogPostSearch.QueryPredicate])
  val textFilter: BlogPostSearch.TextFilter = BlogPostSearch.TextFilter(query, locale)

  override def index: Int = offset

  def pageUrl(path: String, start: Int): String = {
    val uri = UriBuilder.fromPath(path).queryParam("offset", start).queryParam("limit", limit)
    if (locale != "en") uri.queryParam("locale", locale)
    // Insert raw text as a template value: literal %20 and braces must be encoded as data.
    if (query.nonEmpty) uri.queryParam("q", "{search}")
    status.foreach(value => uri.queryParam("status", value.name()))
    uri.queryParam("sort", sort)
    (if (query.nonEmpty) uri.build(query) else uri.build()).toASCIIString
  }
}

object BlogPostSearch {
  final case class TextFilter(text: String, locale: String)

  @ApplicationScoped
  class QueryPredicate extends PredicateProvider[TextFilter, BlogPost] {
    override def build(context: Context[TextFilter, BlogPost]): Unit = {
      if (context.value.text.nonEmpty) {
        val builder = context.builder
        val post = context.root
        val pattern = builder.parameter(classOf[String], context.name)
        val fields = LocalizedPostFields(post, builder, context.value.locale)
        context.predicates.add(builder.or(
          builder.like(builder.lower(fields.title), pattern, '!'),
          builder.like(builder.lower(post.get(BlogPost.schema.slug)), pattern, '!'),
          builder.like(builder.lower(fields.summary), pattern, '!')
        ))
        val literal = context.value.text.toLowerCase(Locale.ROOT)
          .replace("!", "!!").replace("%", "!%").replace("_", "!_")
        context.parameters.put(context.name, s"%$literal%")
      }
    }
  }

  @ApplicationScoped
  class StatusPredicate extends PredicateProvider[Option[BlogPostStatus], BlogPost] {
    override def build(context: Context[Option[BlogPostStatus], BlogPost]): Unit =
      context.value.foreach { status =>
        val parameter = context.builder.parameter(classOf[BlogPostStatus], context.name)
        context.predicates.add(context.builder.equal(context.root.get(BlogPost.schema.status), parameter))
        context.parameters.put(context.name, status)
      }
  }

  @ApplicationScoped
  class PostSort extends SortProvider[BlogPostSearch, BlogPost] {
    override def sort(context: Context[BlogPostSearch, BlogPost]): util.List[Order] = {
      val builder = context.builder
      val post = context.root
      val title = builder.lower(LocalizedPostFields(post, builder, context.value.locale).title)
      val publication = post.get(BlogPost.schema.publishedAt)
      val primary = context.value.sort match {
        case "title" => Seq(builder.asc(title))
        case "title-desc" => Seq(builder.desc(title))
        case direction @ ("oldest" | "newest") =>
          // Keep drafts after dated posts in both directions.
          val undated = builder.selectCase[lang.Integer]()
            .when(builder.isNull(publication), 1).otherwise(0)
          Seq(builder.asc(undated),
            if (direction == "oldest") builder.asc(publication) else builder.desc(publication))
        case _ => throw new IllegalArgumentException("Unsupported post sort")
      }
      (primary :+ builder.asc(post.get(BlogPost.schema.id))).asJava
    }
  }
}
