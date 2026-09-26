package com.anjunar.blog

import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.{ApplicationScoped, RequestScoped}
import jakarta.inject.Inject
import jakarta.ws.rs.{GET, Path, Produces}
import jakarta.ws.rs.container.{ContainerRequestContext, ContainerResponseContext, ContainerResponseFilter}
import jakarta.ws.rs.ext.Provider

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.compiletime.uninitialized

object ScopeEvents {
  val destroyedRequests = ConcurrentHashMap.newKeySet[String]()
  val destroyedApplications = ConcurrentHashMap.newKeySet[String]()

  def clear(): Unit = {
    destroyedRequests.clear()
    destroyedApplications.clear()
  }
}

@RequestScoped
class RequestProbe {
  private val token = UUID.randomUUID().toString
  def id: String = token

  @PreDestroy
  def destroy(): Unit = {
    ScopeEvents.destroyedRequests.add(token)
  }
}

@ApplicationScoped
class ApplicationProbe {
  private val token = UUID.randomUUID().toString
  def id: String = token

  @PreDestroy
  def destroy(): Unit = {
    ScopeEvents.destroyedApplications.add(token)
  }
}

@Path("/_test/scopes")
@RequestScoped
class ScopeProbeResource {
  @Inject
  var request: RequestProbe = uninitialized

  @Inject
  var application: ApplicationProbe = uninitialized

  @GET
  @Produces(Array("text/plain"))
  def read(): String = s"${request.id}|${application.id}"
}

@Provider
@RequestScoped
class ScopeProbeFilter extends ContainerResponseFilter {
  @Inject
  var request: RequestProbe = uninitialized

  override def filter(input: ContainerRequestContext, output: ContainerResponseContext): Unit =
    output.getHeaders.putSingle("X-Probe-Request", request.id)
}
