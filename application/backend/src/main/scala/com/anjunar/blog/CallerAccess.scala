package com.anjunar.blog

import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.security.enterprise.SecurityContext

import scala.compiletime.uninitialized

@RequestScoped
class CallerAccess {
  @Inject var security: SecurityContext = uninitialized

  def authenticated: Boolean = security.getCallerPrincipal != null
  def hasRole(role: String): Boolean = authenticated && security.isCallerInRole(role)
  def administrator: Boolean = hasRole("ADMIN")
}
