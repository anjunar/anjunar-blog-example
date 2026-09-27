package com.anjunar.blog.hibernate.search

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.persistence.criteria.{Expression, Order, Predicate}
import org.hibernate.query.criteria.{HibernateCriteriaBuilder, JpaCriteriaQuery, JpaRoot}

import java.lang
import java.util
import scala.compiletime.uninitialized

@ApplicationScoped
class HibernateSearch {
  @Inject var entityManager: EntityManager = uninitialized
  @Inject var predicateProviders: Instance[PredicateProvider[?, ?]] = uninitialized
  @Inject var sortProviders: Instance[SortProvider[?, ?]] = uninitialized

  def searchContext[S <: AbstractSearch](search: S): HibernateSearchContext =
    new HibernateSearchContext {
      override def apply[E](
          builder: HibernateCriteriaBuilder, query: JpaCriteriaQuery[?], root: JpaRoot[E]
      ): HibernateSearchContextResult =
        SearchBeanReader.read(search, builder, root, query,
          predicateProviders.asInstanceOf[Instance[PredicateProvider[Any, E]]])

      override def sort[E](
          builder: HibernateCriteriaBuilder, query: JpaCriteriaQuery[?], root: JpaRoot[E],
          predicates: util.List[Predicate], selection: util.List[Expression[?]]
      ): util.List[Order] =
        SearchBeanReader.order(search, builder, root, query, predicates, selection,
          sortProviders.asInstanceOf[Instance[SortProvider[Any, E]]])
    }

  def entities[E, P](
      index: Int, limit: Int, entityClass: Class[E], projection: Class[P],
      context: HibernateSearchContext,
      select: (JpaCriteriaQuery[P], JpaRoot[E], util.List[Expression[?]], HibernateCriteriaBuilder) => JpaCriteriaQuery[P]
  ): util.List[P] = {
    val start = QuerySurface.firstResult(index)
    val size = QuerySurface.maxResults(limit)
    val builder = entityManager.getCriteriaBuilder.asInstanceOf[HibernateCriteriaBuilder]
    val query = builder.createQuery(projection)
    val root = query.from(entityClass)
    val result = context(builder, query, root)
    val order = context.sort(builder, query, root, result.predicates, result.selection)
    select(query, root, result.selection, builder).where(result.predicates).orderBy(order)
    val typedQuery = entityManager.createQuery(query).setFirstResult(start).setMaxResults(size)
    result.parameters.forEach((name, value) => typedQuery.setParameter(name, value))
    typedQuery.getResultList
  }

  def count[E](entityClass: Class[E], context: HibernateSearchContext): Long = {
    val builder = entityManager.getCriteriaBuilder.asInstanceOf[HibernateCriteriaBuilder]
    val query = builder.createQuery(classOf[lang.Long])
    val root = query.from(entityClass)
    // Providers rebuild Criteria nodes against this count query's own root.
    val result = context(builder, query, root)
    query.select(builder.count()).where(result.predicates)
    val typedQuery = entityManager.createQuery(query)
    result.parameters.forEach((name, value) => typedQuery.setParameter(name, value))
    typedQuery.getSingleResult.longValue()
  }
}
