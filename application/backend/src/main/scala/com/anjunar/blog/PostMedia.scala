package com.anjunar.blog

import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.persistence.{EntityManager, LockModeType}

import scala.compiletime.uninitialized

@RequestScoped
class PostMedia {
  @Inject var manager: EntityManager = uninitialized
  @Inject var lifecycle: MediaLifecycle = uninitialized

  // These relations are derived from the document, never supplied as another JSON collection.
  def synchronize(post: BlogPost): Unit = {
    val resolved = resolve(post.content, post.contentFormat)
    post.inlineMedia.clear()
    resolved.foreach(post.inlineMedia.add)
  }

  def synchronize(translation: BlogPostTranslation): Unit = {
    val resolved = resolve(translation.content, "MARKDOWN")
    translation.inlineMedia.clear()
    resolved.foreach(translation.inlineMedia.add)
  }

  private def resolve(content: String, format: String): Seq[Media] = {
    val ids = if (format == "MARKDOWN")
      PostDocument.inspect(content).fold(message => Problem.invalidField("content", message), _.images)
    else Set.empty
    ids.toSeq.sortBy(_.toString).map { id =>
      val media = manager.find(classOf[Media], id, LockModeType.PESSIMISTIC_WRITE)
      if (media == null || !lifecycle.canUse(media))
        Problem.invalidField("content", "An embedded image is unavailable or belongs to another account.")
      media
    }
  }
}
