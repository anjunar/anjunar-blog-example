package com.anjunar.blog

import jakarta.annotation.security.PermitAll
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.servlet.ServletContext
import jakarta.ws.rs.{GET, Path, PathParam, Produces}
import jakarta.ws.rs.core.{Context, Response}

import java.nio.charset.StandardCharsets.UTF_8
import scala.compiletime.uninitialized

@PermitAll
@RequestScoped
@Path("/discovery")
class DiscoveryResource {
  @Inject var pages: PublishedPages = uninitialized
  @Context var servlet: ServletContext = uninitialized

  private def origin: String = servlet.getAttribute(PublicSite.OriginAttribute).asInstanceOf[String]

  private def response(body: String, mediaType: String): Response =
    Response.ok(body.getBytes(UTF_8), mediaType).header("Cache-Control", "no-store")
      .header("X-Content-Type-Options", "nosniff").build()

  @GET @Path("/sitemap") @Produces(Array("application/xml"))
  def sitemap(): Response = response(DiscoveryXml.sitemap(origin, pages.sitemap()), "application/xml; charset=UTF-8")

  @GET @Path("/feed/{locale}") @Produces(Array("application/atom+xml"))
  def feed(@PathParam("locale") raw: String): Response = {
    val locale = PostLocale.parse(raw)
    response(DiscoveryXml.feed(origin, locale, pages.feed(locale)), "application/atom+xml; charset=UTF-8")
  }

  @GET @Path("/robots") @Produces(Array("text/plain"))
  def robots(): Response = response(s"User-agent: *\nAllow: /\nSitemap: $origin/sitemap.xml\n", "text/plain; charset=UTF-8")
}
