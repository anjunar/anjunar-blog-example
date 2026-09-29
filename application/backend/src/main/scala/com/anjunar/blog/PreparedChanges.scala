package com.anjunar.blog

import com.anjunar.json.mapper.{JsonMapper, PreparedChange}
import com.anjunar.json.mapper.intermediate.model.{JsonNull, JsonNumber, JsonObject, JsonString}
import com.anjunar.json.mapper.provider.EntityProvider
import com.anjunar.json.mapper.schema.EntitySchema
import com.anjunar.scala.universe.TypeResolver
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.persistence.{EntityManager, LockModeType}
import jakarta.validation.Validator
import jakarta.ws.rs.{ForbiddenException, NotFoundException}

import java.lang.reflect.{ParameterizedType, Type}
import java.util.UUID
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

object PreparedChanges {
  private val types: Set[Class[?]] = Set(classOf[BlogPost], classOf[BlogTag], classOf[Account], classOf[BlogPostTranslation])

  def entityClass(genericType: Type): Option[Class[? <: EntityProvider]] = genericType match {
    case value: ParameterizedType if value.getActualTypeArguments.length == 1 =>
      value.getActualTypeArguments.head match {
        case clazz: Class[?] if types.contains(clazz) => Some(clazz.asSubclass(classOf[EntityProvider]))
        case _ => None
      }
    case _ => None
  }

  def supports(clazz: Class[?], genericType: Type): Boolean =
    clazz == classOf[PreparedChange[?]] && entityClass(genericType).nonEmpty

  def schema(clazz: Class[?]): EntitySchema[?] =
    if (clazz == classOf[BlogPost]) BlogPost.schema
    else if (clazz == classOf[BlogTag]) BlogTag.schema
    else if (clazz == classOf[Account]) Account.schema
    else if (clazz == classOf[BlogPostTranslation]) BlogPostTranslation.schema
    else throw new IllegalArgumentException("Unsupported entity type")

  def graph(clazz: Class[?]): String =
    if (clazz == classOf[BlogPost]) "BlogPost.detail"
    else if (clazz == classOf[BlogTag]) "BlogTag.detail"
    else if (clazz == classOf[Account]) "Account.author"
    else if (clazz == classOf[BlogPostTranslation]) "BlogPostTranslation.detail"
    else throw new IllegalArgumentException("Unsupported entity type")
}

@RequestScoped
class PreparedChanges {
  @Inject var manager: EntityManager = uninitialized
  @Inject var access: PostAccess = uninitialized
  @Inject var caller: CallerAccess = uninitialized
  @Inject var identity: SessionIdentity = uninitialized
  @Inject var references: ReferenceAccess = uninitialized
  @Inject var validator: Validator = uninitialized

  def create(json: JsonObject): PreparedChange[BlogPost] = create(json, classOf[BlogPost])
  def update(id: String, json: JsonObject): PreparedChange[BlogPost] = update(id, json, classOf[BlogPost])

  def create[E <: EntityProvider](json: JsonObject, clazz: Class[E]): PreparedChange[E] = {
    val entity: EntityProvider =
      if (clazz == classOf[BlogPost]) {
        val post = new BlogPost()
        if (!json.value.containsKey("author")) post.author = identity.requireAccount()
        post
      } else if (clazz == classOf[BlogTag]) new BlogTag()
      else if (clazz == classOf[BlogPostTranslation]) new BlogPostTranslation()
      else throw new ApiProblem(400, "Accounts are created through registration or administrator bootstrap.")
    Option(json.value.get("version")).foreach {
      case value: JsonNumber if value.value == "-1" => ()
      case _ => Problem.invalidField("version", "A new entity has no saved version yet.")
    }
    prepare(json, clazz.cast(entity), clazz)
  }

  def update[E <: EntityProvider](id: String, json: JsonObject, clazz: Class[E]): PreparedChange[E] = {
    if (!caller.administrator) throw new ForbiddenException()
    val uuid = try UUID.fromString(id) catch { case _: IllegalArgumentException => throw new NotFoundException() }
    // Lock while loading, before comparing versions, so concurrent requests see the latest row.
    val entity = manager.find(clazz, uuid, LockModeType.PESSIMISTIC_WRITE)
    if (entity == null) throw new NotFoundException()
    entity match {
      case post: BlogPost if !access.canRead(post) => throw new NotFoundException()
      case account: Account if account.locked || account.role != "ADMIN" => throw new NotFoundException()
      case _ => ()
    }
    EntityVersions.requireCurrent(entity, json)
    prepare(json, entity, clazz)
  }

  private def prepare[E <: EntityProvider](json: JsonObject, entity: E, clazz: Class[E]): PreparedChange[E] = {
    val schema = PreparedChanges.schema(clazz)
    val fields = if (clazz == classOf[Account]) Set("id", "version", "displayName")
      else if (clazz == classOf[BlogPost]) schema.properties.keySet.toSet -- Set("inlineMedia", "translations", "translation", "contentLocale", "availableLocales")
      else if (clazz == classOf[BlogPostTranslation]) Set("id", "version", "title", "summary", "content")
      else schema.properties.keySet.toSet
    val unknown = json.value.keySet().asScala.toSet -- fields - "@type"
    if (unknown.nonEmpty) Problem.invalidField(unknown.toSeq.sorted.head, "Unknown entity field.")
    Option(json.value.get("@type")).foreach {
      case value: JsonString if value.value == clazz.getSimpleName => ()
      case _ => Problem.invalidField("@type", s"Expected ${clazz.getSimpleName}.")
    }
    Option(json.value.get("id")).foreach {
      case _: JsonNull if entity.id == null => ()
      case value: JsonString if entity.id == null && value.value.isEmpty => ()
      case value: JsonString if entity.id != null && value.value == entity.id.toString => ()
      case _ => Problem.invalidField("id", "The request cannot change the entity identity.")
    }
    // The mapper dispatches by node shape; scalar type checking belongs at the HTTP boundary.
    val entityType = manager.getMetamodel.entity(clazz)
    json.value.asScala.foreach { (name, value) =>
      if (fields.contains(name) && entityType.getAttribute(name).getJavaType == classOf[String])
        value match {
          case _: JsonString | _: JsonNull => ()
          case _ => Problem.invalidField(name, "Expected a string or null.")
        }
    }
    if (clazz == classOf[BlogPost]) references.checkPostInput(json)
    JsonMapper.prepare(json, entity, TypeResolver.resolve(clazz),
      manager.getEntityGraph(PreparedChanges.graph(clazz)), references,
      [T] => (rule: Class[T]) => RuntimeContext.bean(rule), validator)
  }
}
