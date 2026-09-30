package com.anjunar.blog

import org.scalatest.funsuite.AnyFunSuite
import org.w3c.dom.Document
import org.xml.sax.InputSource

import java.io.StringReader
import java.time.Instant
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory

class DiscoveryXmlSpec extends AnyFunSuite {
  private val origin = "https://journal.example"
  private val published = Instant.parse("2026-09-01T12:00:00Z")
  private val changed = Instant.parse("2026-09-02T12:00:00Z")
  private val id = UUID.fromString("00000000-0000-0000-0000-000000000024")
  private def parse(xml: String): Document = {
    val factory = DocumentBuilderFactory.newInstance()
    factory.setNamespaceAware(true)
    factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)))
  }

  test("sitemap contains canonical pages and omits unknown modification times") {
    val pages = Seq(PublishedPage(id, "example-post", "en", "Example", None, published, None),
      PublishedPage(UUID.randomUUID(), "example-post", "de", "Beispiel", None, published, Some(changed)))
    val doc = parse(DiscoveryXml.sitemap(origin, pages))
    assert(doc.getDocumentElement.getNamespaceURI == "http://www.sitemaps.org/schemas/sitemap/0.9")
    val urls = doc.getElementsByTagName("loc")
    assert((0 until urls.getLength).map(urls.item(_).getTextContent) ==
      Seq(origin + "/en", origin + "/de", origin + "/en/posts/example-post", origin + "/de/posts/example-post"))
    assert(doc.getElementsByTagName("lastmod").getLength == 1)
    assert(doc.getElementsByTagName("lastmod").item(0).getTextContent == changed.toString)
  }

  test("Atom escapes text, uses stable entity identity and carries real change times") {
    val title = "Quotes \" & <script> 😃"
    val page = PublishedPage(id, "example-post", "en", title, Some("<b>A & B</b>"), published, Some(changed))
    val doc = parse(DiscoveryXml.feed(origin, "en", Seq(page)))
    assert(doc.getDocumentElement.getNamespaceURI == "http://www.w3.org/2005/Atom")
    assert(doc.getElementsByTagName("title").item(1).getTextContent == title)
    assert(doc.getElementsByTagName("summary").item(0).getTextContent == "<b>A & B</b>")
    assert(doc.getElementsByTagName("summary").item(0).getAttributes.getNamedItem("type").getNodeValue == "text")
    assert(doc.getElementsByTagName("id").item(1).getTextContent == s"urn:uuid:$id")
    assert(doc.getElementsByTagName("updated").item(0).getTextContent == changed.toString)
    assert(doc.getElementsByTagName("updated").item(1).getTextContent == changed.toString)
    val renamed = parse(DiscoveryXml.feed(origin, "en", Seq(page.copy(slug = "renamed-post"))))
    assert(renamed.getElementsByTagName("id").item(1).getTextContent == s"urn:uuid:$id")
  }

  test("XML-disallowed control characters are replaced without damaging Unicode") {
    val page = PublishedPage(id, "example-post", "en", "A" + 1.toChar + "😃", None, published, None)
    val doc = parse(DiscoveryXml.feed(origin, "en", Seq(page)))
    assert(doc.getElementsByTagName("title").item(1).getTextContent == "A�😃")
  }

  test("empty feeds have stable identity, a publisher, and a deterministic update time") {
    val doc = parse(DiscoveryXml.feed(origin, "de", Seq.empty))
    assert(doc.getElementsByTagName("id").item(0).getTextContent == origin + "/de/feed.xml")
    assert(doc.getElementsByTagName("updated").item(0).getTextContent == Instant.EPOCH.toString)
    assert(doc.getElementsByTagName("author").getLength == 1)
    assert(doc.getElementsByTagName("entry").getLength == 0)
  }

  test("the public origin is configured, normalized, and restricted to a complete HTTP origin") {
    assert(PublicSite.origin(Map.empty, 8080) == "http://127.0.0.1:8080")
    assert(PublicSite.origin(Map("BLOG_PUBLIC_ORIGIN" -> "https://Journal.Example:443/"), 8080) == origin)
    Seq("http://example.com", "https://user@example.com", "https://example.com/blog",
      "https://example.com?host=other", "https://example.com#fragment", "//example.com", "https://example.com:0")
      .foreach(value => intercept[IllegalArgumentException](PublicSite.origin(Map("BLOG_PUBLIC_ORIGIN" -> value), 8080)))
  }
}
