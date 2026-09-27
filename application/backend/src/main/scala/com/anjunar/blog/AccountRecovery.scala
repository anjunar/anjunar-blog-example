package com.anjunar.blog

import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.persistence.{EntityManager, LockModeType}
import jakarta.ws.rs.BadRequestException

import java.lang
import java.time.Instant
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

@RequestScoped
class AccountRecovery {
  @Inject var manager: EntityManager = uninitialized
  @Inject var transaction: RequestTransaction = uninitialized
  @Inject var mail: AccountMail = uninitialized
  @Inject var hashing: LoginLimiter = uninitialized

  def requestLink(email: String, purpose: String): Unit = {
    val config = mail.configuration()
    lockEmail(email)
    val account = Account.byEmail(email)(using manager)
    val eligible = if (purpose == AccountToken.register) account.isEmpty else account.exists(!_.locked)
    if (eligible) {
      // Expired entries are housekeeping only; validity is always checked when consuming a link.
      manager.createQuery("delete from AccountToken t where t.email = :email and t.expiresAt <= :now")
        .setParameter("email", email).setParameter("now", Instant.now()).executeUpdate()
      removeTokens(email, purpose)
      manager.flush()
      val raw = SessionIdentity.newToken()
      val token = new AccountToken()
      token.digest = AccountToken.digest(raw)
      token.email = email
      token.purpose = purpose
      token.expiresAt = Instant.now().plusSeconds(AccountToken.lifetimeSeconds)
      token.authenticationVersion = account.map(_.authenticationVersion).getOrElse(-1L)
      manager.persist(token)
      // Only immutable values cross into the mail worker; no CDI request or EntityManager.
      transaction.afterCommit(() => mail.enqueue(config, email, purpose, raw))
    }
  }

  def complete(input: TokenPasswordRequest, purpose: String): Unit = {
    val digest = AccountToken.digest(input.token)
    val email = Option(manager.createQuery(
      "select t.email from AccountToken t where t.digest = :digest and t.purpose = :purpose", classOf[String])
      .setParameter("digest", digest).setParameter("purpose", purpose).getSingleResultOrNull)
      .getOrElse(invalid())
    lockEmail(email)
    val token = manager.find(classOf[AccountToken], digest)
    if (token == null || token.purpose != purpose || !Instant.now().isBefore(token.expiresAt)) invalid()
    val found = Account.byEmail(email)(using manager)
    if (purpose == AccountToken.register) {
      if (found.nonEmpty) invalid()
      val account = new Account()
      account.email = email
      account.role = "READER"
      account.passwordHash = hashing.withHashing(PasswordHash.create(input.password))
      manager.persist(account)
    } else {
      val account = found.getOrElse(invalid())
      manager.lock(account, LockModeType.PESSIMISTIC_WRITE)
      manager.refresh(account)
      if (account.locked || account.authenticationVersion != token.authenticationVersion) invalid()
      account.passwordHash = hashing.withHashing(PasswordHash.create(input.password))
      account.authenticationVersion = Math.addExact(account.authenticationVersion, 1L)
    }
    removeTokens(email, purpose)
  }

  private def removeTokens(email: String, purpose: String): Unit =
    manager.createQuery("select t from AccountToken t where t.email = :email and t.purpose = :purpose", classOf[AccountToken])
      .setParameter("email", email).setParameter("purpose", purpose).getResultList.asScala.foreach(manager.remove)

  private def lockEmail(email: String): Unit =
    manager.createNativeQuery(
      "select 1 from pg_advisory_xact_lock(hashtextextended(:email, 12012))", classOf[lang.Integer])
      .setParameter("email", email).getSingleResult

  private def invalid(): Nothing = throw new BadRequestException("The link is invalid or has expired")
}
