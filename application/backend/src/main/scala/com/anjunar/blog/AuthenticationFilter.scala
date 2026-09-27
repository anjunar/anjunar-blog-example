package com.anjunar.blog

import jakarta.annotation.Priority
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.servlet.http.HttpServletRequest
import jakarta.ws.rs.container.{ContainerRequestContext, ContainerRequestFilter, ContainerResponseContext, ContainerResponseFilter, ResourceInfo}
import jakarta.ws.rs.core.{Context, SecurityContext}
import jakarta.ws.rs.ext.Provider

import java.security.Principal
import scala.compiletime.uninitialized

@Provider
@RequestScoped
@Priority(2100)
class AuthenticationFilter extends ContainerRequestFilter with ContainerResponseFilter {
  @Inject var identity: SessionIdentity = uninitialized
  @Context var resource: ResourceInfo = uninitialized
  @Context var httpRequest: HttpServletRequest = uninitialized

  override def filter(request: ContainerRequestContext): Unit = {
    if (resource.getResourceClass != classOf[HealthResource]) {
      identity.resolve(httpRequest)
      if (resource.getResourceClass == classOf[AuthenticationResource] &&
          request.getMethod != "GET" && request.getMethod != "HEAD" && request.getMethod != "OPTIONS")
        identity.checkCsrf()
      val previous = request.getSecurityContext
      request.setSecurityContext(new SecurityContext {
        override def getUserPrincipal: Principal =
          identity.account.map(account => new Principal {
            override def getName: String = account.id.toString
          }).orNull
        override def isUserInRole(role: String): Boolean = identity.account.exists(_.role == role)
        override def isSecure: Boolean = previous.isSecure
        override def getAuthenticationScheme: String = "SESSION"
      })
    }
  }

  override def filter(request: ContainerRequestContext, response: ContainerResponseContext): Unit =
    if (request.getUriInfo.getRequestUri.getPath.startsWith("/service/auth/")) {
      response.getHeaders.putSingle("Cache-Control", "no-store")
      response.getHeaders.putSingle("Pragma", "no-cache")
      response.getHeaders.putSingle("X-Content-Type-Options", "nosniff")
    }
}
