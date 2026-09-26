package com.anjunar.blog

import jakarta.validation.Validation
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

import java.time.Instant
import scala.jdk.CollectionConverters.*

class BlogPostValidationSpec extends AnyFunSuite with BeforeAndAfterAll {
  private val factory = Validation.buildDefaultValidatorFactory()
  private val validator = factory.getValidator
  private val publicationTime = Instant.parse("2026-09-27T10:00:00Z")

  override protected def afterAll(): Unit =
    try factory.close()
    finally super.afterAll()

  private def draft(): BlogPost = {
    val post = new BlogPost()
    post.slug = "our-first-post"
    post.title = "Our first post"
    post
  }

  private def violations(post: BlogPost): Set[String] =
    validator.validate(post).asScala.map(_.getPropertyPath.toString).toSet

  test("a draft can have empty content and has no generated identity or version yet") {
    val post = draft()
    assert(violations(post).isEmpty)
    assert(post.status == BlogPostStatus.DRAFT)
    assert(post.publishedAt == null)
    assert(post.id == null)
    assert(post.version == null)
  }

  test("field annotations reject missing values, invalid slugs, and oversized text") {
    val cases: Seq[(String, BlogPost => Unit)] = Seq(
      "title" -> (post => post.title = null),
      "title" -> (post => post.title = "   "),
      "title" -> (post => post.title = "ab"),
      "title" -> (post => post.title = "a" * 181),
      "slug" -> (post => post.slug = null),
      "slug" -> (post => post.slug = "ab"),
      "slug" -> (post => post.slug = "Not a slug"),
      "slug" -> (post => post.slug = "double--hyphen"),
      "slug" -> (post => post.slug = "a" * 221),
      "content" -> (post => post.content = null),
      "content" -> (post => post.content = "a" * 100001),
      "status" -> (post => post.status = null)
    )
    for ((fieldName, change) <- cases) {
      val post = draft()
      change(post)
      assert(violations(post).contains(fieldName), s"Missing constraint on $fieldName")
    }
  }

  test("publishing and retracting keep status and publication time together") {
    val post = draft()
    post.content = "The first paragraph."
    post.publish(publicationTime)
    assert(post.status == BlogPostStatus.PUBLISHED)
    assert(post.publishedAt == publicationTime)
    assert(violations(post).isEmpty)
    post.retract()
    assert(post.status == BlogPostStatus.DRAFT)
    assert(post.publishedAt == null)
    assert(post.content == "The first paragraph.")
    assert(violations(post).isEmpty)
  }

  test("invalid transitions leave the previous publication state unchanged") {
    val post = draft()
    intercept[IllegalArgumentException](post.publish(publicationTime))
    assert(post.status == BlogPostStatus.DRAFT && post.publishedAt == null)
    post.content = "Ready to publish."
    intercept[IllegalArgumentException](post.publish(null))
    intercept[IllegalArgumentException](post.retract())
    post.publish(publicationTime)
    intercept[IllegalArgumentException](post.publish(publicationTime.plusSeconds(60)))
    assert(post.publishedAt == publicationTime)
  }

  test("validation also catches inconsistent state assigned without transition methods") {
    val post = draft()
    post.status = BlogPostStatus.PUBLISHED
    assert(violations(post).contains("publicationConsistent"))
    post.publishedAt = publicationTime
    assert(violations(post).contains("publicationConsistent"))
    post.content = "Ready to publish."
    assert(violations(post).isEmpty)
    post.status = BlogPostStatus.DRAFT
    assert(violations(post).contains("publicationConsistent"))
  }
}
