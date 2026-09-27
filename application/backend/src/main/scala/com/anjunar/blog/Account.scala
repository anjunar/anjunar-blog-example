package com.anjunar.blog

import com.anjunar.hibernateddl.hibernate.annotation.SchemaId
import com.anjunar.json.mapper.provider.EntityProvider
import com.anjunar.json.mapper.schema.{EntitySchema, SchemaProvider}
import com.anjunar.json.mapper.schema.property.SingularProperty
import jakarta.json.bind.annotation.{JsonbProperty, JsonbTransient}
import jakarta.persistence.{Access, AccessType, Column, Entity, EntityManager, GeneratedValue, GenerationType, Id, NamedAttributeNode, NamedEntityGraph, Table, UniqueConstraint, Version}
import jakarta.validation.constraints.{Email, NotBlank, Pattern, Size}

import java.util.{Locale, UUID}

@Entity
@SchemaId("b971c302")
@Access(AccessType.FIELD)
@Table(name = "blog_account", schema = "public",
  uniqueConstraints = Array(new UniqueConstraint(name = "uq_blog_account_email", columnNames = Array("email"))))
@NamedEntityGraph(name = "Account.self", attributeNodes = Array(
  new NamedAttributeNode("id"), new NamedAttributeNode("version"),
  new NamedAttributeNode("email"), new NamedAttributeNode("role")
))
class Account extends EntityProvider {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  @SchemaId("580ef092")
  @JsonbProperty
  var id: UUID = null

  @Version
  @Column(nullable = false)
  @SchemaId("110a7b7f")
  @JsonbProperty
  var version: Long = -1L

  @Email
  @NotBlank
  @Size(max = 254)
  @Column(nullable = false, length = 254)
  @SchemaId("0161ccea")
  @JsonbProperty
  var email: String = ""

  @Pattern(regexp = "ADMIN|READER")
  @NotBlank
  @Column(nullable = false, length = 16)
  @SchemaId("b3421d61")
  @JsonbProperty
  var role: String = "READER"

  @NotBlank
  @Column(name = "password_hash", nullable = false, length = 200)
  @SchemaId("0f26bca3")
  @JsonbTransient
  var passwordHash: String = ""

  @Column(name = "authentication_version", nullable = false)
  @SchemaId("18b98034")
  @JsonbTransient
  var authenticationVersion: Long = 0L

  @Column(nullable = false)
  @SchemaId("7672ca31")
  @JsonbTransient
  var locked: Boolean = false
}

object Account extends SchemaProvider[Account.Schema] {
  def canonicalEmail(value: String): String =
    Option(value).getOrElse("").trim.toLowerCase(Locale.ROOT)

  class Schema extends EntitySchema[Account](RuntimeContext.entityManager()) {
    val id: SingularProperty[Account, UUID] = reference(_.id)
    val version: SingularProperty[Account, Long] = reference(_.version)
    val email: SingularProperty[Account, String] = reference(_.email)
    val role: SingularProperty[Account, String] = reference(_.role)
  }

  def byEmail(email: String)(using manager: EntityManager): Option[Account] = {
    val builder = manager.getCriteriaBuilder
    val query = builder.createQuery(classOf[Account])
    val account = query.from(classOf[Account])
    query.select(account).where(Seq(builder.equal(account.get(schema.email), email))*)
    Option(manager.createQuery(query).getSingleResultOrNull)
  }
}
