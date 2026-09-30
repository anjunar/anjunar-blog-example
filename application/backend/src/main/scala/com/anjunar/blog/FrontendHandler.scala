package com.anjunar.blog

import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.server.handlers.resource.{PathResourceManager, ResourceHandler}
import io.undertow.util.{Headers, HttpString, Methods, StatusCodes}

import java.nio.file.{Files, Path}
import java.util.logging.{Level, Logger}
import scala.util.control.NonFatal

final class FrontendHandler(api: HttpHandler, assets: Path, renderer: Option[SsrRenderer] = None,
    publicOrigin: String = "http://127.0.0.1:8080") extends HttpHandler {
  private val log = Logger.getLogger(classOf[FrontendHandler].getName)
  private val robots = new HttpString("X-Robots-Tag")
  private lazy val files = new ResourceHandler(new PathResourceManager(assets.toAbsolutePath.normalize(), 1024L))
    .setDirectoryListingEnabled(false)
  private val publicPaths = Set("/main.js", "/main.js.map", "/style.css", "/editor.css", "/material-icons.woff2", "/material-icons-LICENSE.txt")
  private val accountPages = Set("/account", "/register", "/confirm", "/forgot-password", "/reset-password")
  private val editorialPost = "/editorial/posts/[0-9a-fA-F-]{36}(?:/edit|/translations/de)?".r
  private val postPage = "/posts/[a-z0-9]+(?:-[a-z0-9]+)*".r
  private val discovery = Map("/sitemap.xml" -> "/service/discovery/sitemap",
    "/robots.txt" -> "/service/discovery/robots",
    "/en/feed.xml" -> "/service/discovery/feed/en", "/de/feed.xml" -> "/service/discovery/feed/de")

  override def handleRequest(exchange: HttpServerExchange): Unit = {
    val path = exchange.getRequestPath
    val localized = path == "/en" || path.startsWith("/en/") || path == "/de" || path.startsWith("/de/")
    val route = if (localized) path.drop(3) match {
      case "" => "/"
      case value => value
    } else path
    val editorial = localized && (route == "/editorial" || route == "/editorial/new" ||
      route == "/editorial/relationships" || editorialPost.matches(route))
    val privatePage = localized && (editorial || accountPages.contains(route))
    val redirect = if (path == "/" || path == "/index.html") Some("/en")
      else if (path == "/feed.xml") Some("/en/feed.xml")
      else if (postPage.matches(path)) Some("/en" + path)
      else if (localized && path.endsWith("/") && (route == "/" || postPage.matches(route.stripSuffix("/"))))
        Some(path.stripSuffix("/"))
      else None
    val known = localized || publicPaths.contains(path) || discovery.contains(path) || redirect.nonEmpty
    if (path == "/service" || path.startsWith("/service/")) api.handleRequest(exchange)
    else if (!known) {
      exchange.setStatusCode(StatusCodes.NOT_FOUND)
      exchange.getResponseHeaders.put(robots, "noindex")
      exchange.endExchange()
    } else if (exchange.getRequestMethod != Methods.GET && exchange.getRequestMethod != Methods.HEAD) {
      exchange.setStatusCode(StatusCodes.METHOD_NOT_ALLOWED)
      exchange.getResponseHeaders.put(Headers.ALLOW, "GET, HEAD")
      exchange.endExchange()
    } else if (redirect.nonEmpty) {
      exchange.setStatusCode(308)
      val query = exchange.getQueryString
      exchange.getResponseHeaders.put(Headers.LOCATION, redirect.get + (if (query.isEmpty) "" else s"?$query"))
      exchange.endExchange()
    } else if (discovery.contains(path)) {
      // Fixed aliases enter the normal servlet/CDI/transaction boundary.
      val target = discovery(path)
      exchange.setRequestURI(target)
      exchange.setRequestPath(target)
      exchange.setRelativePath(target)
      api.handleRequest(exchange)
    } else if (!Files.isDirectory(assets)) {
      exchange.setStatusCode(StatusCodes.SERVICE_UNAVAILABLE)
      exchange.getResponseHeaders.put(robots, "noindex")
      exchange.getResponseHeaders.put(Headers.CONTENT_TYPE, "text/plain; charset=UTF-8")
      if (exchange.getRequestMethod == Methods.HEAD) exchange.endExchange()
      else exchange.getResponseSender.send("Frontend assets are unavailable.\n")
    } else if (localized && !privatePage && renderer.nonEmpty) {
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
        if (rendered.status >= 400) exchange.getResponseHeaders.put(robots, "noindex")
        exchange.getResponseHeaders.put(Headers.CONTENT_TYPE, "text/html; charset=UTF-8")
        exchange.getResponseHeaders.put(Headers.CACHE_CONTROL, "no-store")
        exchange.getResponseHeaders.put(Headers.REFERRER_POLICY, "no-referrer")
        if (exchange.getRequestMethod == Methods.HEAD) exchange.endExchange()
        else exchange.getResponseSender.send(rendered.html)
      }
    } else if (localized) {
      val errorPage = Set("/sign-in-required", "/forbidden", "/bad-request", "/not-found", "/unavailable").contains(route)
      if (!privatePage && route != "/" && !postPage.matches(route) && !errorPage) {
        exchange.setStatusCode(StatusCodes.NOT_FOUND)
        exchange.getResponseHeaders.put(robots, "noindex")
        exchange.endExchange()
      } else if (exchange.isInIoThread) exchange.dispatch(this)
      else {
        exchange.getResponseHeaders.put(Headers.CONTENT_TYPE, "text/html; charset=UTF-8")
        exchange.getResponseHeaders.put(Headers.CACHE_CONTROL, if (privatePage) "no-store" else "no-cache")
        exchange.getResponseHeaders.put(Headers.REFERRER_POLICY, "no-referrer")
        exchange.getResponseHeaders.put(robots, "noindex")
        if (exchange.getRequestMethod == Methods.HEAD) exchange.endExchange()
        else exchange.getResponseSender.send(Files.readString(assets.resolve("index.html"))
          .replace("<!--public-origin-->", s"""<meta name="application-origin" content="$publicOrigin">"""))
      }
    } else files.handleRequest(exchange)
  }
}
