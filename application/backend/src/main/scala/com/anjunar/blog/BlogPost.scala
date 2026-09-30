package com.anjunar.blog

import com.anjunar.hibernateddl.hibernate.annotation.SchemaId
import com.anjunar.json.mapper.annotations.{JsonbGraphProperty, UseConverter}
import com.anjunar.json.mapper.provider.EntityProvider
import com.anjunar.json.mapper.schema.{EntitySchema, SchemaProvider}
import com.anjunar.json.mapper.schema.property.{Property, SetProperty, SingularProperty}
import jakarta.json.bind.annotation.{JsonbProperty, JsonbTransient}
import jakarta.persistence.{Access, AccessType, CascadeType, CheckConstraint, Column, Entity, EntityManager, FetchType, ForeignKey, JoinColumn, JoinTable, ManyToMany, ManyToOne, NamedSubgraph, Enumerated, EnumType, GeneratedValue, GenerationType, Id, NamedAttributeNode, NamedEntityGraph, NamedEntityGraphs, OneToMany, Table, Transient, UniqueConstraint, Version}
import jakarta.validation.constraints.{AssertTrue, NotBlank, NotNull, Pattern, Size}

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
    new NamedAttributeNode("contentFormat"),
    new NamedAttributeNode("summary"),
    new NamedAttributeNode("status"),
    new NamedAttributeNode("publishedAt"),
    new NamedAttributeNode("coverAlt"),
    new NamedAttributeNode(value = "coverImage", subgraph = "post-cover"),
    new NamedAttributeNode(value = "author", subgraph = "public-author"),
    new NamedAttributeNode(value = "tags", subgraph = "post-tags")
  ), subgraphs = Array(
    new NamedSubgraph(name = "post-cover", attributeNodes = Array(
      new NamedAttributeNode("id"), new NamedAttributeNode("version"), new NamedAttributeNode("name"),
      new NamedAttributeNode("contentType"), new NamedAttributeNode("width"),
      new NamedAttributeNode("height"), new NamedAttributeNode("byteSize")
    )),
    new NamedSubgraph(name = "public-author", attributeNodes = Array(
      new NamedAttributeNode("id"), new NamedAttributeNode("version"), new NamedAttributeNode("displayName")
    )),
    new NamedSubgraph(name = "post-tags", attributeNodes = Array(
      new NamedAttributeNode("id"), new NamedAttributeNode("version"),
      new NamedAttributeNode("slug"), new NamedAttributeNode("name")
    ))
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

  // NULL identifies pre-editor rows and retains their plain-text interpretation.
  @Pattern(regexp = "PLAIN_TEXT|MARKDOWN")
  @Column(name = "content_format", length = 16)
  @SchemaId("ad191001") @JsonbProperty
  var contentFormat: String = "PLAIN_TEXT"

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(name = "blog_post_media", schema = "public",
    joinColumns = Array(new JoinColumn(name = "post_id")),
    inverseJoinColumns = Array(new JoinColumn(name = "media_id")),
    foreignKey = new ForeignKey(name = "fk_blog_post_media_post"),
    inverseForeignKey = new ForeignKey(name = "fk_blog_post_media_media"))
  @NotNull @SchemaId("ad191002") @JsonbTransient
  var inlineMedia: util.Set[Media] = new util.LinkedHashSet[Media]()

  @Transient @AssertTrue(message = "The Markdown document is invalid.")
  def isDocumentConsistent: Boolean = contentFormat != "MARKDOWN" || PostDocument.inspect(content).isRight

  @Transient
  def hasPublishableContent: Boolean = PostDocument.hasContent(content, contentFormat)

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

  // Nullable for existing posts and explicitly unassigned editorial work.
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "author_id", foreignKey = new ForeignKey(name = "fk_blog_post_author"))
  @SchemaId("6a1eab40") @JsonbProperty
  var author: Account = null

  // Shared tags are not owned by a post: removing a link must never delete a tag.
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(name = "blog_post_tag", schema = "public",
    joinColumns = Array(new JoinColumn(name = "post_id")),
    inverseJoinColumns = Array(new JoinColumn(name = "tag_id")),
    foreignKey = new ForeignKey(name = "fk_blog_post_tag_post"),
    inverseForeignKey = new ForeignKey(name = "fk_blog_post_tag_tag"))
  @NotNull @Size(max = 20)
  @SchemaId("42b9d1ef") @JsonbProperty
  var tags: util.Set[BlogTag] = new util.LinkedHashSet[BlogTag]()

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "cover_image_id", foreignKey = new ForeignKey(name = "fk_blog_post_cover"))
  @SchemaId("ad182001") @JsonbProperty
  var coverImage: Media = null

  @Size(max = 300) @Column(name = "cover_alt", length = 300)
  @SchemaId("ad182002") @JsonbProperty
  var coverAlt: String = null

  @Transient @AssertTrue(message = "A cover image needs alternative text.")
  def isCoverConsistent: Boolean = coverImage == null || Option(coverAlt).exists(value => !value.isBlank)

  @OneToMany(mappedBy = "post", cascade = Array(CascadeType.ALL), orphanRemoval = true)
  @SchemaId("ab210011") @JsonbTransient
  var translations: util.Set[BlogPostTranslation] = new util.LinkedHashSet[BlogPostTranslation]()

  @Transient @JsonbProperty @JsonbGraphProperty(transitive = true)
  var translation: BlogPostTranslation = null

  @Transient @JsonbProperty @JsonbGraphProperty(transitive = true)
  var contentLocale: String = null

  @Transient @JsonbProperty @JsonbGraphProperty(transitive = true)
  var availableLocales: util.List[String] = new util.ArrayList[String]()

  def publish(at: Instant): Unit = {
    require(status == BlogPostStatus.DRAFT, "Only a draft can be published")
    require(at != null, "Publication time is required")
    require(hasPublishableContent, "A published post needs content")
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
      case BlogPostStatus.PUBLISHED => publishedAt != null && hasPublishableContent
      case null => false
    }
}

