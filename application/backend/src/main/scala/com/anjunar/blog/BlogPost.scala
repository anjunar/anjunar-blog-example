package com.anjunar.blog

import com.anjunar.hibernateddl.hibernate.annotation.SchemaId
import com.anjunar.json.mapper.annotations.UseConverter
import com.anjunar.json.mapper.provider.EntityProvider
import com.anjunar.json.mapper.schema.{EntitySchema, SchemaProvider}
import com.anjunar.json.mapper.schema.property.SingularProperty
import jakarta.json.bind.annotation.JsonbProperty
import jakarta.persistence.{Access, AccessType, CheckConstraint, Column, Entity, EntityManager, Enumerated, EnumType, GeneratedValue, GenerationType, Id, NamedAttributeNode, NamedEntityGraph, NamedEntityGraphs, Table, Transient, UniqueConstraint, Version}
import jakarta.validation.constraints.{AssertTrue, NotBlank, NotNull, Pattern, Size}

import java.lang
import java.time.Instant
import java.util
import java.util.UUID

@Entity
@SchemaId("d4f39c20")
@Access(AccessType.FIELD)
@Table(name = "blog_post", schema = "public",
  uniqueConstraints = Array(new UniqueConstraint(name = "uq_blog_post_slug", columnNames = Array("slug"))),
  check = Array(new CheckConstraint(name = "ck_blog_post_publication",
    constraint = "(status = 'DRAFT' AND published_at IS NULL) OR (status = 'PUBLISHED' AND published_at IS NOT NULL)")))
@NamedEntityGraphs(Array(
  new NamedEntityGraph(name = "BlogPost.list", attributeNodes = Array(
    new NamedAttributeNode("id"),
    new NamedAttributeNode("version"),
    new NamedAttributeNode("slug"),
    new NamedAttributeNode("title"),
    new NamedAttributeNode("summary"),
    new NamedAttributeNode("status"),
    new NamedAttributeNode("publishedAt")
  )),
  new NamedEntityGraph(name = "BlogPost.detail", attributeNodes = Array(
    new NamedAttributeNode("id"),
    new NamedAttributeNode("version"),
    new NamedAttributeNode("slug"),
    new NamedAttributeNode("title"),
    new NamedAttributeNode("content"),
    new NamedAttributeNode("summary"),
    new NamedAttributeNode("status"),
    new NamedAttributeNode("publishedAt")
  ))
))
class BlogPost extends EntityProvider {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  @SchemaId("a2473e8b")
  @JsonbProperty
  var id: UUID = null

  @Version
  @Column(nullable = false)
  @SchemaId("dcb0681e")
  @JsonbProperty
  var version: Long = -1L

  @NotBlank
  @Size(min = 3, max = 220)
  @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$")
  @Column(nullable = false, length = 220)
  @SchemaId("682d9ace")
  @JsonbProperty
  var slug: String = ""

  @NotBlank
  @Size(min = 3, max = 180)
  @Column(nullable = false, length = 180)
  @SchemaId("46fdb02a")
  @JsonbProperty
  var title: String = ""

  @NotNull
  @Size(max = 100000)
  @Column(nullable = false, columnDefinition = "text")
  @SchemaId("7b20efc1")
  @JsonbProperty
  var content: String = ""

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  @SchemaId("cf271a06")
  @JsonbProperty
  var status: BlogPostStatus = BlogPostStatus.DRAFT

  @Column(name = "published_at")
  @SchemaId("398bfd50")
  @JsonbProperty
  @UseConverter(classOf[InstantConverter])
  var publishedAt: Instant = null

  @Size(max = 300)
  @Column(length = 300)
  @SchemaId("0ca6e520")
  @JsonbProperty
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

object BlogPost extends SchemaProvider[BlogPost.Schema] {
  class Schema extends EntitySchema[BlogPost](RuntimeContext.entityManager()) {
    val id: SingularProperty[BlogPost, UUID] = reference(_.id)
    val version: SingularProperty[BlogPost, Long] = reference(_.version)
    val slug: SingularProperty[BlogPost, String] = reference(_.slug)
    val title: SingularProperty[BlogPost, String] = reference(_.title)
    val content: SingularProperty[BlogPost, String] = reference(_.content)
    val status: SingularProperty[BlogPost, BlogPostStatus] = reference(_.status)
    val publishedAt: SingularProperty[BlogPost, Instant] = reference(_.publishedAt)
    val summary: SingularProperty[BlogPost, String] = reference(_.summary)
  }

  def findPublishedBySlug(slug: String)(using entityManager: EntityManager): Option[BlogPost] = {
    val builder = entityManager.getCriteriaBuilder
    val query = builder.createQuery(classOf[BlogPost])
    val post = query.from(classOf[BlogPost])
    query.select(post).where(
      builder.equal(post.get(schema.slug), builder.parameter(classOf[String], "slug")),
      builder.equal(post.get(schema.status), BlogPostStatus.PUBLISHED)
    )
    Option(entityManager.createQuery(query)
      .setHint("jakarta.persistence.fetchgraph", entityManager.getEntityGraph("BlogPost.detail"))
      .setParameter("slug", slug).getSingleResultOrNull)
  }

  def listPublished(offset: Int, limit: Int)(using entityManager: EntityManager): util.List[BlogPost] = {
    val builder = entityManager.getCriteriaBuilder
    val query = builder.createQuery(classOf[BlogPost])
    val post = query.from(classOf[BlogPost])
    query.select(post)
      .where(Seq(builder.equal(post.get(schema.status), BlogPostStatus.PUBLISHED))*)
      .orderBy(builder.desc(post.get(schema.publishedAt)), builder.asc(post.get(schema.id)))
    entityManager.createQuery(query)
      .setHint("jakarta.persistence.fetchgraph", entityManager.getEntityGraph("BlogPost.list"))
      .setFirstResult(offset)
      .setMaxResults(limit)
      .getResultList
  }

  def countPublished()(using entityManager: EntityManager): Long = {
    val builder = entityManager.getCriteriaBuilder
    val query = builder.createQuery(classOf[lang.Long])
    val post = query.from(classOf[BlogPost])
    query.select(builder.count(post))
      .where(Seq(builder.equal(post.get(schema.status), BlogPostStatus.PUBLISHED))*)
    entityManager.createQuery(query).getSingleResult.longValue()
  }
}
