package com.anjunar.blog.hibernate.search

import com.anjunar.blog.hibernate.search.annotations.{RestPredicate, RestSort}
import com.anjunar.scala.universe.introspector.AnnotationIntrospector
import jakarta.enterprise.inject.Instance
import jakarta.json.bind.annotation.JsonbProperty
import jakarta.persistence.criteria.{Expression, Order, Predicate}
import org.hibernate.query.criteria.{HibernateCriteriaBuilder, JpaCriteriaQuery, JpaRoot}

import java.util
import scala.jdk.CollectionConverters.*

object SearchBeanReader {
  def read[E](
      search: AbstractSearch, builder: HibernateCriteriaBuilder,
      root: JpaRoot[E], query: JpaCriteriaQuery[?],
      instances: Instance[PredicateProvider[Any, E]]
  ): HibernateSearchContextResult = {
    val model = AnnotationIntrospector.createWithType(search.getClass, classOf[JsonbProperty])
    val predicates = new util.ArrayList[Predicate]()
    val selection = new util.ArrayList[Expression[?]]()
    val parameters = new util.HashMap[String, Any]()
    model.properties.foreach { property =>
      val annotation = property.findAnnotation(classOf[RestPredicate])
      if (annotation != null) {
        // A missing provider or unreadable field is a configuration error.
        // Never silently drop a predicate that could restrict public visibility.
        val provider = findProvider(instances, annotation.value())
        val value = property.get(search)
        if (value != null) {
          val name = if (annotation.name().isBlank) property.name else annotation.name()
          provider.build(Context(value, builder, predicates, root, query, selection, name, parameters))
        }
      }
    }
    HibernateSearchContextResult(selection, predicates, parameters)
  }

  def order[E](
      search: AbstractSearch, builder: HibernateCriteriaBuilder,
      root: JpaRoot[E], query: JpaCriteriaQuery[?],
      predicates: util.List[Predicate], selection: util.List[Expression[?]],
      instances: Instance[SortProvider[Any, E]]
  ): util.List[Order] = {
    val model = AnnotationIntrospector.createWithType(search.getClass, classOf[JsonbProperty])
    val properties = model.properties.filter(_.findAnnotation(classOf[RestSort]) != null).toList
    require(properties.size <= 1, "A search must declare at most one sort provider")
    properties.headOption match {
      case Some(property) =>
        val provider = findProvider(instances, property.findAnnotation(classOf[RestSort]).value())
        if (property.get(search) == null) new util.ArrayList[Order]()
        else {
          // As in the stack, sorting receives the entire search, not just the sort field.
          provider.sort(Context(search, builder, predicates, root, query, selection,
            property.name, new util.HashMap[String, Any]()))
        }
      case None => new util.ArrayList[Order]()
    }
  }

  private def findProvider[T](instances: Instance[T], providerClass: Class[?]): T = {
    val matches = instances.iterator().asScala.filter(providerClass.isInstance).toList
    if (matches.size != 1)
      throw new IllegalStateException(
        s"Expected one CDI search provider for ${providerClass.getName}, found ${matches.size}")
    matches.head
  }
}
