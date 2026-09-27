package com.anjunar.blog

import com.anjunar.json.mapper.{EntityLoader, JsonMapper, PreparedChange}
import com.anjunar.json.mapper.intermediate.model.{JsonNull, JsonNumber, JsonObject, JsonString}
import com.anjunar.scala.universe.TypeResolver
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.persistence.{EntityManager, LockModeType}
import jakarta.ws.rs.NotFoundException

import java.lang.reflect.{ParameterizedType, Type}
import java.util.UUID
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

object PreparedChanges {
  def supports(clazz: Class[?], genericType: Type): Boolean =
    clazz == classOf[PreparedChange[?]] && (genericType match {
      case value: ParameterizedType => value.getActualTypeArguments.toSeq == Seq(classOf[BlogPost])
      case _ => false
    })
}

@RequestScoped
class PreparedChanges {
  @Inject var manager: EntityManager = uninitialized
  @Inject var access: PostAccess = uninitialized
  @Inject var validation: PostValidation = uninitialized

  def create(json: JsonObject): PreparedChange[BlogPost] = {
    val post = new BlogPost()
    Option(json.value.get("version")).foreach {
      case value: JsonNumber if value.value == "-1" => ()
      case _ => Problem.invalidField("version", "A new post has no saved version yet.")
    }
    prepare(json, post)
  }

  def update(id: String, json: JsonObject): PreparedChange[BlogPost] = {
    val uuid = try UUID.fromString(id) catch { case _: IllegalArgumentException => throw new NotFoundException() }
    // Lock while loading, before comparing versions, so concurrent requests see the latest row.
    val post = manager.find(classOf[BlogPost], uuid, LockModeType.PESSIMISTIC_WRITE)
    if (post == null || !access.canRead(post)) throw new NotFoundException()
    EntityVersions.requireCurrent(post, json)
    prepare(json, post)
  }

  private def prepare(json: JsonObject, post: BlogPost): PreparedChange[BlogPost] = {
    val unknown = json.value.keySet().asScala.toSet -- BlogPost.schema.properties.keySet - "@type"
    if (unknown.nonEmpty) Problem.invalidField(unknown.toSeq.sorted.head, "Unknown post field.")
    Option(json.value.get("@type")).foreach {
      case value: JsonString if value.value == "BlogPost" => ()
      case _ => Problem.invalidField("@type", "Expected BlogPost.")
    }
    Option(json.value.get("id")).foreach {
      case _: JsonNull if post.id == null => ()
      case value: JsonString if post.id == null && value.value.isEmpty => ()
      case value: JsonString if post.id != null && value.value == post.id.toString => ()
      case _ => Problem.invalidField("id", "The request cannot change the post identity.")
    }
    // The mapper dispatches by JSON node shape. Reject object/number input for String attributes
    // at the HTTP boundary instead of relying on coercion or reflective assignment failures.
    val entityType = manager.getMetamodel.entity(classOf[BlogPost])
    json.value.asScala.foreach { (name, value) =>
      if (BlogPost.schema.properties.contains(name) && entityType.getAttribute(name).getJavaType == classOf[String])
        value match {
          case _: JsonString | _: JsonNull => ()
          case _ => Problem.invalidField(name, "Expected a string or null.")
        }
    }
    val noReferences = new EntityLoader {
      override def load(id: UUID, clazz: Class[?]): Any =
        throw new ApiProblem(400, "This post contract does not accept entity references.")
    }
    JsonMapper.prepare(json, post, TypeResolver.resolve(classOf[BlogPost]),
      manager.getEntityGraph("BlogPost.detail"), noReferences,
      [T] => (clazz: Class[T]) => RuntimeContext.bean(clazz), validation.validator)
  }
}
