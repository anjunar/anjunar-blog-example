package com.anjunar.blog

import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.ws.rs.{GET, Path, Produces}

import scala.compiletime.uninitialized

@Path("/health/ready")
@RequestScoped
class DatabaseHealthResource {
  @Inject
  var entityManager: EntityManager = uninitialized

  @GET
  @Produces(Array("text/plain;charset=UTF-8"))
  def ready(): String = {
    entityManager.createNativeQuery("select 1", classOf[java.lang.Integer]).getSingleResult
    "UP\n"
  }
}
