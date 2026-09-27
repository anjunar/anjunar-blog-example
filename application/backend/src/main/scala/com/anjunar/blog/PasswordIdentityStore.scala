package com.anjunar.blog

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.security.enterprise.credential.{Credential, UsernamePasswordCredential}
import jakarta.security.enterprise.identitystore.{CredentialValidationResult, IdentityStore}

import java.time.Instant
import java.util.Set
import scala.compiletime.uninitialized

@ApplicationScoped
class PasswordIdentityStore extends IdentityStore {
  @Inject var manager: EntityManager = uninitialized
  @Inject var limiter: LoginLimiter = uninitialized

  override def validate(credential: Credential): CredentialValidationResult = credential match {
    case password: UsernamePasswordCredential =>
      val found = Account.byEmail(Account.canonicalEmail(password.getCaller))(using manager)
      val valid = limiter.withHashing {
        PasswordHash.verify(password.getPasswordAsString, found.map(_.passwordHash).getOrElse(PasswordHash.decoy))
      }
      found.filter(account => valid && !account.locked).map { account =>
        new CredentialValidationResult(
          SessionPrincipal(account.id, account.authenticationVersion, Instant.now()), Set.of(account.role))
      }.getOrElse(CredentialValidationResult.INVALID_RESULT)
    case _ => CredentialValidationResult.NOT_VALIDATED_RESULT
  }
}
