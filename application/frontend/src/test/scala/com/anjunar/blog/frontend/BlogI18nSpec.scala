package com.anjunar.blog.frontend

import org.scalatest.funsuite.AnyFunSuite
import ui.core.i18n.{I18n, I18nRuntime, i18n}
import ui.core.state.Property
import ui.editor.EditorMessages

import scala.collection.mutable

class BlogI18nSpec extends AnyFunSuite {
  test("URL locale initializes the runtime and unknown prefixes retain the English default") {
    assert(I18nRuntime.managed(BlogI18n.config, "/de/posts/example?q=scala#code").locale.get == BlogI18n.German)
    assert(I18nRuntime.managed(BlogI18n.config, "/en").locale.get == BlogI18n.English)
    assert(I18nRuntime.managed(BlogI18n.config, "/fr").locale.get == BlogI18n.English)
  }

  test("translations retain named parameters and the English source is the fallback") {
    val message = i18n"Showing ${I18n.named("count", 2)} of ${I18n.named("total", 15L)} posts"
    assert(BlogI18n.config.resolver.resolve(message, BlogI18n.German) == "2 von 15 Beiträgen")
    assert(BlogI18n.config.resolver.resolve(message, BlogI18n.English) == "Showing 2 of 15 posts")
    assert(BlogI18n.config.resolver.resolve(i18n"A message without a catalog entry", BlogI18n.German) ==
      "A message without a catalog entry")
  }

  test("every catalog translation preserves exactly the source placeholders") {
    val placeholder = "\\{([A-Za-z][A-Za-z0-9_]*)\\}".r
    BlogI18n.catalog.keys.foreach { key =>
      val german = BlogI18n.catalog.entryFor(key).get.value.at(BlogI18n.German).get.value
      assert(placeholder.findAllMatchIn(german).map(_.group(1)).toSet == key.placeholders.toSet,
        s"Changed placeholders for '${key.source}'")
    }
  }

  test("bound messages and editor labels follow the same locale") {
    val runtime = I18nRuntime.managed(BlogI18n.config, "/en")
    val seen = mutable.ArrayBuffer.empty[String]
    val subscription = runtime.text(i18n"Save post").observe(seen += _)
    runtime.setLocale(BlogI18n.German)
    assert(seen.toSeq == Seq("Save post", "Beitrag speichern"))
    assert(runtime.resolveNow(EditorMessages.uploading(2)) == "2 Bild(er) werden hochgeladen…")
    assert(runtime.resolveNow(EditorMessages.editImage) == "Bild bearbeiten")
    subscription.dispose()
  }

  test("form guards combine independently and release subscriptions on disposal") {
    val navigation = new LanguageNavigation()
    val title = Property("")
    val busy = Property(false)
    val first = navigation.watch(Seq(title))(title.get.nonEmpty)
    val second = navigation.watch(Seq(busy))(busy.get)
    assert(!navigation.blocked.get)
    title.set("Unsaved")
    busy.set(true)
    title.set("")
    assert(navigation.blocked.get)
    second.dispose()
    assert(!navigation.blocked.get)
    title.set("Still here")
    assert(navigation.blocked.get)
    first.dispose()
    first.dispose()
    assert(!navigation.blocked.get && title.get == "Still here")
    title.set("Disposed observer")
    busy.set(false)
    busy.set(true)
    assert(!navigation.blocked.get)
  }
}
