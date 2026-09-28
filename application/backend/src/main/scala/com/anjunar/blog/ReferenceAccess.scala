package com.anjunar.blog

import com.anjunar.json.mapper.EntityLoader
import com.anjunar.json.mapper.intermediate.model.{JsonArray, JsonNull, JsonObject, JsonString}
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.persistence.{EntityManager, LockModeType}
import jakarta.ws.rs.ForbiddenException

import java.util.UUID
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

@RequestScoped
class ReferenceAccess extends EntityLoader {
  @Inject var manager: EntityManager = uninitialized
  @Inject var caller: CallerAccess = uninitialized
  @Inject var media: MediaLifecycle = uninitialized

  override def load(id: UUID, clazz: Class[?]): Any = {
    if (!caller.administrator) throw new ForbiddenException()
    if (clazz == classOf[Account]) {
      val account = manager.find(classOf[Account], id)
      if (account == null || account.locked || account.role != "ADMIN")
        Problem.invalidField("author", "Choose an available author.")
      account
    } else if (clazz == classOf[Media]) {
      val image = manager.find(classOf[Media], id, LockModeType.PESSIMISTIC_WRITE)
      if (image == null || !media.canUse(image))
        Problem.invalidField("coverImage", "Choose an available image that you may use.")
      image
    } else if (clazz == classOf[BlogTag]) {
      val tag = manager.find(classOf[BlogTag], id)
      if (tag == null) Problem.invalidField("tags", "Choose an available tag.")
      tag
    } else {
      throw new ApiProblem(400, "This entity type cannot be referenced.")
    }
  }

  // These endpoints accept links to shared entities, not nested edits or new children.
  def checkPostInput(json: JsonObject): Unit = {
    Option(json.value.get("coverImage")).foreach {
      case _: JsonNull => ()
      case value => referenceId(value, "coverImage")
    }
    Option(json.value.get("author")).foreach {
      case _: JsonNull => ()
      case value => referenceId(value, "author")
    }
    Option(json.value.get("tags")).foreach {
      case array: JsonArray =>
        val ids = array.value.asScala.map(value => referenceId(value, "tags"))
        if (ids.distinct.size != ids.size) Problem.invalidField("tags", "Choose each tag only once.")
      case _ => Problem.invalidField("tags", "Expected an array of tag references; use [] to clear it.")
    }
  }

  private def referenceId(value: Any, field: String): UUID = value match {
    case obj: JsonObject if obj.value.keySet().asScala.toSet == Set("id") =>
      obj.value.get("id") match {
        case id: JsonString =>
          val parsed = try UUID.fromString(id.value) catch {
            case _: IllegalArgumentException => Problem.invalidField(field, "Reference id must be a UUID.")
          }
          if (!parsed.toString.equalsIgnoreCase(id.value))
            Problem.invalidField(field, "Reference id must be a canonical UUID.")
          parsed
        case _ => Problem.invalidField(field, "Reference id must be a UUID string.")
      }
    case _ => Problem.invalidField(field, "Send only the id of an existing entity.")
  }
}
