package com.anjunar.blog.frontend

import org.scalajs.dom
import ui.core.async.AsyncRenderContext
import ui.core.component.Runtime
import ui.core.render.{DomCursor, HydratingCursor}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.scalajs.js.JSConverters.*

object Main {
  @JSExportTopLevel("render")
  def render(url: String): js.Promise[js.Object] = {
    val document = new BlogDocument(url)
    Runtime.renderToStringAsync(cursor => Runtime.mount(document, cursor), timeoutMs = 10000).map { html =>
      js.Dynamic.literal(html = ("<!doctype html>" + html), status = document.responseStatus)
    }.toJSPromise
  }

  private var booted: Option[js.Promise[Unit]] = None

  @JSExportTopLevel("boot")
  def boot(): js.Promise[Unit] = booted.getOrElse {
    val result = try start().toJSPromise catch { case NonFatal(error) => Future.failed[Unit](error).toJSPromise }
    booted = Some(result)
    result
  }

  private def start(): Future[Unit] = {
    val root = dom.document.getElementById("app")
    require(root != null, "The page must contain an element with id='app'")
    val url = dom.window.location.pathname + dom.window.location.search
    val embedded = Option(dom.document.getElementById("application-state"))
    val actions = new BlogActions(() => dom.window.location.reload())

    def mountFresh(): Future[Unit] = {
      root.textContent = ""
      val async = new AsyncRenderContext()
      Runtime.mount(new BlogPage(new BlogService, actions), DomCursor.root(root, async))
      async.drain()
    }

    val result = embedded match {
      case None => mountFresh() // Account/editorial pages and the shell-only test configuration.
      case Some(node) =>
        val async = new AsyncRenderContext()
        var page: Option[BlogPage] = None
        val hydrated = try {
          val initial = InitialPageData.replay(node.getAttribute("data-state"), url)
          val cursor = HydratingCursor.root(root, async)
          val component = new BlogPage(new BlogService(initial), actions)
          page = Some(component)
          Runtime.mount(component, cursor)
          async.drain().map { _ =>
            cursor.completeHydration()
            initial.finish()
          }
        } catch { case NonFatal(error) => Future.failed(error) }
        hydrated.recoverWith { case NonFatal(error) =>
          async.cancel()
          page.filter(_.isBound).foreach(Runtime.unmount)
          dom.console.warn("Hydration failed; rebuilding the page.", error.getMessage)
          mountFresh()
        }
    }
    // The snapshot is bootstrap data. Removing it also releases the serialized response.
    result.andThen { case _ => embedded.foreach(_.remove()) }
  }
}
