package com.anjunar.blog

import com.anjunar.json.mapper.annotations.UseConverter
import com.anjunar.json.mapper.provider.DTO
import jakarta.json.bind.annotation.JsonbProperty
import jakarta.persistence.criteria.Expression
import org.hibernate.query.criteria.{HibernateCriteriaBuilder, JpaCriteriaQuery, JpaRoot}

import java.time.Instant
import java.util
import java.util.UUID
import scala.annotation.meta.field

// A read-only list projection. Load BlogPost detail before editing.
final class BlogPostSummary(
    @(JsonbProperty @field) val id: UUID,
    @(JsonbProperty @field) val version: Long,
    @(JsonbProperty @field) val slug: String,
    @(JsonbProperty @field) val title: String,
    @(JsonbProperty @field) val summary: String,
    @(JsonbProperty @field) val status: BlogPostStatus,
    @(JsonbProperty @field) @(UseConverter @field)(classOf[InstantConverter]) val publishedAt: Instant,
    @(JsonbProperty @field) val contentLocale: String = "en"
) extends DTO

object BlogPostSummary {
  def select(
      query: JpaCriteriaQuery[BlogPostSummary], post: JpaRoot[BlogPost],
      selection: util.List[Expression[?]], builder: HibernateCriteriaBuilder
  ): JpaCriteriaQuery[BlogPostSummary] = {
    selectLocalized(query, post, selection, builder, "en")
  }

  def selectLocalized(
      query: JpaCriteriaQuery[BlogPostSummary], post: JpaRoot[BlogPost],
      selection: util.List[Expression[?]], builder: HibernateCriteriaBuilder, locale: String
  ): JpaCriteriaQuery[BlogPostSummary] = {
    val schema = BlogPost.schema
    val fields = LocalizedPostFields(post, builder, locale)
    query.select(builder.construct(classOf[BlogPostSummary],
      post.get(schema.id), post.get(schema.version), post.get(schema.slug),
      fields.title, fields.summary, post.get(schema.status),
      post.get(schema.publishedAt), fields.locale))
  }
}
