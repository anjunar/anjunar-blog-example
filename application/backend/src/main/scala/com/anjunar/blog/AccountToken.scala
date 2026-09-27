package com.anjunar.blog

import com.anjunar.hibernateddl.hibernate.annotation.SchemaId
import jakarta.persistence.{Access, AccessType, Column, Entity, Id, Table, UniqueConstraint}
import jakarta.validation.constraints.{Email, NotBlank, Pattern}

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat

@Entity
@Access(AccessType.FIELD)
@SchemaId("bc12e601")
@Table(name = "blog_account_token", schema = "public",
  uniqueConstraints = Array(new UniqueConstraint(name = "uq_account_token_email_purpose",
    columnNames = Array("email", "purpose"))))
class AccountToken {
  @Id
  @Column(length = 64, nullable = false)
  @SchemaId("bc12e602")
  var digest: String = ""

  @Email
  @NotBlank
  @Column(length = 254, nullable = false)
  @SchemaId("bc12e603")
  var email: String = ""

  @Pattern(regexp = "REGISTER|RESET")
  @Column(length = 16, nullable = false)
  @SchemaId("bc12e604")
  var purpose: String = ""

  @Column(name = "expires_at", nullable = false)
  @SchemaId("bc12e605")
  var expiresAt: Instant = null

  @Column(name = "authentication_version", nullable = false)
  @SchemaId("bc12e606")
  var authenticationVersion: Long = -1L
}

object AccountToken {
  val register = "REGISTER"
  val reset = "RESET"
  val lifetimeSeconds = 30 * 60L

  def digest(token: String): String =
    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(UTF_8)))

  def wellFormed(token: String): Boolean =
    token != null && token.matches("[A-Za-z0-9_-]{43}")
}
