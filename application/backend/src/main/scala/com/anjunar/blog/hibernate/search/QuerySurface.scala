package com.anjunar.blog.hibernate.search

object QuerySurface {
  def firstResult(index: Int): Int = {
    require(index >= 0, "Search index must be nonnegative")
    index
  }

  def maxResults(limit: Int): Int = {
    require(limit >= 1 && limit <= 100, "Search limit must be between 1 and 100")
    limit
  }
}
