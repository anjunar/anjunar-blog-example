package com.anjunar.blog

import jakarta.enterprise.inject.spi.CDI
import jakarta.persistence.EntityManager

object RuntimeContext {
  def bean[T](clazz: Class[T]): T = {
    val instance = CDI.current().select(clazz)
    if (instance.isUnsatisfied) clazz.getDeclaredConstructor().newInstance()
    else instance.get()
  }

  def entityManager(): EntityManager =
    CDI.current().select(classOf[EntityManager]).get()
}
