package com.anjunar.blog

import jakarta.annotation.{PostConstruct, PreDestroy}
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.validation.{Validation, Validator, ValidatorFactory}

@ApplicationScoped
class ValidationProducer {
  private var factory: ValidatorFactory = null
  @PostConstruct def initialize(): Unit = factory = Validation.buildDefaultValidatorFactory()

  @Produces def validator: Validator = factory.getValidator

  @PreDestroy def close(): Unit = if (factory != null) factory.close()
}