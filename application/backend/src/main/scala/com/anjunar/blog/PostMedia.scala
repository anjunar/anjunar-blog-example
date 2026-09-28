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
    val ids = if (post.contentFormat == "MARKDOWN")
      PostDocument.inspect(post.content).fold(message => Problem.invalidField("content", message), _.images)
    else Set.empty
    val resolved = ids.toSeq.sortBy(_.toString).map { id =>
      val media = manager.find(classOf[Media], id, LockModeType.PESSIMISTIC_WRITE)
      if (media == null || !lifecycle.canUse(media))
        Problem.invalidField("content", "An embedded image is unavailable or belongs to another account.")
      media
    }
    post.inlineMedia.clear()
    resolved.foreach(post.inlineMedia.add)
  }
}
