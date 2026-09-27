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
  private val accountPages = Set("/en/account", "/en/register", "/en/confirm", "/en/forgot-password", "/en/reset-password")
  private val postPage = "/en/posts/[a-z0-9]+(?:-[a-z0-9]+)*".r

  override def handleRequest(exchange: HttpServerExchange): Unit = {
    val path = exchange.getRequestPath
    val page = path == "/en" || path == "/en/" || accountPages.contains(path) || postPage.matches(path)
    if (path == "/service" || path.startsWith("/service/")) api.handleRequest(exchange)
    else if (!publicPaths.contains(path) && !page) {
      exchange.setStatusCode(StatusCodes.NOT_FOUND)
      exchange.endExchange()
    } else if (exchange.getRequestMethod != Methods.GET && exchange.getRequestMethod != Methods.HEAD) {
      exchange.setStatusCode(StatusCodes.METHOD_NOT_ALLOWED)
      exchange.getResponseHeaders.put(Headers.ALLOW, "GET, HEAD")
      exchange.endExchange()
    } else if (path == "/index.html") {
      exchange.setStatusCode(StatusCodes.TEMPORARY_REDIRECT)
      val query = exchange.getQueryString
      exchange.getResponseHeaders.put(Headers.LOCATION, if (query.isEmpty) "/" else s"/?$query")
      exchange.endExchange()
    } else if (!Files.isDirectory(assets)) {
      exchange.setStatusCode(StatusCodes.SERVICE_UNAVAILABLE)
      exchange.getResponseHeaders.put(Headers.CONTENT_TYPE, "text/plain; charset=UTF-8")
      exchange.getResponseSender.send("Frontend assets are unavailable.\n")
    } else {
      // Known browser routes load the shell; REST still decides whether a post exists.
      if (page) exchange.setRelativePath("/index.html")
      exchange.getResponseHeaders.put(Headers.CACHE_CONTROL, if (accountPages.contains(path)) "no-store" else "no-cache")
      exchange.getResponseHeaders.put(Headers.REFERRER_POLICY, "no-referrer")
      files.handleRequest(exchange)
    }
  }
}
