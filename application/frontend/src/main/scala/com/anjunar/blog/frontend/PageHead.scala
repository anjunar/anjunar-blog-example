package com.anjunar.blog.frontend

import org.scalajs.dom
import ui.core.component.AbstractComponent
import ui.core.di.Context
import ui.core.document.{BrowserHeadSink, DocumentHead, HeadEntry, HeadSink}
import ui.core.i18n.{I18nRuntime, i18n}
import ui.core.render.Cursor

/** The application hydrates #app, so its browser head sink lives outside that cursor. */
final class PageHead(val origin: String, registry: DocumentHead, sink: Option[HeadSink]) {
  def bind(entries: HeadEntry*)(using owner: AbstractComponent): Unit = {
    val handle = registry.handle()
    handle.set(entries*)
    synchronize()
    owner.addDisposable(() => {
      handle.dispose()
      synchronize()
    })
  }

  private def synchronize(): Unit =
    sink.foreach(_.update(registry.entries, registry.htmlAttributes))
}

object PageHead {
  private val context = Context.create[PageHead]("PageHead")

  def current(using AbstractComponent): Option[PageHead] = context.inject

  def provide(origin: String, cursor: Cursor)(using owner: AbstractComponent): PageHead = {
    val registry = DocumentHead.current.getOrElse(new DocumentHead)
    val sink = Option.when(cursor.isBrowser)(new BrowserHeadSink(dom.document.head, None))
    val head = new PageHead(origin, registry, sink)
    context.provide(head)
    head.bind(HeadEntry.title("Anjunar Journal"), HeadEntry.meta("robots", "noindex, follow"))
    head
  }

  def browserOrigin: String =
    Option(dom.document.querySelector("meta[name='application-origin']"))
      .map(_.getAttribute("content")).filter(value => value != null && value.nonEmpty)
      .getOrElse(dom.window.location.origin)

  private def feed(origin: String, locale: String): HeadEntry =
    HeadEntry("feed", "link", Seq("rel" -> "alternate", "type" -> "application/atom+xml",
      "title" -> s"Anjunar Journal ($locale)", "href" -> s"$origin/$locale/feed.xml"))

  private def social(title: String, description: String, url: String, kind: String): Seq[HeadEntry] = Seq(
    HeadEntry.title(s"$title — Anjunar Journal"),
    HeadEntry.meta("description", description),
    HeadEntry.property("og:title", title),
    HeadEntry.property("og:description", description),
    HeadEntry.property("og:type", kind),
    HeadEntry.property("og:url", url),
    HeadEntry.property("og:site_name", "Anjunar Journal"))

  def article(origin: String, post: BlogPost): Seq[HeadEntry] = {
    val translation = Option(post.translation.get)
    val title = translation.map(_.title.get).getOrElse(post.title.get)
    val summary = translation.map(_.summary.get).getOrElse(post.summary.get)
    val description = Option(summary).filter(_.nonEmpty).getOrElse(title)
    val locale = post.contentLocale.get
    val canonical = s"$origin/$locale/posts/${post.slug.get}"
    val languages = (Seq("en") ++ post.availableLocales.toSeq.filter(_ == "de")).distinct
    val alternates = languages.map(language =>
      HeadEntry.alternate(language, s"$origin/$language/posts/${post.slug.get}")) :+
      HeadEntry.alternate("x-default", s"$origin/en/posts/${post.slug.get}")
    val image = Option(post.coverImage.get).toSeq.flatMap(value => Seq(
      HeadEntry.property("og:image", origin + value.source),
      HeadEntry.property("og:image:alt", Option(post.coverAlt.get).getOrElse(""))))
    social(title, description, canonical, "article") ++ Seq(
      HeadEntry.link("canonical", canonical), HeadEntry.meta("robots", "index, follow"),
      HeadEntry.property("og:locale", if (locale == "de") "de_DE" else "en_US"), feed(origin, locale)) ++
      post.publishedAt.get.toSeq.map(value => HeadEntry.property("article:published_time", value)) ++ alternates ++ image
  }

  def list(origin: String, locale: String, search: PostSearch, emptyPage: Boolean,
      title: String, description: String): Seq[HeadEntry] = {
    val query = search.queryString()
    val canonical = s"$origin/$locale" + (if (query.isEmpty) "" else s"?$query")
    val defaultListing = !search.filtered && search.sort == "newest" && search.limit == 20
    val indexable = defaultListing && !emptyPage
    social(title, description, canonical, "website") ++ Seq(
      HeadEntry.link("canonical", canonical), HeadEntry.meta("robots", if (indexable) "index, follow" else "noindex, follow"),
      feed(origin, locale)) ++ (if (query.isEmpty) Seq(
      HeadEntry.alternate("en", s"$origin/en"), HeadEntry.alternate("de", s"$origin/de"),
      HeadEntry.alternate("x-default", s"$origin/en")) else Seq.empty)
  }

  def bindList(search: PostSearch, emptyPage: Boolean)(using owner: AbstractComponent): Unit =
    current.foreach { head =>
      val runtime = I18nRuntime.require
      head.bind(list(head.origin, runtime.locale.get.code, search, emptyPage,
        runtime.resolveNow(i18n"Latest posts"),
        runtime.resolveNow(i18n"Notes on Scala, the web, and the decisions in between."))*)
    }
}
