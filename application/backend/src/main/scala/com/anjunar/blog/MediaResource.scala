package com.anjunar.blog

import com.anjunar.json.mapper.schema.Link
import jakarta.annotation.security.{PermitAll, RolesAllowed}
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.ws.rs.{Consumes, GET, HeaderParam, NotFoundException, POST, Path, PathParam, Produces}
import jakarta.ws.rs.core.{GenericEntity, MediaType, Response}

import java.io.InputStream
import java.util
import java.util.UUID
import scala.compiletime.uninitialized

@Path("/editorial/media")
@RolesAllowed(Array("ADMIN"))
@RequestScoped
class MediaUploadResource {
  @Inject var lifecycle: MediaLifecycle = uninitialized
  @Inject var manager: EntityManager = uninitialized

  @POST @Consumes(Array("image/jpeg", "image/png")) @Produces(Array(MediaType.APPLICATION_JSON))
  @EntityGraph("Media.detail")
  def upload(@HeaderParam("Content-Type") contentType: String,
      @HeaderParam("X-File-Name") name: String, input: InputStream): Response = {
    val media = lifecycle.create(ImageContent.read(input, contentType), name)
    val path = s"/service/media/${media.id}"
    val result = new Data(media, Schema.forGraph(Media.schema, manager.getEntityGraph("Media.detail")),
      util.List.of(new Link("self", path, "GET", "Media")))
    Response.status(201).header("Location", path).entity(new GenericEntity[Data[Media]](result) {}).build()
  }
}

@Path("/media")
@PermitAll
@RequestScoped
class MediaResource {
  @Inject var manager: EntityManager = uninitialized
  @Inject var lifecycle: MediaLifecycle = uninitialized

  @GET @Path("/{id}")
  @Produces(Array("image/jpeg", "image/png"))
  def image(@PathParam("id") rawId: String): Response = {
    val id = try UUID.fromString(rawId) catch { case _: IllegalArgumentException => throw new NotFoundException() }
    val media = manager.find(classOf[Media], id)
    if (media == null || (!lifecycle.referenced(media, publishedOnly = true) && !lifecycle.canUse(media)))
      throw new NotFoundException()
    Response.ok(media.data, media.contentType)
      .header("Content-Length", media.byteSize)
      .header("Content-Disposition", "inline")
      .header("Cache-Control", "no-store")
      .header("X-Content-Type-Options", "nosniff").build()
  }
}
