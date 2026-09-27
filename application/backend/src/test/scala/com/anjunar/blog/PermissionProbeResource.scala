package com.anjunar.blog

import com.anjunar.json.mapper.JsonMapper
import com.anjunar.json.mapper.intermediate.JsonParser
import com.anjunar.json.mapper.EntityLoader
import com.anjunar.scala.universe.TypeResolver
import jakarta.annotation.security.PermitAll
import jakarta.enterprise.context.RequestScoped
import jakarta.validation.Validation
import jakarta.ws.rs.{GET, Path, Produces}
import jakarta.ws.rs.core.MediaType

import java.util.UUID
import scala.util.Using

// Test-only: exercise mapper writes under real anonymous, reader and admin requests.
// This object is never persisted; no production update endpoint exists yet.
@PermitAll
@RequestScoped
@Path("/_test/permissions")
class PermissionProbeResource {
  @GET
  @Produces(Array(MediaType.TEXT_PLAIN))
  def rules(): String = {
    val post = new BlogPost()
    post.slug = "permission-probe"
    post.title = "Original title"
    post.content = "Original body"
    val input = JsonParser.parse(
      """{"title":"Changed title","version":99,"status":"PUBLISHED","publishedAt":"2026-09-27T10:00:00Z"}""")
    val loader = new EntityLoader {
      override def load(id: UUID, clazz: Class[?]): Any = throw new AssertionError("No references")
    }
    Using.resource(Validation.buildDefaultValidatorFactory()) { factory =>
      JsonMapper.deserialize(input, post, TypeResolver.resolve(classOf[BlogPost]), null, loader,
        [T] => (clazz: Class[T]) => RuntimeContext.bean(clazz), factory.getValidator)
    }
    s"${post.title}|${post.version}|${post.status}|${post.publishedAt}"
  }
}
