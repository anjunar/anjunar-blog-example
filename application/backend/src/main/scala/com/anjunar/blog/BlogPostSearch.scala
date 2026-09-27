package com.anjunar.blog

import jakarta.persistence.{EntityManager, TypedQuery}
import jakarta.persistence.criteria.{CriteriaBuilder, Order, Predicate, Root}

import java.lang
import java.util
import java.util.Locale

final class BlogPostSearch(search: PostSearch)(using manager: EntityManager) {
  private def predicates(builder: CriteriaBuilder, post: Root[BlogPost]): Seq[Predicate] = {
    val visibility = search.status.toSeq.map(value => builder.equal(post.get(BlogPost.schema.status), value))
    if (search.query.isEmpty) visibility
    else {
      val pattern = builder.parameter(classOf[String], "query")
      visibility :+ builder.or(
        builder.like(builder.lower(post.get(BlogPost.schema.title)), pattern, '!'),
        builder.like(builder.lower(post.get(BlogPost.schema.slug)), pattern, '!'),
        builder.like(builder.lower(post.get(BlogPost.schema.summary)), pattern, '!')
      )
    }
  }

  private def bind[T](query: TypedQuery[T]): TypedQuery[T] = {
    if (search.query.nonEmpty) {
      val literal = search.query.toLowerCase(Locale.ROOT)
        .replace("!", "!!").replace("%", "!%").replace("_", "!_")
      query.setParameter("query", s"%$literal%")
    }
    query
  }

  private def order(builder: CriteriaBuilder, post: Root[BlogPost]): Seq[Order] = {
    val title = builder.lower(post.get(BlogPost.schema.title))
    val publication = post.get(BlogPost.schema.publishedAt)
    val primary = search.sort match {
      case "title" => Seq(builder.asc(title))
      case "title-desc" => Seq(builder.desc(title))
      case value =>
        // Keep drafts after dated posts in both directions.
        val undated = builder.selectCase[lang.Integer]()
          .when(builder.isNull(publication), 1).otherwise(0)
        Seq(builder.asc(undated),
          if (value == "oldest") builder.asc(publication) else builder.desc(publication))
    }
    primary :+ builder.asc(post.get(BlogPost.schema.id))
  }

  def rows(): util.List[BlogPostSummary] = {
    val builder = manager.getCriteriaBuilder
    val query = builder.createQuery(classOf[BlogPostSummary])
    val post = query.from(classOf[BlogPost])
    val schema = BlogPost.schema
    query.select(builder.construct(classOf[BlogPostSummary],
      post.get(schema.id), post.get(schema.version), post.get(schema.slug),
      post.get(schema.title), post.get(schema.summary), post.get(schema.status),
      post.get(schema.publishedAt)))
      .where(predicates(builder, post)*).orderBy(order(builder, post)*)
    bind(manager.createQuery(query)).setFirstResult(search.offset)
      .setMaxResults(search.limit).getResultList
  }

  def count(): Long = {
    val builder = manager.getCriteriaBuilder
    val query = builder.createQuery(classOf[lang.Long])
    val post = query.from(classOf[BlogPost])
    // Rebuild the same predicates for this query's root; Criteria nodes are not reusable.
    query.select(builder.count(post)).where(predicates(builder, post)*)
    bind(manager.createQuery(query)).getSingleResult.longValue()
  }
}
