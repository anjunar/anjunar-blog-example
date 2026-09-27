package com.anjunar.blog.hibernate.search

import jakarta.persistence.criteria.Order

import java.util

trait SortProvider[V, E] {
  def sort(context: Context[V, E]): util.List[Order]
}
