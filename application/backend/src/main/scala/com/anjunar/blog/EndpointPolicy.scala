package com.anjunar.blog

import jakarta.annotation.security.{DenyAll, PermitAll, RolesAllowed}
import jakarta.ws.rs.{ForbiddenException, NotAuthorizedException}

import java.lang.reflect.{AnnotatedElement, Method}

enum EndpointPolicy {
  case Public, Denied
  case Roles(names: Set[String])

  def allows(hasRole: String => Boolean): Boolean = this match {
    case Public => true
    case Denied => false
    case Roles(names) => names.exists(hasRole)
  }

  def requireAccess(access: CallerAccess): Unit =
    if (!allows(access.hasRole)) this match {
      case Roles(_) if !access.authenticated => throw new NotAuthorizedException("Session")
      case _ => throw new ForbiddenException()
    }
}

object EndpointPolicy {
  // An unannotated endpoint is closed. A method declaration overrides its resource class.
  def of(method: Method, resource: Class[?]): EndpointPolicy =
    declared(method).orElse(declared(resource)).getOrElse(EndpointPolicy.Denied)

  private def declared(element: AnnotatedElement): Option[EndpointPolicy] = {
    val policies = Seq(
      Option(element.getAnnotation(classOf[PermitAll])).map(_ => EndpointPolicy.Public),
      Option(element.getAnnotation(classOf[DenyAll])).map(_ => EndpointPolicy.Denied),
      Option(element.getAnnotation(classOf[RolesAllowed])).map(value => EndpointPolicy.Roles(value.value().toSet))
    ).flatten
    require(policies.size <= 1, "Declare one access policy per class or method")
    policies.headOption
  }
}