object BlogPost extends SchemaProvider[BlogPost.Schema] {
  class Schema extends EntitySchema[BlogPost](RuntimeContext.entityManager()) {
    val id: SingularProperty[BlogPost, UUID] = reference(_.id)
    val version: SingularProperty[BlogPost, Long] = reference(_.version)
    val slug: SingularProperty[BlogPost, String] = reference(_.slug, classOf[PostEditRule])
    val title: SingularProperty[BlogPost, String] = reference(_.title, classOf[PostEditRule])
    val content: SingularProperty[BlogPost, String] = reference(_.content, classOf[PostEditRule])
    val contentFormat: SingularProperty[BlogPost, String] = reference(_.contentFormat, classOf[PostEditRule])
    val inlineMedia: SetProperty[BlogPost, util.Set[Media]] = set(_.inlineMedia)
    val status: SingularProperty[BlogPost, BlogPostStatus] = reference(_.status, classOf[PostReadRule])
    val publishedAt: SingularProperty[BlogPost, Instant] = reference(_.publishedAt, classOf[PostReadRule])
    val summary: SingularProperty[BlogPost, String] = reference(_.summary, classOf[PostEditRule])
    val coverImage: SingularProperty[BlogPost, Media] = reference(_.coverImage, classOf[PostEditRule])
    val coverAlt: SingularProperty[BlogPost, String] = reference(_.coverAlt, classOf[PostEditRule])
    val author: SingularProperty[BlogPost, Account] = reference(_.author, classOf[PostEditRule])
    val tags: SetProperty[BlogPost, util.Set[BlogTag]] = set(_.tags, classOf[PostEditRule])
    val translations: SetProperty[BlogPost, util.Set[BlogPostTranslation]] = set(_.translations)
    // Resolve this response-only nested schema in PostLocalization, after the two
    // persistent schemas exist. This factory needs no retained EntityManager.
    lazy val translation: Property[BlogPost, BlogPostTranslation] = property(_.translation)
    val contentLocale: Property[BlogPost, String] = property(_.contentLocale)
    val availableLocales: Property[BlogPost, util.List[String]] = property(_.availableLocales)
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

}
