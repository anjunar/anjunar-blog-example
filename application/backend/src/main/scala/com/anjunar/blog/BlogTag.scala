package com.anjunar.blog

import com.anjunar.hibernateddl.hibernate.annotation.SchemaId
import com.anjunar.json.mapper.provider.EntityProvider
import com.anjunar.json.mapper.schema.{EntitySchema, SchemaProvider, VisibilityRule}
import com.anjunar.json.mapper.schema.property.SingularProperty
import com.anjunar.scala.universe.introspector.AbstractProperty
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.json.bind.annotation.JsonbProperty
import jakarta.persistence.{Access, AccessType, Column, Entity, GeneratedValue, GenerationType, Id, NamedAttributeNode, NamedEntityGraph, Table, UniqueConstraint, Version}
import jakarta.validation.constraints.{NotBlank, Pattern, Size}

import java.util.UUID
import scala.compiletime.uninitialized

@Entity
@SchemaId("fa617029")
@Access(AccessType.FIELD)
@Table(name = "blog_tag", schema = "public",
  uniqueConstraints = Array(new UniqueConstraint(name = "uq_blog_tag_slug", columnNames = Array("slug"))))
@NamedEntityGraph(name = "BlogTag.detail", attributeNodes = Array(
  new NamedAttributeNode("id"), new NamedAttributeNode("version"),
  new NamedAttributeNode("slug"), new NamedAttributeNode("name")
))
class BlogTag extends EntityProvider {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  @SchemaId("927c16a3") @JsonbProperty
  var id: UUID = null

  @Version @Column(nullable = false)
  @SchemaId("1cd45fab") @JsonbProperty
  var version: Long = -1L

  @NotBlank @Size(min = 2, max = 80)
  @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$")
  @Column(nullable = false, length = 80)
  @SchemaId("e426902b") @JsonbProperty
  var slug: String = ""

  @NotBlank @Size(min = 2, max = 80)
  @Column(nullable = false, length = 80)
  @SchemaId("5aa14e09") @JsonbProperty
  var name: String = ""
}

object BlogTag extends SchemaProvider[BlogTag.Schema] {
  class Schema extends EntitySchema[BlogTag](RuntimeContext.entityManager()) {
    val id: SingularProperty[BlogTag, UUID] = reference(_.id)
    val version: SingularProperty[BlogTag, Long] = reference(_.version)
    val slug: SingularProperty[BlogTag, String] = reference(_.slug, classOf[TagEditRule])
    val name: SingularProperty[BlogTag, String] = reference(_.name, classOf[TagEditRule])
  }
}

@RequestScoped
class TagEditRule extends VisibilityRule[BlogTag] {
  @Inject var caller: CallerAccess = uninitialized
  override def isVisible(tag: BlogTag, property: AbstractProperty): Boolean = true
  override def isWriteable(tag: BlogTag, property: AbstractProperty): Boolean = caller.administrator
}

@RequestScoped
class AuthorEditRule extends VisibilityRule[Account] {
  @Inject var caller: CallerAccess = uninitialized
  override def isVisible(account: Account, property: AbstractProperty): Boolean = true
  override def isWriteable(account: Account, property: AbstractProperty): Boolean = caller.administrator
}
