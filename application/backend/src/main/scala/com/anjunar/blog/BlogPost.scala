package com.anjunar.blog

import com.anjunar.hibernateddl.hibernate.annotation.SchemaId
import jakarta.persistence.{Access, AccessType, CheckConstraint, Column, Entity, Enumerated, EnumType, GeneratedValue, GenerationType, Id, Table, Transient, UniqueConstraint, Version}
import jakarta.validation.constraints.{AssertTrue, NotBlank, NotNull, Pattern, Size}

import java.lang
import java.time.Instant
import java.util.UUID

@Entity
@SchemaId("d4f39c20")
@Access(AccessType.FIELD)
@Table(name = "blog_post", schema = "public",
  uniqueConstraints = Array(new UniqueConstraint(name = "uq_blog_post_slug", columnNames = Array("slug"))),
  check = Array(new CheckConstraint(name = "ck_blog_post_publication",
    constraint = "(status = 'DRAFT' AND published_at IS NULL) OR (status = 'PUBLISHED' AND published_at IS NOT NULL)")))
class BlogPost {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  @SchemaId("a2473e8b")
  var id: UUID = null

  @Version
  @Column(nullable = false)
  @SchemaId("dcb0681e")
  var version: lang.Long = null

  @NotBlank
  @Size(min = 3, max = 220)
  @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$")
  @Column(nullable = false, length = 220)
  @SchemaId("682d9ace")
  var slug: String = ""

  @NotBlank
  @Size(min = 3, max = 180)
  @Column(nullable = false, length = 180)
  @SchemaId("46fdb02a")
  var title: String = ""

  @NotNull
  @Size(max = 100000)
  @Column(nullable = false, columnDefinition = "text")
  @SchemaId("7b20efc1")
  var content: String = ""

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  @SchemaId("cf271a06")
  var status: BlogPostStatus = BlogPostStatus.DRAFT

  @Column(name = "published_at")
  @SchemaId("398bfd50")
  var publishedAt: Instant = null

  @Size(max = 300)
  @Column(length = 300)
  @SchemaId("0ca6e520")
  var summary: String = null

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
