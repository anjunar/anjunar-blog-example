package com.anjunar.blog

import com.anjunar.json.mapper.{EntityLoader, JsonMapper}
import com.anjunar.json.mapper.intermediate.JsonParser
import com.anjunar.json.mapper.intermediate.model.JsonObject
import com.anjunar.scala.universe.TypeResolver
import jakarta.enterprise.context.control.RequestContextController
import jakarta.enterprise.inject.se.{SeContainer, SeContainerInitializer}
import jakarta.persistence.{EntityManager, OptimisticLockException}
import jakarta.validation.{ConstraintViolationException, Validation}
import org.hibernate.exception.{ConstraintViolationException as SqlConstraintViolationException}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

import java.sql.{DriverManager, SQLException}
import java.time.Instant
import java.util.UUID
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.Using
import scala.util.control.NonFatal

class BlogPostPersistenceSpec extends AnyFunSuite with BeforeAndAfterAll {
  private var container: SeContainer = null
  private var persistence: Persistence = null
  private val ownedIds = mutable.Set.empty[UUID]
  private var initialized = false

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    container = SeContainerInitializer.newInstance().initialize()
    persistence = container.select(classOf[Persistence]).get()
    persistence.openEntityManager().close()
    initialized = true
  }

  override protected def afterAll(): Unit =
    try {
      if (initialized) inTransaction() { manager =>
        for (id <- ownedIds) {
          val post = manager.find(classOf[BlogPost], id)
          if (post != null) manager.remove(post)
        }
      }
    } finally {
      try { if (container != null) container.close() }
      finally super.afterAll()
    }

  private def inTransaction[A](readOnly: Boolean = false)(work: EntityManager => A): A = {
    val request = container.select(classOf[RequestContextController]).get()
    assert(request.activate())
    val transaction = container.select(classOf[RequestTransaction]).get()
    try {
      transaction.begin(readOnly)
      val result = work(transaction.entityManager)
      transaction.flush(true)
      transaction.finish(true)
      result
    } catch {
      case NonFatal(error) =>
        try transaction.finish(false)
        catch { case NonFatal(cleanup) => error.addSuppressed(cleanup) }
        throw error
    } finally request.deactivate()
  }

  private def draft(): BlogPost = {
    val post = new BlogPost()
    post.slug = s"post-${UUID.randomUUID()}"
    post.title = "Our first post"
    post.content = "The first paragraph."
    post
  }

  private def persist(manager: EntityManager, post: BlogPost): BlogPost = {
    manager.persist(post)
    ownedIds += post.id
    post
  }

  private def savedDraft(): BlogPost =
    inTransaction()(manager => persist(manager, draft()))

  private def load(id: UUID): BlogPost =
    inTransaction(readOnly = true)(_.find(classOf[BlogPost], id))

  test("CDI discovers entity classes without making their instances injectable") {
    val registry = container.select(classOf[EntityRegistry]).get()
    assert(registry.entityClasses.toSet == Set(classOf[BlogPost], classOf[EntityDiscoveryProbe]))
    assert(container.select(classOf[BlogPost]).isUnsatisfied)
    assert(container.select(classOf[EntityDiscoveryProbe]).isUnsatisfied)
  }

  test("Hibernate maps an additional discovered entity without a manual class registration") {
    val saved = savedDraft()
    val probe = inTransaction(readOnly = true)(_.find(classOf[EntityDiscoveryProbe], saved.id))
    assert(probe != null)
    assert(probe.id == saved.id)
    assert(probe.title == saved.title)
  }

  test("a persisted draft receives a UUID and version and survives a new persistence context") {
    val saved = savedDraft()
    assert(saved.id != null)
    assert(saved.version == 0L)
    val loaded = load(saved.id)
    assert(loaded ne saved)
    assert(loaded.id == saved.id)
    assert(loaded.version == saved.version)
    assert(loaded.slug == saved.slug)
    assert(loaded.title == saved.title)
    assert(loaded.content == saved.content)
    assert(loaded.status == BlogPostStatus.DRAFT)
    assert(loaded.publishedAt == null)
  }

  test("managed changes persist publication state and increment the version without changing the slug") {
    val saved = savedDraft()
    val originalVersion = saved.version
    val at = Instant.parse("2026-09-27T10:00:00Z")
    inTransaction() { manager =>
      val post = manager.find(classOf[BlogPost], saved.id)
      post.title = "A better title"
      post.publish(at)
    }
    val published = load(saved.id)
    assert(published.version == originalVersion + 1)
    assert(published.slug == saved.slug)
    assert(published.title == "A better title")
    assert(published.status == BlogPostStatus.PUBLISHED)
    assert(published.publishedAt == at)

    inTransaction()(_.find(classOf[BlogPost], saved.id).retract())
    val retracted = load(saved.id)
    assert(retracted.status == BlogPostStatus.DRAFT)
    assert(retracted.publishedAt == null)
    assert(retracted.version == originalVersion + 2)
  }

  test("a summary can be saved and cleared without changing the post identity") {
    val saved = savedDraft()
    assert(load(saved.id).summary == null)
    inTransaction() { manager =>
      manager.find(classOf[BlogPost], saved.id).summary = "A short introduction."
    }
    val edited = load(saved.id)
    assert(edited.summary == "A short introduction.")
    assert(edited.slug == saved.slug)
    assert(edited.version == saved.version + 1)
    inTransaction()(_.find(classOf[BlogPost], saved.id).summary = null)
    assert(load(saved.id).summary == null)
  }

  test("Hibernate validates new entities before inserting them") {
    val post = draft()
    post.title = " "
    val error = intercept[ConstraintViolationException] {
      inTransaction()(manager => persist(manager, post))
    }
    assert(!error.getConstraintViolations.isEmpty)
    assert(load(post.id) == null)
  }

  test("Hibernate validates changed entities before updating them") {
    val saved = savedDraft()
    intercept[ConstraintViolationException] {
      inTransaction() { manager =>
        val post = manager.find(classOf[BlogPost], saved.id)
        post.status = BlogPostStatus.PUBLISHED
      }
    }
    val loaded = load(saved.id)
    assert(loaded.status == BlogPostStatus.DRAFT)
    assert(loaded.publishedAt == null)
    assert(loaded.version == saved.version)
  }

  test("PostgreSQL rejects a duplicate slug and keeps the first post") {
    val first = savedDraft()
    val duplicate = draft()
    duplicate.slug = first.slug
    val error = intercept[SqlConstraintViolationException] {
      inTransaction()(manager => persist(manager, duplicate))
    }
    assert(error.getSQLState == "23505")
    assert(load(first.id).title == first.title)
    assert(load(duplicate.id) == null)
  }

  test("an old detached version cannot overwrite a newer committed edit") {
    val saved = savedDraft()
    val firstEditor = load(saved.id)
    val secondEditor = load(saved.id)
    firstEditor.title = "The committed title"
    inTransaction()(_.merge(firstEditor))
    secondEditor.title = "The stale title"
    intercept[OptimisticLockException] {
      inTransaction()(_.merge(secondEditor))
    }
    val current = load(saved.id)
    assert(current.title == "The committed title")
    assert(current.version == saved.version + 1)
  }

  test("the database rejects a published row without a publication time even outside Hibernate") {
    val config = DatabaseConfig.load()
    val connection = DriverManager.getConnection(config.url, config.user, config.password)
    val id = UUID.randomUUID()
    ownedIds += id
    try {
      val command = connection.prepareStatement(
        "insert into public.blog_post (id, version, slug, title, content, status) values (?, 0, ?, ?, ?, 'PUBLISHED')")
      try {
        command.setObject(1, id)
        command.setString(2, s"post-$id")
        command.setString(3, "Direct SQL")
        command.setString(4, "This insert must fail.")
        val error = intercept[SQLException](command.executeUpdate())
        assert(error.getSQLState == "23514")
      } finally command.close()
    } finally connection.close()
    assert(load(id) == null)
  }

  test("the schema covers every persistent field and exposes real JPA attributes") {
    inTransaction(readOnly = true) { manager =>
      val schema = BlogPost.schema
      val mappedNames = manager.getMetamodel.entity(classOf[BlogPost])
        .getAttributes.asScala.map(_.getName).toSet
      assert(schema.properties.keySet.toSet == mappedNames)
      assert(schema.id.isId)
      assert(schema.version.isVersion)
      assert(schema.slug.getJavaType == classOf[String])
      assert(!schema.slug.isAssociation)

      val post = draft()
      post.title = "A title read through the schema"
      assert(schema.title.get(post) == post.title)
    }
  }

  test("typed Criteria finds a published slug and excludes drafts and missing posts") {
    val published = savedDraft()
    val unpublished = savedDraft()
    inTransaction()(_.find(classOf[BlogPost], published.id).publish(Instant.parse("2026-09-27T10:00:00Z")))

    // The schema was initialized in an earlier request; its attributes remain reusable.
    inTransaction(readOnly = true) { manager =>
      assert(BlogPost.findPublishedBySlug(published.slug)(using manager).map(_.id).contains(published.id))
      assert(BlogPost.findPublishedBySlug(unpublished.slug)(using manager).isEmpty)
      assert(BlogPost.findPublishedBySlug(s"missing-${UUID.randomUUID()}")(using manager).isEmpty)
    }
  }

  private val constructRule = [T] => (clazz: Class[T]) => clazz.getDeclaredConstructor().newInstance()

  private def json(post: BlogPost): JsonObject =
    JsonParser.parse(JsonMapper.serialize(
      post, TypeResolver.resolve(classOf[BlogPost]), null, constructRule
    )).asInstanceOf[JsonObject]

  test("the mapper publishes every populated schema field and preserves the Instant precision") {
    val post = draft()
    val publishedAt = Instant.parse("2026-09-27T10:15:42.123456Z")
    post.summary = "A concise introduction."
    post.publish(publishedAt)
    val saved = inTransaction()(manager => persist(manager, post))

    inTransaction(readOnly = true) { manager =>
      val loaded = manager.find(classOf[BlogPost], saved.id)
      val output = json(loaded)
      assert(output.value.keySet().asScala.toSet == Set(
        "@type", "id", "version", "slug", "title", "content", "summary", "status", "publishedAt"))
      assert(output.getString("@type") == "BlogPost")
      assert(output.getString("id") == saved.id.toString)
      assert(output.value.get("version").value == saved.version.toString)
      assert(output.getString("slug") == saved.slug)
      assert(output.getString("title") == saved.title)
      assert(output.getString("content") == saved.content)
      assert(output.getString("summary") == saved.summary)
      assert(output.getString("status") == "PUBLISHED")
      assert(output.getString("publishedAt") == publishedAt.toString)
    }
  }

  test("the mapper omits null optional values but retains version zero") {
    val saved = savedDraft()
    inTransaction(readOnly = true) { manager =>
      val output = json(manager.find(classOf[BlogPost], saved.id))
      assert(!output.value.containsKey("summary"))
      assert(!output.value.containsKey("publishedAt"))
      assert(output.value.get("version").value == "0")
    }
  }

  test("default schema rules ignore incoming writes without changing the stored post") {
    val saved = savedDraft()
    inTransaction() { manager =>
      val post = manager.find(classOf[BlogPost], saved.id)
      val input = JsonParser.parse(
        """{"title":"Unauthorized title","summary":"Unauthorized summary","version":999,
          |"status":"PUBLISHED","publishedAt":"2026-09-27T10:00:00Z"}""".stripMargin)
      val noReferences = new EntityLoader {
        override def load(id: UUID, clazz: Class[?]): Any =
          throw new AssertionError("This scalar input must not load references")
      }
      Using.resource(Validation.buildDefaultValidatorFactory()) { factory =>
        JsonMapper.deserialize(input, post, TypeResolver.resolve(classOf[BlogPost]),
          null, noReferences, constructRule, factory.getValidator)
      }
      assert(post.title == saved.title)
      assert(post.summary == null)
      assert(post.status == BlogPostStatus.DRAFT)
      assert(post.publishedAt == null)
      assert(post.version == saved.version)
    }
    val unchanged = load(saved.id)
    assert(unchanged.title == saved.title)
    assert(unchanged.summary == null)
    assert(unchanged.status == BlogPostStatus.DRAFT)
    assert(unchanged.version == saved.version)
  }
}
