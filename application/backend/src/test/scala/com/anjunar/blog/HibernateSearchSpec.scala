package com.anjunar.blog

import com.anjunar.blog.hibernate.search.{AbstractSearch, Context, HibernateSearch, PredicateProvider, SortProvider}
import com.anjunar.blog.hibernate.search.annotations.{RestPredicate, RestSort}
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.control.RequestContextController
import jakarta.enterprise.inject.Vetoed
import jakarta.enterprise.inject.se.{SeContainer, SeContainerInitializer}
import jakarta.json.bind.annotation.JsonbProperty
import jakarta.persistence.EntityManager
import jakarta.persistence.criteria.Order
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

import java.util
import java.util.UUID
import scala.annotation.meta.field

object HibernateSearchSpec {
  final case class ById(
      @(JsonbProperty @field) @(RestPredicate @field)(value = classOf[IdPredicate], name = "postId")
      id: UUID
  ) extends AbstractSearch {
    override val index: Int = 0
    override val limit: Int = 10
  }

  @ApplicationScoped
  class IdPredicate extends PredicateProvider[UUID, BlogPost] {
    override def build(context: Context[UUID, BlogPost]): Unit = {
      val parameter = context.builder.parameter(classOf[UUID], context.name)
      context.predicates.add(context.builder.equal(context.root.get(BlogPost.schema.id), parameter))
      context.parameters.put(context.name, context.value)
    }
  }

  final case class MissingPredicate(
      @(JsonbProperty @field) @(RestPredicate @field)(classOf[UndiscoveredPredicate])
      id: UUID
  ) extends AbstractSearch {
    override val index: Int = 0
    override val limit: Int = 10
  }

  @Vetoed
  class UndiscoveredPredicate extends PredicateProvider[UUID, BlogPost] {
    override def build(context: Context[UUID, BlogPost]): Unit =
      throw new AssertionError("This provider must not be discovered")
  }

  final case class MissingSort(
      @(JsonbProperty @field) @(RestSort @field)(classOf[UndiscoveredSort])
      sort: String = "title"
  ) extends AbstractSearch {
    override val index: Int = 0
    override val limit: Int = 10
  }

  @Vetoed
  class UndiscoveredSort extends SortProvider[MissingSort, BlogPost] {
    override def sort(context: Context[MissingSort, BlogPost]): util.List[Order] =
      throw new AssertionError("This provider must not be discovered")
  }
}

class HibernateSearchSpec extends AnyFunSuite with BeforeAndAfterAll {
  import HibernateSearchSpec.*

  private var container: SeContainer = null

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    container = SeContainerInitializer.newInstance().initialize()
    container.select(classOf[Persistence]).get().openEntityManager().close()
  }

  override protected def afterAll(): Unit =
    try { if (container != null) container.close() }
    finally super.afterAll()

  private def inTransaction[A](work: (HibernateSearch, EntityManager) => A): A = {
    val request = container.select(classOf[RequestContextController]).get()
    assert(request.activate())
    val transaction = container.select(classOf[RequestTransaction]).get()
    try {
      transaction.begin(readRequest = false)
      work(container.select(classOf[HibernateSearch]).get(), transaction.entityManager)
    } finally {
      // Every test owns its inserts and always rolls them back.
      try transaction.finish(false)
      finally request.deactivate()
    }
  }

  test("a new CDI search provider supports entity and scalar projections and a separate count") {
    inTransaction { (queries, manager) =>
      val post = new BlogPost()
      post.slug = "search-provider-" + UUID.randomUUID()
      post.title = "A separate provider"
      post.content = "Only selected for the entity projection."
      manager.persist(post)
      manager.flush()
      manager.clear()

      val search = ById(post.id)
      val context = queries.searchContext(search)
      val entities = queries.entities(search.index, search.limit, classOf[BlogPost],
        classOf[BlogPost], context, (query, root, _, _) => query.select(root))
      val titles = queries.entities(search.index, search.limit, classOf[BlogPost],
        classOf[String], context, (query, root, _, _) => query.select(root.get(BlogPost.schema.title)))
      assert(entities.size() == 1)
      assert(entities.get(0).id == post.id)
      assert(titles == util.List.of(post.title))
      assert(queries.count(classOf[BlogPost], context) == 1L)
      assert(queries.count(classOf[BlogPost], queries.searchContext(ById(UUID.randomUUID()))) == 0L)
    }
  }

  test("a missing predicate provider fails instead of returning unrestricted rows or counts") {
    inTransaction { (queries, _) =>
      val context = queries.searchContext(MissingPredicate(UUID.randomUUID()))
      val countError = intercept[IllegalStateException](queries.count(classOf[BlogPost], context))
      assert(countError.getMessage.contains(classOf[UndiscoveredPredicate].getName))
      intercept[IllegalStateException] {
        queries.entities(0, 10, classOf[BlogPost], classOf[BlogPost], context,
          (query, root, _, _) => query.select(root))
      }
    }
  }

  test("a missing sort provider fails instead of returning silently unordered pages") {
    inTransaction { (queries, _) =>
      val error = intercept[IllegalStateException] {
        queries.entities(0, 10, classOf[BlogPost], classOf[BlogPost],
          queries.searchContext(MissingSort()), (query, root, _, _) => query.select(root))
      }
      assert(error.getMessage.contains(classOf[UndiscoveredSort].getName))
    }
  }

  test("internal callers cannot bypass the generic page bounds") {
    inTransaction { (queries, _) =>
      val context = queries.searchContext(ById(UUID.randomUUID()))
      for ((index, limit) <- Seq((-1, 10), (0, 0), (0, 101))) {
        intercept[IllegalArgumentException] {
          queries.entities(index, limit, classOf[BlogPost], classOf[BlogPost], context,
            (query, root, _, _) => query.select(root))
        }
      }
    }
  }
}
