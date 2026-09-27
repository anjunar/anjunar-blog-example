package com.anjunar.blog

import jakarta.annotation.{PostConstruct, PreDestroy}
import jakarta.enterprise.context.ApplicationScoped
import jakarta.validation.{ConstraintViolationException, Validation, Validator, ValidatorFactory}

@ApplicationScoped
class PostValidation {
  private var factory: ValidatorFactory = null
  @PostConstruct def initialize(): Unit = factory = Validation.buildDefaultValidatorFactory()
  def validator: Validator = factory.getValidator

  def requireValid(post: BlogPost): Unit = {
    val violations = validator.validate(post)
    if (!violations.isEmpty) throw new ConstraintViolationException(violations)
  }

  @PreDestroy def close(): Unit = if (factory != null) factory.close()
}
