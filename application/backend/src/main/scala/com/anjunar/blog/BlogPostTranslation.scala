package com.anjunar.blog

import com.anjunar.hibernateddl.hibernate.annotation.SchemaId
import com.anjunar.json.mapper.provider.EntityProvider
import com.anjunar.json.mapper.schema.{EntitySchema, SchemaProvider, VisibilityRule}
import com.anjunar.json.mapper.schema.property.{SetProperty, SingularProperty}
import com.anjunar.scala.universe.introspector.AbstractProperty
import jakarta.enterprise.context.RequestScoped
import jakarta.enterprise.inject.Typed
import jakarta.inject.Inject
import jakarta.json.bind.annotation.{JsonbProperty, JsonbTransient}
import jakarta.persistence.{Access, AccessType, Column, Entity, FetchType, ForeignKey, GeneratedValue, GenerationType, Id, JoinColumn, JoinTable, ManyToMany, ManyToOne, NamedAttributeNode, NamedEntityGraph, Table, Transient, UniqueConstraint, Version}
import jakarta.validation.constraints.{AssertTrue, NotBlank, NotNull, Pattern, Size}

import jakarta.persistence.{PrePersist, PreUpdate}
import java.time.Instant
import java.util
import java.util.UUID
import scala.compiletime.uninitialized

@Entity
@SchemaId("ab210001")
@Access(AccessType.FIELD)
@Table(name = "blog_post_translation", schema = "public",
  uniqueConstraints = Array(new UniqueConstraint(name = "uq_post_translation_locale", columnNames = Array("post_id", "locale"))))
@NamedEntityGraph(name = "BlogPostTranslation.detail", attributeNodes = Array(
  new NamedAttributeNode("id"), new NamedAttributeNode("version"), new NamedAttributeNode("locale"),
  new NamedAttributeNode("title"), new NamedAttributeNode("summary"), new NamedAttributeNode("content"),
  new NamedAttributeNode("published")))
class BlogPostTranslation extends EntityProvider {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false) @SchemaId("ab210002") @JsonbProperty
  var id: UUID = null

  @Version @Column(nullable = false) @SchemaId("ab210003") @JsonbProperty
  var version: Long = -1L

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "post_id", nullable = false, foreignKey = new ForeignKey(name = "fk_translation_post"))
  @NotNull @SchemaId("ab210004") @JsonbTransient
  var post: BlogPost = null

  // English remains the existing source on BlogPost; this chapter adds German.
  @NotBlank @Pattern(regexp = "de") @Column(nullable = false, length = 2)
  @SchemaId("ab210005") @JsonbProperty
  var locale: String = "de"

  @NotBlank @Size(min = 3, max = 180) @Column(nullable = false, length = 180)
  @SchemaId("ab210006") @JsonbProperty
  var title: String = ""

  @Size(max = 300) @Column(length = 300) @SchemaId("ab210007") @JsonbProperty
  var summary: String = null

  @NotNull @Size(max = 100000) @Column(nullable = false, columnDefinition = "text")
  @SchemaId("ab210008") @JsonbProperty
  var content: String = ""

  @Column(nullable = false) @SchemaId("ab210009") @JsonbProperty
  var published: Boolean = false

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(name = "blog_post_translation_media", schema = "public",
    joinColumns = Array(new JoinColumn(name = "translation_id")),
    inverseJoinColumns = Array(new JoinColumn(name = "media_id")),
    foreignKey = new ForeignKey(name = "fk_translation_media_translation"),
    inverseForeignKey = new ForeignKey(name = "fk_translation_media_media"))
  @NotNull @SchemaId("ab210010") @JsonbTransient
  var inlineMedia: util.Set[Media] = new util.LinkedHashSet[Media]()

  @Column(name = "updated_at") @SchemaId("ab240002") @JsonbTransient
  var updatedAt: Instant = null

  @PrePersist @PreUpdate
  def recordChange(): Unit = updatedAt = Instant.now()

  @Transient @AssertTrue(message = "The translated Markdown document is invalid.")
  def isDocumentConsistent: Boolean = PostDocument.inspect(content).isRight

  @Transient @AssertTrue(message = "A published translation needs content.")
  def isPublicationConsistent: Boolean = !published || PostDocument.hasContent(content, "MARKDOWN")
}

object BlogPostTranslation extends SchemaProvider[BlogPostTranslation.Schema] {
  class Schema extends EntitySchema[BlogPostTranslation](RuntimeContext.entityManager()) {
    val id: SingularProperty[BlogPostTranslation, UUID] = reference(_.id)
    val version: SingularProperty[BlogPostTranslation, Long] = reference(_.version)
    val updatedAt: SingularProperty[BlogPostTranslation, Instant] = reference(_.updatedAt)
    val post: SingularProperty[BlogPostTranslation, BlogPost] = reference(_.post)
    val locale: SingularProperty[BlogPostTranslation, String] = reference(_.locale, classOf[TranslationReadRule])
    val title: SingularProperty[BlogPostTranslation, String] = reference(_.title, classOf[TranslationEditRule])
    val summary: SingularProperty[BlogPostTranslation, String] = reference(_.summary, classOf[TranslationEditRule])
    val content: SingularProperty[BlogPostTranslation, String] = reference(_.content, classOf[TranslationEditRule])
    val published: SingularProperty[BlogPostTranslation, Boolean] = reference(_.published, classOf[TranslationReadRule])
    val inlineMedia: SetProperty[BlogPostTranslation, util.Set[Media]] = set(_.inlineMedia)
  }
}

@RequestScoped
class TranslationReadRule extends VisibilityRule[BlogPostTranslation] {
  @Inject var caller: CallerAccess = uninitialized
  override def isVisible(value: BlogPostTranslation, property: AbstractProperty): Boolean =
    caller.administrator || (value.published && value.post != null && value.post.status == BlogPostStatus.PUBLISHED)
  override def isWriteable(value: BlogPostTranslation, property: AbstractProperty): Boolean = false
}

@RequestScoped
@Typed(Array(classOf[TranslationEditRule]))
class TranslationEditRule extends TranslationReadRule {
  override def isWriteable(value: BlogPostTranslation, property: AbstractProperty): Boolean = caller.administrator
}
