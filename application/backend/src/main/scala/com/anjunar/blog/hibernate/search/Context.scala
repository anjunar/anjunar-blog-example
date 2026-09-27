package com.anjunar.blog.hibernate.search

import jakarta.persistence.criteria.{Expression, Predicate}
import org.hibernate.query.criteria.{HibernateCriteriaBuilder, JpaCriteriaQuery, JpaRoot}

import java.util

final case class Context[V, E](
    value: V,
    builder: HibernateCriteriaBuilder,
    predicates: util.List[Predicate],
    root: JpaRoot[E],
    query: JpaCriteriaQuery[?],
    selection: util.List[Expression[?]],
    name: String,
    parameters: util.Map[String, Any]
)
