package com.anjunar.blog

import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.persistence.criteria.JoinType
import jakarta.ws.rs.ServiceUnavailableException

import java.time.Instant
import java.util.UUID
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

final case class PublishedPage(id: UUID, slug: String, locale: String, title: String,
    summary: Option[String], published: Instant, changed: Option[Instant]) {
  def path: String = s"/$locale/posts/$slug"
  def updated: Instant = changed.filter(_.isAfter(published)).getOrElse(published)
}

@RequestScoped
class PublishedPages {
  @Inject var manager: EntityManager = uninitialized

  // A bounded read projection, not an editable entity or a second domain model.
  def load(locale: Option[String], limit: Int): Vector[PublishedPage] = {
    val builder = manager.getCriteriaBuilder
    val query = builder.createTupleQuery()
    val post = query.from(classOf[BlogPost])
    val translation = post.joinSet[BlogPost, BlogPostTranslation](BlogPost.schema.translations.name, JoinType.LEFT)
    translation.on(
      builder.equal(translation.get(BlogPostTranslation.schema.locale), "de"),
      builder.equal(translation.get(BlogPostTranslation.schema.published), true))
    query.multiselect(
      post.get(BlogPost.schema.id).alias("id"), post.get(BlogPost.schema.slug).alias("slug"),
      post.get(BlogPost.schema.title).alias("title"), post.get(BlogPost.schema.summary).alias("summary"),
      post.get(BlogPost.schema.publishedAt).alias("published"), post.get(BlogPost.schema.updatedAt).alias("changed"),
      translation.get(BlogPostTranslation.schema.id).alias("translationId"),
      translation.get(BlogPostTranslation.schema.title).alias("translatedTitle"),
      translation.get(BlogPostTranslation.schema.summary).alias("translatedSummary"),
      translation.get(BlogPostTranslation.schema.updatedAt).alias("translationChanged"))
    val visible = builder.equal(post.get(BlogPost.schema.status), BlogPostStatus.PUBLISHED)
    val predicates = Seq(visible) ++ Option.when(locale.contains("de"))(
      builder.isNotNull(translation.get(BlogPostTranslation.schema.id)))
    query.where(predicates*)
    query.orderBy(builder.desc(post.get(BlogPost.schema.publishedAt)), builder.asc(post.get(BlogPost.schema.id)))
    manager.createQuery(query).setMaxResults(limit).getResultList.asScala.toVector.flatMap { row =>
      val published = row.get("published", classOf[Instant])
      val sourceChanged = Option(row.get("changed", classOf[Instant]))
      val source = PublishedPage(row.get("id", classOf[UUID]), row.get("slug", classOf[String]),
        "en", row.get("title", classOf[String]), Option(row.get("summary", classOf[String])), published, sourceChanged)
      val german = Option(row.get("translationId", classOf[UUID])).map { id =>
        val changed = (sourceChanged.toSeq ++ Option(row.get("translationChanged", classOf[Instant]))).sorted.lastOption
        PublishedPage(id, source.slug, "de", row.get("translatedTitle", classOf[String]),
          Option(row.get("translatedSummary", classOf[String])), published, changed)
      }
      locale match {
        case Some("en") => Vector(source)
        case Some("de") => german.toVector
        case _ => Vector(source) ++ german
      }
    }
  }

  def sitemap(): Vector[PublishedPage] = {
    val pages = load(None, 5001)
    if (pages.count(_.locale == "en") > 5000)
      throw new ServiceUnavailableException("This tutorial sitemap supports at most 5000 published posts")
    pages
  }

  def feed(locale: String): Vector[PublishedPage] = load(Some(locale), 20)
}
