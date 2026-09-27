package com.anjunar.blog

import jakarta.enterprise.inject.spi.CDI
import jakarta.persistence.EntityManager

object RuntimeContext {
  def entityManager(): EntityManager =
    CDI.current().select(classOf[EntityManager]).get()
}
