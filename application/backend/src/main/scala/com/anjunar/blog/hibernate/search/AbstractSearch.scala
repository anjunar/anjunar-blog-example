package com.anjunar.blog.hibernate.search

// HTTP parsing belongs to the resource's input bean; index is a row offset.
abstract class AbstractSearch {
  def index: Int
  def limit: Int
}
