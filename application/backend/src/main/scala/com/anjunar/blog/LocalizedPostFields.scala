package com.anjunar.blog

import jakarta.persistence.criteria.{Expression, Join, JoinType}
import org.hibernate.query.criteria.{HibernateCriteriaBuilder, JpaRoot}

import scala.jdk.CollectionConverters.*

final case class LocalizedPostFields(title: Expression[String], summary: Expression[String], locale: Expression[String])

object LocalizedPostFields {
  def apply(post: JpaRoot[BlogPost], builder: HibernateCriteriaBuilder, locale: String): LocalizedPostFields = {
    if (locale == "en")
      return LocalizedPostFields(post.get(BlogPost.schema.title), post.get(BlogPost.schema.summary), builder.literal("en"))
    // Reuse one unique (post, locale) join for filtering, sorting and projection.
    val translation = post.getJoins.asScala.find(_.getAlias == "published_translation")
      .map(_.asInstanceOf[Join[BlogPost, BlogPostTranslation]]).getOrElse {
        val linked = post.joinSet[BlogPost, BlogPostTranslation](BlogPost.schema.translations.name, JoinType.LEFT)
        linked.alias("published_translation")
        linked.on(
          builder.equal(linked.get(BlogPostTranslation.schema.locale), locale),
          builder.equal(linked.get(BlogPostTranslation.schema.published), true))
        linked
      }
    val present = builder.isNotNull(translation.get(BlogPostTranslation.schema.id))
    LocalizedPostFields(
      builder.selectCase[String]().when(present, translation.get(BlogPostTranslation.schema.title)).otherwise(post.get(BlogPost.schema.title)),
      // A null translated summary stays null: fallback selects a whole translation.
      builder.selectCase[String]().when(present, translation.get(BlogPostTranslation.schema.summary)).otherwise(post.get(BlogPost.schema.summary)),
      builder.selectCase[String]().when(present, locale).otherwise("en"))
  }
}
