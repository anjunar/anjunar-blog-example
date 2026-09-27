package com.anjunar.blog

import com.anjunar.json.mapper.PreparedChange
import jakarta.annotation.security.RolesAllowed
import jakarta.enterprise.context.RequestScoped
import jakarta.ws.rs.{Consumes, PATCH, Path, PathParam, Produces, WebApplicationException}
import jakarta.ws.rs.core.{MediaType, Response}

@RolesAllowed(Array("ADMIN"))
@RequestScoped
@Path("/_test/changes")
@Consumes(Array(MediaType.APPLICATION_JSON))
class PreparedChangeProbeResource {
  @PATCH @Path("/{id}")
  def reject(@PathParam("id") change: PreparedChange[BlogPost]): String = {
    assert(!change.isApplied)
    throw new WebApplicationException(Response.status(403)
      .header("X-Test-Original-Title", change.getEntity().title).build())
  }

  @PATCH @Path("/{id}/apply")
  @Produces(Array(MediaType.TEXT_PLAIN))
  def applyOnce(@PathParam("id") change: PreparedChange[BlogPost]): String = {
    val post = change.getEntity()
    assert(!change.isApplied)
    assert(change.applyChanges() eq post)
    assert(change.isApplied)
    try {
      change.applyChanges()
      throw new AssertionError("A second successful application must be rejected")
    } catch { case _: IllegalStateException => "single-use" }
  }
}
