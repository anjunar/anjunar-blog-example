package com.anjunar.blog

import com.anjunar.hibernateddl.hibernate.annotation.SchemaId
import com.anjunar.json.mapper.provider.EntityProvider
import com.anjunar.json.mapper.schema.{EntitySchema, SchemaProvider}
import com.anjunar.json.mapper.schema.property.SingularProperty
import jakarta.json.bind.annotation.{JsonbProperty, JsonbTransient}
import jakarta.persistence.{Access, AccessType, Column, Entity, EntityManager, GeneratedValue, GenerationType, Id, NamedAttributeNode, NamedEntityGraph, NamedEntityGraphs, Table, UniqueConstraint, Version}
import jakarta.validation.constraints.{Email, NotBlank, Pattern, Size}

import java.util.{Locale, UUID}

@Entity
@SchemaId("b971c302")
@Access(AccessType.FIELD)
@Table(name = "blog_account", schema = "public",
  uniqueConstraints = Array(new UniqueConstraint(name = "uq_blog_account_email", columnNames = Array("email"))))
@NamedEntityGraphs(Array(
  new NamedEntityGraph(name = "Account.self", attributeNodes = Array(
    new NamedAttributeNode("id"), new NamedAttributeNode("version"),
    new NamedAttributeNode("email"), new NamedAttributeNode("role"),
    new NamedAttributeNode("displayName")
  )),
  new NamedEntityGraph(name = "Account.author", attributeNodes = Array(
    new NamedAttributeNode("id"), new NamedAttributeNode("version"), new NamedAttributeNode("displayName")
  ))
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

  @Size(min = 2, max = 80)
  @Column(name = "display_name", length = 80)
  @SchemaId("c81a429f") @JsonbProperty
  var displayName: String = null

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
    val displayName: SingularProperty[Account, String] = reference(_.displayName, classOf[AuthorEditRule])
    val locked: SingularProperty[Account, Boolean] = reference(_.locked)
  }

  def byEmail(email: String)(using manager: EntityManager): Option[Account] = {
    val builder = manager.getCriteriaBuilder
    val query = builder.createQuery(classOf[Account])
    val account = query.from(classOf[Account])
    query.select(account).where(Seq(builder.equal(account.get(schema.email), email))*)
    Option(manager.createQuery(query).getSingleResultOrNull)
  }
}
