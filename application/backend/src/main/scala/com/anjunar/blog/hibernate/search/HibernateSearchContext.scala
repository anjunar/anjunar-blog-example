package com.anjunar.blog.hibernate.search

import jakarta.persistence.criteria.{Expression, Order, Predicate}
import org.hibernate.query.criteria.{HibernateCriteriaBuilder, JpaCriteriaQuery, JpaRoot}

import java.util

trait HibernateSearchContext {
  def apply[E](
      builder: HibernateCriteriaBuilder, query: JpaCriteriaQuery[?], root: JpaRoot[E]
  ): HibernateSearchContextResult

  def sort[E](
      builder: HibernateCriteriaBuilder, query: JpaCriteriaQuery[?], root: JpaRoot[E],
      predicates: util.List[Predicate], selection: util.List[Expression[?]]
  ): util.List[Order]
}
