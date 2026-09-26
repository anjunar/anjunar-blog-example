package com.anjunar.blog

import jakarta.enterprise.event.Observes
import jakarta.enterprise.inject.spi.{Extension, ProcessManagedBean}
import jakarta.ws.rs.Path
import jakarta.ws.rs.ext.Provider

import java.util
import java.util.concurrent.ConcurrentHashMap

class RestComponentsExtension extends Extension {

  private val components = ConcurrentHashMap.newKeySet[Class[?]]()

  def collect(@Observes event: ProcessManagedBean[?]): Unit = {
    val beanType = event.getAnnotatedBeanClass
    if (beanType.isAnnotationPresent(classOf[Path]) ||
        beanType.isAnnotationPresent(classOf[Provider])) {
      components.add(beanType.getJavaClass)
    }
  }

  def classes: util.Set[Class[?]] = util.Set.copyOf(components)

}
