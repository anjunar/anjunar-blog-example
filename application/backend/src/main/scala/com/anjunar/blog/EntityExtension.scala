package com.anjunar.blog

import jakarta.enterprise.context.spi.CreationalContext
import jakarta.enterprise.event.Observes
import jakarta.enterprise.inject.{Any as AnyQualifier, Default}
import jakarta.enterprise.inject.spi.{AfterBeanDiscovery, Extension, ProcessAnnotatedType, WithAnnotations}
import jakarta.inject.Singleton
import jakarta.persistence.Entity

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*

class EntityExtension extends Extension {
  private val entityClasses = ConcurrentHashMap.newKeySet[Class[?]]()

  def collect(@Observes @WithAnnotations(Array(classOf[Entity])) event: ProcessAnnotatedType[?]): Unit = {
    val annotatedType = event.getAnnotatedType
    if (annotatedType.isAnnotationPresent(classOf[Entity])) {
      entityClasses.add(annotatedType.getJavaClass)
      // Hibernate manages entity instances; CDI only discovers their classes.
      event.veto()
    }
  }

  def registerRegistry(@Observes event: AfterBeanDiscovery): Unit = {
    val discovered = entityClasses.asScala.toList.sortBy(_.getName)
    event.addBean[EntityRegistry]()
      .beanClass(classOf[EntityRegistry])
      .types(classOf[EntityRegistry], classOf[Object])
      .scope(classOf[Singleton])
      .qualifiers(Default.Literal.INSTANCE, AnyQualifier.Literal.INSTANCE)
      .createWith((_: CreationalContext[EntityRegistry]) => new EntityRegistry(discovered))
  }
}