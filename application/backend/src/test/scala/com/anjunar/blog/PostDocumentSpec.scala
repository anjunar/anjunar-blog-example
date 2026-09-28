package com.anjunar.blog

import org.scalatest.funsuite.AnyFunSuite

import java.util.UUID

class PostDocumentSpec extends AnyFunSuite {
  private val first = UUID.fromString("b8a6b169-a8d4-482e-a6d9-70a8f34e19e1")
  private val second = UUID.fromString("935b93fc-a533-4a2a-a4dd-f2cbe1328987")
  private def image(id: UUID, alt: String = "Preview"): String = s"![$alt](/service/media/$id)"

  test("the document collects distinct inline and reference images") {
    val source = image(first) + "\n\n" + image(first) +
      s"\n\n![Another preview][second]\n\n[second]: /service/media/$second"
    assert(PostDocument.inspect(source) == Right(DocumentInfo(Set(first, second), hasContent = true)))
  }

  test("code samples and escaped Markdown do not become media references") {
    val source = s"```markdown\n${image(first)}\n```\n\n`${image(second)}`\n\n\\${image(first)}"
    assert(PostDocument.inspect(source) == Right(DocumentInfo(Set.empty, hasContent = true)))
    assert(PostDocument.inspect("    " + image(first)).toOption.get.images.isEmpty)
  }

  test("image URLs must be exact internal references and have alternative text") {
    for (url <- Seq("https://example.test/a.png", "data:image/png;base64,AA", "//example.test/a",
      s"/service/media/$first?size=small", s"/service/media/$first/extra", "/service/media/not-a-uuid"))
      assert(PostDocument.inspect(s"![Preview]($url)").isLeft, url)
    assert(PostDocument.inspect(image(first, "")).isLeft)
    assert(PostDocument.inspect(image(first, "   ")).isLeft)
    assert(PostDocument.inspect(image(first, "**Meaningful** description")).isRight)
  }

  test("HTML and executable links are rejected while ordinary links remain usable") {
    for (value <- Seq("<script>alert(1)</script>", "Text <img src=x>", "[click](javascript:alert)",
      "[click](JaVaScRiPt:alert)", "[click](data:text/html,payload)", "[click](//example.test/path)"))
      assert(PostDocument.inspect(value).isLeft, value)
    for (url <- Seq("https://example.test/path", "http://example.test", "mailto:reader@example.test", "/posts/first", "#heading"))
      assert(PostDocument.inspect(s"[Read more]($url)").isRight, url)
  }

  test("publication needs content rather than only structural markup") {
    for (empty <- Seq("", "  \n\n", "---", "# ", "> "))
      assert(!PostDocument.hasContent(empty, "MARKDOWN"), empty)
    assert(PostDocument.hasContent("# Heading", "MARKDOWN"))
    assert(PostDocument.hasContent("```scala\nval answer = 42\n```", "MARKDOWN"))
    assert(PostDocument.hasContent(image(first), "MARKDOWN"))
  }

  test("plain text retains its existing interpretation") {
    assert(PostDocument.hasContent("---", "PLAIN_TEXT"))
    assert(PostDocument.hasContent("# Still literal", null))
    assert(PostDocument.hasContent("<example>", "PLAIN_TEXT"))
    assert(!PostDocument.hasContent("", "PLAIN_TEXT"))
    assert(!PostDocument.hasContent(null, "PLAIN_TEXT"))
    assert(!PostDocument.hasContent("Some text", "unknown"))
  }

  test("input size, nesting and distinct image counts are bounded") {
    assert(PostDocument.inspect("a" * 100001).isLeft)
    assert(PostDocument.inspect(null).isLeft)
    assert(PostDocument.inspect(("> " * 40) + "Nested").isLeft)
    val tooMany = (1 to 21).map(_ => image(UUID.randomUUID())).mkString("\n\n")
    assert(PostDocument.inspect(tooMany).isLeft)
    assert(PostDocument.inspect(Seq.fill(25)(image(first)).mkString("\n\n")).isRight)
  }
}
