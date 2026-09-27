package com.anjunar.blog

import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.security.enterprise.CallerPrincipal
import jakarta.servlet.http.HttpServletRequest
import jakarta.ws.rs.{NotAuthorizedException, WebApplicationException}
import jakarta.ws.rs.core.Response

import java.nio.charset.StandardCharsets.UTF_8
import java.security.{MessageDigest, SecureRandom}
import java.time.Instant
import java.util.{Base64, UUID}
import scala.compiletime.uninitialized

final case class SessionPrincipal(accountId: UUID, authenticationVersion: Long, issuedAt: Instant)
    extends CallerPrincipal(accountId.toString) {
  override def getName: String = accountId.toString

  def active(account: Account, now: Instant): Boolean =
    account != null && !account.locked && account.authenticationVersion == authenticationVersion &&
      !now.isBefore(issuedAt) && now.isBefore(issuedAt.plusSeconds(SecurityConfig.absoluteSeconds))
}

object SessionIdentity {
  private val random = new SecureRandom()
  val principalKey = "blog.principal"
  val csrfKey = "blog.csrf"

  def newToken(): String = {
    val bytes = new Array[Byte](32)
    random.nextBytes(bytes)
    Base64.getUrlEncoder.withoutPadding().encodeToString(bytes)
  }
}

@RequestScoped
class SessionIdentity {
  @Inject var manager: EntityManager = uninitialized
  private var request: HttpServletRequest = null

  private var resolved: Option[Account] = None
  private var authenticated: SessionPrincipal = null
  def account: Option[Account] = resolved
  def principal: SessionPrincipal = {
    requireAccount()
    authenticated
  }

  def resolve(httpRequest: HttpServletRequest): Option[SessionPrincipal] = {
    request = httpRequest
    val session = request.getSession(false)
    Option(session).flatMap(value => Option(value.getAttribute(SessionIdentity.principalKey))) match {
      case Some(principal: SessionPrincipal) =>
        val value = manager.find(classOf[Account], principal.accountId)
        if (principal.active(value, Instant.now())) {
          resolved = Some(value)
          authenticated = principal
          Some(principal)
        } else {
          session.invalidate()
          None
        }
      case _ => None
    }
  }

  def accept(httpRequest: HttpServletRequest, principal: SessionPrincipal): Unit = {
    request = httpRequest
    val value = manager.find(classOf[Account], principal.accountId)
    if (!principal.active(value, Instant.now())) throw new NotAuthorizedException("Session")
    resolved = Some(value)
    authenticated = principal
  }

  def clear(httpRequest: HttpServletRequest): Unit = {
    Option(httpRequest.getSession(false)).foreach(_.removeAttribute(SessionIdentity.principalKey))
    resolved = None
    authenticated = null
  }

  def requireAccount(): Account = resolved.getOrElse(throw new NotAuthorizedException("Session"))

  def csrfToken(): String = {
    val session = request.getSession(true)
    session.synchronized {
      Option(session.getAttribute(SessionIdentity.csrfKey)).map(_.toString).getOrElse {
        val token = SessionIdentity.newToken()
        session.setAttribute(SessionIdentity.csrfKey, token)
        token
      }
    }
  }

  def checkCsrf(): Unit = {
    val supplied = Option(request.getHeader("X-CSRF-Token")).getOrElse("")
    val expected = Option(request.getSession(false))
      .flatMap(session => Option(session.getAttribute(SessionIdentity.csrfKey))).map(_.toString)
    if (request.getHeader("Sec-Fetch-Site") == "cross-site" || supplied.length > 128 ||
        !expected.exists(token => MessageDigest.isEqual(token.getBytes(UTF_8), supplied.getBytes(UTF_8))))
      throw new WebApplicationException(Response.status(403).build())
  }

  def establish(principal: SessionPrincipal, token: String): Unit = {
    val session = request.getSession(true)
    request.changeSessionId()
    session.setAttribute(SessionIdentity.principalKey, principal)
    session.setAttribute(SessionIdentity.csrfKey, token)
  }

  def end(token: String): Unit = {
    request.logout()
    Option(request.getSession(false)).foreach(_.invalidate())
    val anonymous = request.getSession(true)
    anonymous.setAttribute(SessionIdentity.csrfKey, token)
  }
}
