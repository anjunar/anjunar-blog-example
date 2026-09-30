package com.anjunar.blog

import com.anjunar.json.mapper.provider.EntityProvider
import jakarta.inject.Inject
import jakarta.persistence.{Entity, EntityManager}
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.ext.{ParamConverter, ParamConverterProvider, Provider}

import java.lang.annotation.Annotation
import java.lang.reflect.Type
import java.util.UUID
import scala.compiletime.uninitialized

@Provider
class EntityParamConverterProvider extends ParamConverterProvider {
  @Inject var manager: EntityManager = uninitialized

  override def getConverter[T](rawType: Class[T], genericType: Type,
      annotations: Array[Annotation]): ParamConverter[T] =
    if (!classOf[EntityProvider].isAssignableFrom(rawType) || !rawType.isAnnotationPresent(classOf[Entity])) null
    else new ParamConverter[T] {
      override def fromString(value: String): T = {
        val id = try UUID.fromString(value) catch {
          case _: IllegalArgumentException => throw new NotFoundException()
        }
        Option(manager.find(rawType, id)).getOrElse(throw new NotFoundException())
      }

      override def toString(value: T): String =
        value.asInstanceOf[EntityProvider].id.toString
    }
}
