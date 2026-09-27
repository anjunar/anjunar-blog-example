package com.anjunar.blog.frontend

import org.scalatest.funsuite.AnyFunSuite

class PostSearchSpec extends AnyFunSuite {
  private def parse(values: (String, String)*)(editorial: Boolean = false): PostSearch =
    PostSearch.parse(values.toMap.get, editorial)

  test("public and editorial defaults keep their existing ordering") {
    assert(parse()() == PostSearch())
    assert(parse()(editorial = true).sort == "title")
    assert(parse()().url == "/")
    assert(parse()(editorial = true).url == "/editorial")
  }

  test("filter values are encoded once and survive a page change") {
    val value = parse("q" -> "  100%_! & + café  ", "sort" -> "title-desc", "limit" -> "10")()
    assert(value.query == "100%_! & + café")
    val next = value.copy(offset = 10)
    assert(next.url.startsWith("/?offset=10&limit=10&q=100%25_!%20%26%20%2B%20caf%C3%A9"))
    assert(next.url.endsWith("&sort=title-desc"))
    assert(next.copy(offset = 0).query == value.query)
  }

  test("submitting changed filters resets the page without changing the current route state") {
    val current = PostSearch("old", "DRAFT", "title", 40, 10, editorial = true)
    val form = new PostSearchFields(current)
    form.query.set("new")
    form.status.set("PUBLISHED")
    val submitted = form.submitted(editorial = true)
    assert(submitted.offset == 0 && submitted.limit == 10 && submitted.query == "new")
    assert(submitted.status == "PUBLISHED" && submitted.sort == "title")
    assert(current.offset == 40 && current.query == "old")
  }

  test("malformed routes fail before issuing an HTTP request") {
    for ((key, value) <- Seq("offset" -> "-1", "offset" -> "2147483648", "limit" -> "0",
      "limit" -> "101", "sort" -> "content", "status" -> "DRAFT", "q" -> ("a" * 101), "q" -> "a\u0000b")) {
      assert(intercept[HttpFailure](parse(key -> value)()).status == 400)
    }
    assert(parse("status" -> "DRAFT")(editorial = true).status == "DRAFT")
  }

  test("API requests include bounds while default browser URLs stay short") {
    assert(PostSearch().queryString(includeDefaults = true) == "offset=0&limit=20")
    assert(PostSearch(offset = 20).url == "/?offset=20")
    assert(parse("limit" -> "1")().limit == 1)
  }
}
