package com.anjunar.blog

import com.anjunar.json.mapper.schema.VisibilityRule
import com.anjunar.scala.universe.introspector.AbstractProperty
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response

import scala.compiletime.uninitialized

@RequestScoped
class PostAccess {
  @Inject var caller: CallerAccess = uninitialized

  def canRead(post: BlogPost): Boolean =
    post != null && (post.status == BlogPostStatus.PUBLISHED || caller.administrator)

  def canEdit(post: BlogPost): Boolean = post != null && caller.administrator

  def canPublish(post: BlogPost): Boolean =
    canEdit(post) && post.status == BlogPostStatus.DRAFT &&
      post.content != null && !post.content.isBlank

  def canRetract(post: BlogPost): Boolean =
    canEdit(post) && post.status == BlogPostStatus.PUBLISHED

  def requireTransition(allowed: Boolean): Unit =
    if (!allowed) throw new WebApplicationException(Response.status(409).build())
}

@RequestScoped
class PostReadRule extends VisibilityRule[BlogPost] {
  @Inject var access: PostAccess = uninitialized
  override def isVisible(post: BlogPost, property: AbstractProperty): Boolean = access.canRead(post)
  override def isWriteable(post: BlogPost, property: AbstractProperty): Boolean = false
}

@RequestScoped
class PostEditRule extends VisibilityRule[BlogPost] {
  @Inject var access: PostAccess = uninitialized
  override def isVisible(post: BlogPost, property: AbstractProperty): Boolean = access.canRead(post)
  override def isWriteable(post: BlogPost, property: AbstractProperty): Boolean = access.canEdit(post)
}
