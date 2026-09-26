package com.anjunar.blog

import jakarta.persistence.{Access, AccessType, Column, Entity, Enumerated, EnumType, GeneratedValue, GenerationType, Id, Table, Transient, UniqueConstraint, Version}
import jakarta.validation.constraints.{AssertTrue, NotBlank, NotNull, Pattern, Size}

import java.lang
import java.time.Instant
import java.util.UUID
import scala.annotation.meta.field

@Entity
@Access(AccessType.FIELD)
@Table(name = "blog_post", schema = "public",
  uniqueConstraints = Array(new UniqueConstraint(name = "uq_blog_post_slug", columnNames = Array("slug"))))
class BlogPost {
  @(Id @field)
  @(GeneratedValue @field)(strategy = GenerationType.UUID)
  @(Column @field)(nullable = false, updatable = false)
  var id: UUID = null

  @(Version @field)
  @(Column @field)(nullable = false)
  var version: lang.Long = null

  @(NotBlank @field)
  @(Size @field)(min = 3, max = 220)
  @(Pattern @field)(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$")
  @(Column @field)(nullable = false, length = 220)
  var slug: String = ""

  @(NotBlank @field)
  @(Size @field)(min = 3, max = 180)
  @(Column @field)(nullable = false, length = 180)
  var title: String = ""

  @(NotNull @field)
  @(Size @field)(max = 100000)
  @(Column @field)(nullable = false, columnDefinition = "text")
  var content: String = ""

  @(NotNull @field)
  @(Enumerated @field)(EnumType.STRING)
  @(Column @field)(nullable = false, length = 24)
  var status: BlogPostStatus = BlogPostStatus.DRAFT

  @(Column @field)(name = "published_at")
  var publishedAt: Instant = null

  def publish(at: Instant): Unit = {
    require(status == BlogPostStatus.DRAFT, "Only a draft can be published")
    require(at != null, "Publication time is required")
    require(content != null && !content.isBlank, "A published post needs content")
    status = BlogPostStatus.PUBLISHED
    publishedAt = at
  }

  def retract(): Unit = {
    require(status == BlogPostStatus.PUBLISHED, "Only a published post can be retracted")
    status = BlogPostStatus.DRAFT
    publishedAt = null
  }

  @Transient
  @AssertTrue(message = "Publication status, time, and content must be consistent")
  def isPublicationConsistent: Boolean =
    status match {
      case BlogPostStatus.DRAFT => publishedAt == null
      case BlogPostStatus.PUBLISHED => publishedAt != null && content != null && !content.isBlank
      case null => false
    }
}
