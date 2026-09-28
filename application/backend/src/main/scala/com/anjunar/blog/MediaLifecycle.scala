package com.anjunar.blog

import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.persistence.{EntityManager, FlushModeType, LockModeType}
import jakarta.persistence.criteria.JoinType

import java.lang
import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import java.util.UUID
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

@RequestScoped
class MediaLifecycle {
  @Inject var manager: EntityManager = uninitialized
  @Inject var identity: SessionIdentity = uninitialized
  @Inject var caller: CallerAccess = uninitialized

  def create(image: DecodedImage, encodedName: String): Media = {
    val owner = identity.requireAccount().id
    cleanup(Instant.now().minusSeconds(86400), Some(owner), 20)
    val media = new Media()
    val decoded = try URLDecoder.decode(Option(encodedName).getOrElse(""), UTF_8)
      catch { case _: IllegalArgumentException => "" }
    val name = decoded.replace('\\', '/').split('/').lastOption.getOrElse("")
      .replaceAll("[\\p{Cntrl}]", "").trim.take(80)
    media.name = if (name.nonEmpty) name else "Uploaded image"
    media.contentType = image.contentType
    media.width = image.width
    media.height = image.height
    media.byteSize = image.bytes.length.toLong
    media.data = image.bytes
    media.ownerId = owner
    manager.persist(media)
    manager.flush()
    media
  }

  def referenced(media: Media, publishedOnly: Boolean = false): Boolean = {
    val builder = manager.getCriteriaBuilder
    val query = builder.createQuery(classOf[lang.Long])
    val post = query.from(classOf[BlogPost])
    // Hibernate joins require the real JPA collection attribute exposed by the schema.
    val reference = builder.or(Seq(
      builder.equal(post.get(BlogPost.schema.coverImage), media),
      builder.equal(post.join(BlogPost.schema.inlineMedia.collectionAttribute, JoinType.LEFT), media))*)
    val conditions = Seq(reference) ++ Option.when(publishedOnly)(
      builder.equal(post.get(BlogPost.schema.status), BlogPostStatus.PUBLISHED))
    query.select(builder.count(post)).where(conditions*)
    manager.createQuery(query).setFlushMode(FlushModeType.COMMIT).getSingleResult.longValue() > 0
  }

  def canUse(media: Media): Boolean =
    caller.administrator && (identity.account.exists(_.id == media.ownerId) || referenced(media))

  // The media-row lock is shared by attachment and cleanup. Recheck references after locking.
  def cleanup(cutoff: Instant, owner: Option[UUID], limit: Int): Int = {
    require(limit >= 1 && limit <= 100, "Cleanup batch must be between 1 and 100")
    val builder = manager.getCriteriaBuilder
    val query = builder.createQuery(classOf[UUID])
    val media = query.from(classOf[Media])
    val linked = query.subquery(classOf[UUID])
    val post = linked.from(classOf[BlogPost])
    val reference = builder.or(Seq(
      builder.equal(post.get(BlogPost.schema.coverImage), media),
      builder.equal(post.join(BlogPost.schema.inlineMedia.collectionAttribute, JoinType.LEFT), media))*)
    linked.select(post.get(BlogPost.schema.id)).where(Seq(reference)*)
    val conditions = Seq(builder.lessThan(media.get(Media.schema.createdAt), cutoff), builder.not(builder.exists(linked))) ++
      owner.toSeq.map(value => builder.equal(media.get(Media.schema.ownerId), value))
    query.select(media.get(Media.schema.id)).where(conditions*)
      .orderBy(builder.asc(media.get(Media.schema.id)))
    val ids = manager.createQuery(query).setFlushMode(FlushModeType.COMMIT).setMaxResults(limit).getResultList.asScala
    var removed = 0
    ids.foreach { id =>
      val current = manager.find(classOf[Media], id, LockModeType.PESSIMISTIC_WRITE)
      if (current != null && current.createdAt.isBefore(cutoff) &&
          owner.forall(_ == current.ownerId) && !referenced(current)) {
        manager.remove(current)
        removed += 1
      }
    }
    manager.flush()
    removed
  }
}
