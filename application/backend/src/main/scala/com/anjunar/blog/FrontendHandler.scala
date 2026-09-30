package com.anjunar.blog

import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.server.handlers.resource.{PathResourceManager, ResourceHandler}
import io.undertow.util.{Headers, Methods, StatusCodes}

import java.nio.file.{Files, Path}
import java.util.logging.{Level, Logger}
import scala.util.control.NonFatal

final class FrontendHandler(api: HttpHandler, assets: Path, renderer: Option[SsrRenderer] = None) extends HttpHandler {
  private val log = Logger.getLogger(classOf[FrontendHandler].getName)
  private lazy val files = new ResourceHandler(new PathResourceManager(assets.toAbsolutePath.normalize(), 1024L))
    .setDirectoryListingEnabled(false)
    .setWelcomeFiles("index.html")
  private val publicPaths = Set("/", "/index.html", "/main.js", "/main.js.map", "/style.css", "/editor.css", "/material-icons.woff2", "/material-icons-LICENSE.txt")
  private val accountPages = Set("/account", "/register", "/confirm", "/forgot-password", "/reset-password")
  private val editorialPost = "/editorial/posts/[0-9a-fA-F-]{36}(?:/edit|/translations/de)?".r
  private val postPage = "/posts/[a-z0-9]+(?:-[a-z0-9]+)*".r

  override def handleRequest(exchange: HttpServerExchange): Unit = {
    val path = exchange.getRequestPath
    val localized = path == "/en" || path.startsWith("/en/") || path == "/de" || path.startsWith("/de/")
    val route = if (localized) path.drop(3) match {
      case "" => "/"
      case value => value
    } else path
    val editorial = localized && (route == "/editorial" || route == "/editorial/new" ||
      route == "/editorial/relationships" || editorialPost.matches(route))
    val errorPage = Set("/sign-in-required", "/forbidden", "/bad-request", "/not-found", "/unavailable").contains(route)
    val page = localized && (editorial || route == "/" || accountPages.contains(route) || postPage.matches(route) || errorPage)
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
    } else if (renderer.nonEmpty && (path == "/" || (localized && (route == "/" || postPage.matches(route) || errorPage)))) {
      if (exchange.isInIoThread) exchange.dispatch(this)
      else {
        val query = exchange.getQueryString
        val url = path + (if (query.isEmpty) "" else "?" + query)
        val rendered = try renderer.get.render(url) catch {
          case NonFatal(error) =>
            log.log(Level.WARNING, "Public page rendering failed", error)
            RenderedPage("The page is temporarily unavailable.\n", 503)
        }
        exchange.setStatusCode(rendered.status)
        exchange.getResponseHeaders.put(Headers.CONTENT_TYPE, "text/html; charset=UTF-8")
        exchange.getResponseHeaders.put(Headers.CACHE_CONTROL, "no-store")
        exchange.getResponseHeaders.put(Headers.REFERRER_POLICY, "no-referrer")
        if (exchange.getRequestMethod == Methods.HEAD) exchange.endExchange()
        else exchange.getResponseSender.send(rendered.html)
      }
    } else {
      // Account/editorial routes remain browser applications in this chapter.
      if (page) exchange.setRelativePath("/index.html")
      exchange.getResponseHeaders.put(Headers.CACHE_CONTROL, if (accountPages.contains(route) || editorial) "no-store" else "no-cache")
      exchange.getResponseHeaders.put(Headers.REFERRER_POLICY, "no-referrer")
      files.handleRequest(exchange)
    }
  }
}
