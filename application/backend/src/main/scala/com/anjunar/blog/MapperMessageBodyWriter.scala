package com.anjunar.blog

import com.anjunar.json.mapper.JsonMapper
import com.anjunar.json.mapper.provider.DTO
import com.anjunar.scala.universe.TypeResolver
import jakarta.annotation.Priority
import jakarta.ws.rs.Produces
import jakarta.ws.rs.container.ResourceInfo
import jakarta.ws.rs.core.{Context, MediaType, MultivaluedMap}
import jakarta.ws.rs.ext.{MessageBodyWriter, Provider}

import java.io.OutputStream
import java.lang.annotation.Annotation
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets
import scala.compiletime.uninitialized

@Provider
@Priority(3900)
@Produces(Array(MediaType.APPLICATION_JSON))
class MapperMessageBodyWriter extends MessageBodyWriter[Any] {
  @Context
  var resource: ResourceInfo = uninitialized

  override def isWriteable(clazz: Class[?], genericType: Type,
      annotations: Array[Annotation], mediaType: MediaType): Boolean =
    classOf[DTO].isAssignableFrom(clazz)

  override def writeTo(body: Any, clazz: Class[?], genericType: Type,
      annotations: Array[Annotation], mediaType: MediaType,
      headers: MultivaluedMap[String, Object], stream: OutputStream): Unit = {
    val annotation = resource.getResourceMethod.getAnnotation(classOf[EntityGraph])
    val graph =
      if (annotation == null) null
      else RuntimeContext.entityManager().getEntityGraph(annotation.value())
    val targetType = if (genericType == null) clazz else genericType
    val json = JsonMapper.serialize(body, TypeResolver.resolve(targetType), graph,
      [T] => (ruleClass: Class[T]) => RuntimeContext.bean(ruleClass))
    stream.write(json.getBytes(StandardCharsets.UTF_8))
  }
}
