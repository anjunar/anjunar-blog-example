package com.anjunar.blog.frontend

import org.scalajs.dom
import ui.editor.{MediaReference, MediaUploader, MediaUrlPolicy, UploadedMediaReference}

import scala.concurrent.{ExecutionContext, Future}

object PostMarkdown {
  private val imagePath = "^/service/media/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$".r

  val mediaPolicy: MediaUrlPolicy = new MediaUrlPolicy {
    def resolve(source: String): Option[MediaReference] = Option(source).flatMap {
      case imagePath(id) => Some(MediaReference(source, Some(id)))
      case _ => None
    }
  }

  def uploader(service: MediaService)(using ExecutionContext): MediaUploader = new MediaUploader {
    def upload(file: dom.File, signal: dom.AbortSignal): Future[UploadedMediaReference] =
      service.upload(file, signal).map(media => UploadedMediaReference(media.source, media.id.get))
  }

  // Conversion is explicit: existing asterisks, headings and HTML-like text stay literal.
  def fromPlainText(value: String): String = Option(value).getOrElse("")
    .replace("\r\n", "\n").replace("\r", "\n")
    .flatMap {
      case '&' => "&amp;"
      case '<' => "&lt;"
      case '>' => "&gt;"
      case character =>
        if ("\\`*_{}[]()#+.!|~=-".contains(character)) "\\" + character else character.toString
    }
    .replace("\n", "  \n")
}
