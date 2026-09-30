package com.anjunar.blog.frontend

import org.scalatest.funsuite.AnyFunSuite
import ui.core.document.HeadEntry

class PageHeadSpec extends AnyFunSuite {
  private val origin = "https://journal.example"
  private def attribute(entries: Seq[HeadEntry], key: String, name: String): String =
    entries.find(_.key == key).get.attributes.toMap.apply(name)
  private def post(): BlogPost = {
    val value = new BlogPost
    value.slug.set("example-post")
    value.title.set("English title")
    value.summary.set("English summary")
    value.availableLocales.setAll(Seq("en"))
    value
  }

  test("an English fallback has an English canonical and no invented German alternate") {
    val entries = PageHead.article(origin, post())
    assert(attribute(entries, "link:canonical", "href") == origin + "/en/posts/example-post")
    assert(!entries.exists(_.key == "link:alternate:de"))
    assert(attribute(entries, "link:alternate:x-default", "href") == origin + "/en/posts/example-post")
  }

  test("a published German article uses its own summary policy and reciprocal alternatives") {
    val value = post()
    val german = new BlogPostTranslation
    german.title.set("Deutscher Titel")
    german.summary.set(null)
    value.translation.set(german)
    value.contentLocale.set("de")
    value.availableLocales.setAll(Seq("en", "de"))
    val entries = PageHead.article(origin, value)
    assert(attribute(entries, "link:canonical", "href") == origin + "/de/posts/example-post")
    assert(attribute(entries, "meta:name=description", "content") == "Deutscher Titel")
    assert(attribute(entries, "link:alternate:en", "href") == origin + "/en/posts/example-post")
    assert(attribute(entries, "link:alternate:de", "href") == origin + "/de/posts/example-post")
  }

  test("pagination stays self-canonical while search, custom sorting and empty pages are noindex") {
    def entries(search: PostSearch, empty: Boolean = false) = PageHead.list(origin, "en", search, empty, "Posts", "Notes")
    assert(attribute(entries(PostSearch(offset = 20)), "link:canonical", "href") == origin + "/en?offset=20")
    assert(attribute(entries(PostSearch(offset = 20)), "meta:name=robots", "content") == "index, follow")
    Seq(PostSearch(query = "Scala & CDI"), PostSearch(sort = "title"), PostSearch(limit = 5))
      .foreach(search => assert(attribute(entries(search), "meta:name=robots", "content") == "noindex, follow"))
    assert(attribute(entries(PostSearch(offset = 100), true), "meta:name=robots", "content") == "noindex, follow")
    assert(!entries(PostSearch(query = "Scala")).exists(_.key == "link:alternate:de"))
  }
}
