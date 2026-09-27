package com.anjunar.blog.frontend

import ui.router.{Route, RouteFailure, RouterConfig}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

final class BlogRoutes(service: BlogService, actions: BlogActions)(using ExecutionContext) {
  private val accounts = new AccountService()
  val routes: Seq[Route] = Seq(
    Route.view("/account") { context =>
      accounts.session(context.signal)
        .map(state => new AccountPage(Some(state), accounts, () => actions.retry()))
        .recover { case NonFatal(_) => new AccountPage(None, accounts, () => actions.retry()) }
    },
    Route.view("/") { context =>
      val offset = context.queryParams.get("offset").getOrElse("0").toIntOption
        .filter(_ >= 0).getOrElse(throw new HttpFailure(400))
      service.list(offset, context.signal)
        .map(table => new PostListPage(table, offset, service.pageSize, actions))
    },
    Route.view("/posts/:slug") { context =>
      service.detail(context.pathParams("slug"), context.signal).map(new PostPage(_))
    },
    Route.error("/bad-request", status = 400) { _ =>
      Future.successful(new ErrorPage(400, actions))
    },
    Route.error("/not-found", status = 404) { _ =>
      Future.successful(new ErrorPage(404, actions))
    },
    Route.error("/unavailable", status = 503) { _ =>
      Future.successful(new ErrorPage(503, actions))
    }
  )

  val config: RouterConfig = RouterConfig(
    loading = _ => new LoadingPage,
    onFailure = {
      case _: RouteFailure.NotMatched => Some("/not-found")
      case RouteFailure.LoadFailed(error: HttpFailure, _) if error.status == 404 =>
        Some("/not-found")
      case RouteFailure.LoadFailed(error: HttpFailure, _) if error.status == 400 =>
        Some("/bad-request")
      case _ => Some("/unavailable")
    }
  )
}
