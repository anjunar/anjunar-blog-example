package com.anjunar.blog.frontend

import ui.router.{Route, RouteFailure, RouterConfig}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

final class BlogRoutes(service: BlogService, actions: BlogActions)(using ExecutionContext) {
  private val accounts = new AccountService()
  private val editorial = new EditorialService(accounts)
  private val catalog = new CatalogService(accounts)
  private val media = new MediaService(accounts)
  val routes: Seq[Route] = Seq("register", "confirm", "forgot-password", "reset-password").map { endpoint =>
    Route.view(s"/$endpoint") { _ =>
      Future.successful(new RecoveryPage(endpoint, AccountLink.takeToken(), accounts))
    }
  } ++ Seq(
    Route.view("/account") { context =>
      accounts.session(context.signal)
        .map(state => new AccountPage(Some(state), accounts, () => actions.retry()))
        .recover { case NonFatal(_) => new AccountPage(None, accounts, () => actions.retry()) }
    },
    Route.view("/editorial") { context =>
      val search = PostSearch.parse(context.queryParams.get, editorial = true)
      editorial.list(search, context.signal).map(new EditorialListPage(_, search))
    },
    Route.view("/editorial/relationships") { context =>
      catalog.load(context.signal).map(value =>
        new RelationshipPage(value.getOrElse(throw new HttpFailure(403)), catalog))
    },
    Route.view("/editorial/new") { context =>
      editorial.newPost(context.signal).zip(catalog.load(context.signal))
        .map((value, options) => new PostEditorPage(value, editorial, options, catalog, media))
    },
    Route.view("/editorial/posts/:id/edit") { context =>
      editorial.detail(context.pathParams("id"), context.signal).zip(catalog.load(context.signal)).map { (value, options) =>
        val link = value.links.find(_.rel == "update").getOrElse(throw new HttpFailure(403))
        link.path("PATCH")
        new PostEditorPage(value, editorial, options, catalog, media)
      }
    },
    Route.view("/editorial/posts/:id") { context =>
      editorial.detail(context.pathParams("id"), context.signal)
        .map(value => new EditorialPostPage(value, editorial, () => actions.retry()))
    },
    Route.error("/sign-in-required", status = 401) { _ =>
      Future.successful(new ErrorPage(401, actions))
    },
    Route.error("/forbidden", status = 403) { _ =>
      Future.successful(new ErrorPage(403, actions))
    },
    Route.view("/") { context =>
      val search = PostSearch.parse(context.queryParams.get, editorial = false)
      service.list(search, context.signal)
        .map(table => new PostListPage(table, search, actions))
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
      case RouteFailure.LoadFailed(error: HttpFailure, _) if error.status == 401 =>
        Some("/sign-in-required")
      case RouteFailure.LoadFailed(error: HttpFailure, _) if error.status == 403 =>
        Some("/forbidden")
      case _: RouteFailure.NotMatched => Some("/not-found")
      case RouteFailure.LoadFailed(error: HttpFailure, _) if error.status == 404 =>
        Some("/not-found")
      case RouteFailure.LoadFailed(error: HttpFailure, _) if error.status == 400 =>
        Some("/bad-request")
      case _ => Some("/unavailable")
    }
  )
}
