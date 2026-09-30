package com.anjunar.blog

import java.io.StringWriter
import java.lang.StringBuilder
import java.time.Instant
import javax.xml.stream.{XMLOutputFactory, XMLStreamWriter}

object DiscoveryXml {
  private val Sitemap = "http://www.sitemaps.org/schemas/sitemap/0.9"
  private val Atom = "http://www.w3.org/2005/Atom"

  private def document(namespace: String, root: String)(body: XMLStreamWriter => Unit): String = {
    val result = new StringWriter()
    val xml = XMLOutputFactory.newFactory().createXMLStreamWriter(result)
    try {
      xml.writeStartDocument("UTF-8", "1.0")
      xml.writeStartElement(root)
      xml.writeDefaultNamespace(namespace)
      body(xml)
      xml.writeEndElement()
      xml.writeEndDocument()
      xml.flush()
      result.toString
    } finally xml.close()
  }

  private def xmlText(value: String): String = {
    val result = new StringBuilder()
    value.codePoints().forEach { code =>
      val valid = code == 9 || code == 10 || code == 13 ||
        (code >= 0x20 && code <= 0xd7ff) || (code >= 0xe000 && code <= 0xfffd) ||
        (code >= 0x10000 && code <= 0x10ffff)
      result.appendCodePoint(if (valid) code else 0xfffd)
      ()
    }
    result.toString
  }

  private def text(xml: XMLStreamWriter, name: String, value: String): Unit = {
    xml.writeStartElement(name)
    xml.writeCharacters(xmlText(value))
    xml.writeEndElement()
  }

  def sitemap(origin: String, pages: Seq[PublishedPage]): String = document(Sitemap, "urlset") { xml =>
    // With no complete modification history, omit lastmod rather than inventing one.
    val entries = Seq("/en" -> None, "/de" -> None) ++ pages.map(page => page.path -> page.changed)
    entries.foreach { (path, changed) =>
      xml.writeStartElement("url")
      text(xml, "loc", origin + path)
      changed.foreach(value => text(xml, "lastmod", value.toString))
      xml.writeEndElement()
    }
  }

  def feed(origin: String, locale: String, pages: Seq[PublishedPage]): String = document(Atom, "feed") { xml =>
    xml.writeAttribute("xml", "http://www.w3.org/XML/1998/namespace", "lang", locale)
    val self = s"$origin/$locale/feed.xml"
    text(xml, "id", self)
    text(xml, "title", "Anjunar Journal")
    text(xml, "updated", pages.map(_.updated).sorted.lastOption.getOrElse(Instant.EPOCH).toString)
    xml.writeStartElement("author")
    text(xml, "name", "Anjunar Journal")
    xml.writeEndElement()
    def link(rel: String, href: String): Unit = {
      xml.writeEmptyElement("link")
      xml.writeAttribute("rel", rel)
      xml.writeAttribute("href", href)
    }
    link("self", self)
    link("alternate", s"$origin/$locale")
    pages.foreach { page =>
      xml.writeStartElement("entry")
      text(xml, "id", s"urn:uuid:${page.id}")
      text(xml, "title", page.title)
      link("alternate", origin + page.path)
      text(xml, "published", page.published.toString)
      text(xml, "updated", page.updated.toString)
      xml.writeStartElement("summary")
      xml.writeAttribute("type", "text")
      xml.writeCharacters(xmlText(page.summary.getOrElse(page.title)))
      xml.writeEndElement()
      xml.writeEndElement()
    }
  }
}
