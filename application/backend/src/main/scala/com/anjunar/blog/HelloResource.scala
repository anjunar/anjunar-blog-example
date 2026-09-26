package com.anjunar.blog

import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.ws.rs.{GET, Path, Produces}

import scala.compiletime.uninitialized

@Path("/hello")
@RequestScoped
class HelloResource {

  @Inject
  var greeting: GreetingService = uninitialized

  @GET
  @Produces(Array("text/plain;charset=UTF-8"))
  def hello(): String = greeting.message

}
