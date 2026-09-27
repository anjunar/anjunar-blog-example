package com.anjunar.blog

import jakarta.annotation.security.PermitAll

import com.arjuna.ats.jta.{UserTransaction as NarayanaUserTransaction}
import io.undertow.servlet.handlers.ServletRequestContext
import jakarta.annotation.Priority
import jakarta.enterprise.context.RequestScoped
import jakarta.enterprise.inject.spi.{CDI, PassivationCapable}
import jakarta.inject.Inject
import jakarta.security.enterprise.{SecurityContext as JakartaSecurityContext}
import jakarta.security.enterprise.authentication.mechanism.http.HttpAuthenticationMechanismHandler
import jakarta.security.enterprise.identitystore.IdentityStoreHandler
import jakarta.servlet.http.HttpServletRequest
import jakarta.ws.rs.{GET, Path, Produces}
import jakarta.ws.rs.core.{Context, MediaType, SecurityContext as RestSecurityContext}
import jakarta.ws.rs.ext.{Provider, WriterInterceptor, WriterInterceptorContext}

import java.io.IOException
import java.security.Principal
import scala.compiletime.uninitialized

@PermitAll
@Path("/auth/probe")
@RequestScoped
class AuthenticationProbeResource {
  @Context var request: HttpServletRequest = uninitialized
  @Context var rest: RestSecurityContext = uninitialized
  @Inject var security: JakartaSecurityContext = uninitialized

  @GET
  @Produces(Array(MediaType.TEXT_PLAIN))
  def identity(): String = {
    def name(principal: Principal): String = Option(principal).map(_.getName).getOrElse("anonymous")
    def beanId(kind: Class[?]): String = {
      val manager = CDI.current().getBeanManager
      manager.resolve(manager.getBeans(kind)).asInstanceOf[PassivationCapable].getId
    }
    Seq(
      "servlet=" + name(request.getUserPrincipal),
      "rest=" + name(rest.getUserPrincipal),
      "jakarta=" + name(security.getCallerPrincipal),
      "servletAdmin=" + request.isUserInRole("ADMIN"),
      "restAdmin=" + rest.isUserInRole("ADMIN"),
      "jakartaAdmin=" + security.isCallerInRole("ADMIN"),
      "servletReader=" + request.isUserInRole("READER"),
      "restReader=" + rest.isUserInRole("READER"),
      "jakartaReader=" + security.isCallerInRole("READER"),
      "typedPrincipals=" + security.getPrincipalsByType(classOf[SessionPrincipal]).size(),
      "container=" + ServletRequestContext.requireCurrent().getExchange.getSecurityContext.getClass.getName,
      "storeHandler=" + beanId(classOf[IdentityStoreHandler]),
      "mechanismHandler=" + beanId(classOf[HttpAuthenticationMechanismHandler])
    ).mkString("\n")
  }
}

// Test-only: force failures after credentials have passed through Soteria.
@Provider
@Priority(6000)
@RequestScoped
class AuthenticationFailureProbe extends WriterInterceptor {
  private var applied = false
  @Context var request: HttpServletRequest = uninitialized

  override def aroundWriteTo(context: WriterInterceptorContext): Unit = {
    val failure = if (applied) null else request.getHeader("X-Test-Auth-Failure")
    applied = true
    failure match {
      case "writer" => throw new IOException("Intentional authentication response failure")
      case "commit" => NarayanaUserTransaction.userTransaction().setRollbackOnly()
      case _ => ()
    }
    context.proceed()
  }
}
