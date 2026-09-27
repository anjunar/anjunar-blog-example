package com.anjunar.blog

import io.undertow.servlet.handlers.ServletRequestContext
import jakarta.servlet.http.HttpServletRequest
import org.glassfish.soteria.authorization.spi.CallerDetailsResolver

import java.security.Principal
import java.util.{Objects, Set}
import scala.jdk.CollectionConverters.*

/** Soteria's caller SPI reads the identity already established in Undertow. */
class SoteriaCallerDetails extends CallerDetailsResolver {
  private def request: Option[HttpServletRequest] =
    Option(ServletRequestContext.current()).map(_.getServletRequest.asInstanceOf[HttpServletRequest])

  override def getCallerPrincipal: Principal = request.map(_.getUserPrincipal).orNull

  override def getPrincipalsByType[T <: Principal](kind: Class[T]): Set[T] = {
    Objects.requireNonNull(kind)
    Option(getCallerPrincipal).filter(kind.isInstance).map(value => Set.of(kind.cast(value))).getOrElse(Set.of())
  }

  override def isCallerInRole(role: String): Boolean = request.exists(_.isUserInRole(role))

  override def getAllDeclaredCallerRoles: Set[String] =
    Set.of("ADMIN", "READER").asScala.filter(isCallerInRole).toSet.asJava
}
