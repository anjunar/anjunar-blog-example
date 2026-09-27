package com.anjunar.blog

import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.server.handlers.resource.{PathResourceManager, ResourceHandler}
import io.undertow.util.{Headers, Methods, StatusCodes}

import java.nio.file.{Files, Path}

final class FrontendHandler(api: HttpHandler, assets: Path) extends HttpHandler {
  private lazy val files = new ResourceHandler(new PathResourceManager(assets.toAbsolutePath.normalize(), 1024L))
    .setDirectoryListingEnabled(false)
    .setWelcomeFiles("index.html")
  private val publicPaths = Set("/", "/index.html", "/main.js", "/main.js.map", "/style.css")

  override def handleRequest(exchange: HttpServerExchange): Unit = {
    val path = exchange.getRequestPath
    if (path == "/service" || path.startsWith("/service/")) api.handleRequest(exchange)
    else if (!publicPaths.contains(path)) {
      exchange.setStatusCode(StatusCodes.NOT_FOUND)
      exchange.endExchange()
    } else if (exchange.getRequestMethod != Methods.GET && exchange.getRequestMethod != Methods.HEAD) {
      exchange.setStatusCode(StatusCodes.METHOD_NOT_ALLOWED)
      exchange.getResponseHeaders.put(Headers.ALLOW, "GET, HEAD")
      exchange.endExchange()
    } else if (!Files.isDirectory(assets)) {
      exchange.setStatusCode(StatusCodes.SERVICE_UNAVAILABLE)
      exchange.getResponseHeaders.put(Headers.CONTENT_TYPE, "text/plain; charset=UTF-8")
      exchange.getResponseSender.send("Frontend assets are unavailable.\n")
    } else {
      // Development assets have stable names; a rebuild must be visible after reload.
      exchange.getResponseHeaders.put(Headers.CACHE_CONTROL, "no-cache")
      files.handleRequest(exchange)
    }
  }
}
