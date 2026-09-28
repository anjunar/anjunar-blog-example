package com.anjunar.blog

import com.anjunar.hibernateddl.hibernate.annotation.SchemaId
import com.anjunar.json.mapper.annotations.UseConverter
import com.anjunar.json.mapper.provider.EntityProvider
import com.anjunar.json.mapper.schema.{EntitySchema, SchemaProvider}
import com.anjunar.json.mapper.schema.property.SingularProperty
import jakarta.json.bind.annotation.{JsonbProperty, JsonbTransient}
import jakarta.persistence.{Access, AccessType, Column, Entity, GeneratedValue, GenerationType, Id, NamedAttributeNode, NamedEntityGraph, Table, Version}
import jakarta.validation.constraints.{NotBlank, NotNull, Size}

import java.time.Instant
import java.util.UUID

@Entity
@SchemaId("ad181000")
@Access(AccessType.FIELD)
@Table(name = "blog_media", schema = "public")
@NamedEntityGraph(name = "Media.detail", attributeNodes = Array(
  new NamedAttributeNode("id"), new NamedAttributeNode("version"), new NamedAttributeNode("name"),
  new NamedAttributeNode("contentType"), new NamedAttributeNode("width"),
  new NamedAttributeNode("height"), new NamedAttributeNode("byteSize")
))
class Media extends EntityProvider {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  @SchemaId("ad181001") @JsonbProperty
  var id: UUID = null

  @Version @Column(nullable = false)
  @SchemaId("ad181002") @JsonbProperty
  var version: Long = -1L

  @NotBlank @Size(max = 80) @Column(nullable = false, length = 80)
  @SchemaId("ad181003") @JsonbProperty
  var name: String = ""

  @NotBlank @Column(name = "content_type", nullable = false, length = 32)
  @SchemaId("ad181004") @JsonbProperty
  var contentType: String = ""

  @Column(nullable = false) @SchemaId("ad181005") @JsonbProperty
  var width: Int = 0

  @Column(nullable = false) @SchemaId("ad181006") @JsonbProperty
  var height: Int = 0

  @Column(name = "byte_size", nullable = false) @SchemaId("ad181007") @JsonbProperty
  var byteSize: Long = 0L

  @NotNull @Column(name = "created_at", nullable = false, updatable = false)
  @SchemaId("ad181008") @JsonbProperty @UseConverter(classOf[InstantConverter])
  var createdAt: Instant = Instant.now()

  @NotNull @Column(name = "owner_id", nullable = false, updatable = false)
  @SchemaId("ad181009") @JsonbTransient
  var ownerId: UUID = null

  // PostgreSQL bytea stays in the same transaction as metadata; it is never JSON.
  @NotNull @Column(nullable = false, columnDefinition = "bytea")
  @SchemaId("ad18100a") @JsonbTransient
  var data: Array[Byte] = Array.emptyByteArray
}

object Media extends SchemaProvider[Media.Schema] {
  class Schema extends EntitySchema[Media](RuntimeContext.entityManager()) {
    val id: SingularProperty[Media, UUID] = reference(_.id)
    val version: SingularProperty[Media, Long] = reference(_.version)
    val name: SingularProperty[Media, String] = reference(_.name)
    val contentType: SingularProperty[Media, String] = reference(_.contentType)
    val width: SingularProperty[Media, Int] = reference(_.width)
    val height: SingularProperty[Media, Int] = reference(_.height)
    val byteSize: SingularProperty[Media, Long] = reference(_.byteSize)
    val createdAt: SingularProperty[Media, Instant] = reference(_.createdAt)
    val ownerId: SingularProperty[Media, UUID] = reference(_.ownerId)
  }
}
