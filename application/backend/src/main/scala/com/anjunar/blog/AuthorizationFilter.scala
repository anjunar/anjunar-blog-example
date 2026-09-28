package com.anjunar.blog

import jakarta.annotation.Priority
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.ws.rs.container.{ContainerRequestContext, ContainerRequestFilter, ResourceInfo}
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.ext.Provider

import scala.compiletime.uninitialized

@Provider
@RequestScoped
@Priority(2200)
class AuthorizationFilter extends ContainerRequestFilter {
  @Inject var access: CallerAccess = uninitialized
  @Inject var identity: SessionIdentity = uninitialized
  @Context var resource: ResourceInfo = uninitialized

  override def filter(request: ContainerRequestContext): Unit = {
    EndpointPolicy.of(resource.getResourceMethod, resource.getResourceClass).requireAccess(access)
    val writes = request.getMethod != "GET" && request.getMethod != "HEAD" && request.getMethod != "OPTIONS"
    val protectedResource = Set[Class[?]](classOf[AuthenticationResource],
      classOf[AccountRecoveryResource], classOf[EditorialPostsResource],
      classOf[EditorialAuthorsResource], classOf[EditorialTagsResource]).contains(resource.getResourceClass)
    if (writes && protectedResource) identity.checkCsrf()
  }
}
