package com.anjunar.blog

import jakarta.annotation.security.PermitAll

import jakarta.enterprise.context.RequestScoped
import jakarta.ws.rs.{GET, Path, Produces}

@PermitAll
@Path("/health/live")
@RequestScoped
class HealthResource {

  @GET
  @Produces(Array("text/plain;charset=UTF-8"))
  def live(): String = "UP\n"

}
