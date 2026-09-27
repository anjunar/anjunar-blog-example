package com.anjunar.blog.hibernate.search

import jakarta.persistence.criteria.{Expression, Predicate}

import java.util

final case class HibernateSearchContextResult(
    selection: util.List[Expression[?]],
    predicates: util.List[Predicate],
    parameters: util.Map[String, Any]
)
