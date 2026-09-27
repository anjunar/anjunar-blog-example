package com.anjunar.blog

import jakarta.annotation.Priority
import jakarta.enterprise.context.RequestScoped
import jakarta.servlet.http.HttpServletRequest
import jakarta.ws.rs.container.{ContainerRequestContext, ContainerRequestFilter, ContainerResponseContext, ContainerResponseFilter, ResourceInfo}
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.ext.Provider

import scala.compiletime.uninitialized

@Provider
@RequestScoped
@Priority(2100)
class AuthenticationFilter extends ContainerRequestFilter with ContainerResponseFilter {
  @Context var resource: ResourceInfo = uninitialized
  @Context var httpRequest: HttpServletRequest = uninitialized

  override def filter(request: ContainerRequestContext): Unit =
    if (resource.getResourceClass != classOf[HealthResource])
      SoteriaIntegration.resolve(httpRequest)

  override def filter(request: ContainerRequestContext, response: ContainerResponseContext): Unit = {
    // Even public post envelopes can contain caller-specific editorial links.
    response.getHeaders.putSingle("Cache-Control", "no-store")
    response.getHeaders.putSingle("Pragma", "no-cache")
    response.getHeaders.putSingle("X-Content-Type-Options", "nosniff")
  }
}