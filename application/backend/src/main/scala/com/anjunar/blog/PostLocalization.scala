package com.anjunar.blog

import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.ws.rs.BadRequestException

import scala.compiletime.uninitialized

object PostLocale {
  def parse(raw: String): String = Option(raw).filter(_.nonEmpty).getOrElse("en") match {
    case locale @ ("en" | "de") => locale
    case _ => throw new BadRequestException("locale must be en or de")
  }
}

@RequestScoped
class PostLocalization {
  @Inject var manager: EntityManager = uninitialized

  // Only response-only properties change. The managed English fields remain untouched.
  def select(post: BlogPost, locale: String): Unit = {
    val builder = manager.getCriteriaBuilder
    val query = builder.createQuery(classOf[BlogPostTranslation])
    val translation = query.from(classOf[BlogPostTranslation])
    query.select(translation).where(
      builder.equal(translation.get(BlogPostTranslation.schema.post), post),
      builder.equal(translation.get(BlogPostTranslation.schema.locale), "de"),
      builder.equal(translation.get(BlogPostTranslation.schema.published), true))
    val german = Option(manager.createQuery(query).getSingleResultOrNull)
    BlogPost.schema.translation // Register the nested response field after both schemas exist.
    post.translation = if (locale == "de") german.orNull else null
    post.contentLocale = if (post.translation == null) "en" else "de"
    post.availableLocales.clear()
    post.availableLocales.add("en")
    if (german.nonEmpty) post.availableLocales.add("de")
  }
}
