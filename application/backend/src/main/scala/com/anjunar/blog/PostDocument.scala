package com.anjunar.blog

import org.commonmark.node.{Code, FencedCodeBlock, HtmlBlock, HtmlInline, Image, IndentedCodeBlock, Link, Node, Text}
import org.commonmark.parser.Parser

import java.net.URI
import java.util.{Locale, UUID}
import scala.collection.mutable
import scala.util.control.NonFatal

final case class DocumentInfo(images: Set[UUID], hasContent: Boolean)

object PostDocument {
  private val parser = Parser.builder().build()
  private val imagePath = "^/service/media/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$".r

  def imageId(source: String): Option[UUID] = Option(source).flatMap {
    case imagePath(id) => Some(UUID.fromString(id))
    case _ => None
  }

  def inspect(source: String): Either[String, DocumentInfo] = {
    if (source == null || source.length > 100000) return Left("Content must be at most 100000 characters.")
    try {
      val images = mutable.Set.empty[UUID]
      val pending = mutable.Stack[(Node, Int)]((parser.parse(source), 0))
      var count = 0
      var meaningful = false
      while (pending.nonEmpty) {
        val (node, depth) = pending.pop()
        count += 1
        if (count > 10000 || depth > 32) return Left("The document is too complex.")
        node match {
          case _: HtmlInline | _: HtmlBlock => return Left("Use Markdown; raw HTML is not supported.")
          case image: Image =>
            val id = imageId(image.getDestination) match {
              case Some(value) => value
              case None => return Left("Images must reference an uploaded /service/media/ UUID.")
            }
            if (!hasAlternativeText(image)) return Left("Every embedded image needs alternative text.")
            images += id
            if (images.size > 20) return Left("Use at most 20 distinct embedded images.")
            meaningful = true
          case link: Link if !safeLink(link.getDestination) =>
            return Left("Use an HTTP, HTTPS, mailto or relative link.")
          case value: Text => meaningful ||= !value.getLiteral.isBlank
          case value: Code => meaningful ||= !value.getLiteral.isBlank
          case value: FencedCodeBlock => meaningful ||= !value.getLiteral.isBlank
          case value: IndentedCodeBlock => meaningful ||= !value.getLiteral.isBlank
          case _ => ()
        }
        var child = node.getFirstChild
        while (child != null) {
          pending.push((child, depth + 1))
          child = child.getNext
        }
      }
      Right(DocumentInfo(images.toSet, meaningful))
    } catch { case NonFatal(_) => Left("The Markdown document could not be read.") }
  }

  def hasContent(source: String, format: String): Boolean =
    if (format == "MARKDOWN") inspect(source).exists(_.hasContent)
    else (format == null || format == "PLAIN_TEXT") && source != null && !source.isBlank

  private def hasAlternativeText(image: Image): Boolean = {
    val pending = mutable.Stack[Node](image)
    while (pending.nonEmpty) {
      val node = pending.pop()
      node match {
        case value: Text if !value.getLiteral.isBlank => return true
        case value: Code if !value.getLiteral.isBlank => return true
        case _ => ()
      }
      var child = node.getFirstChild
      while (child != null) {
        pending.push(child)
        child = child.getNext
      }
    }
    false
  }

  private def safeLink(value: String): Boolean = {
    if (value == null || value.exists(_.isControl) || value.contains('\\') || value.startsWith("//")) return false
    try {
      val uri = URI.create(value)
      Option(uri.getScheme).forall(scheme => Set("http", "https", "mailto").contains(scheme.toLowerCase(Locale.ROOT)))
    } catch { case _: IllegalArgumentException => false }
  }
}
