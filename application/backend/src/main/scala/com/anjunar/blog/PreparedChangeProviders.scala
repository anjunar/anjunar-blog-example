package com.anjunar.blog

import com.anjunar.json.mapper.PreparedChange
import jakarta.annotation.Priority
import jakarta.inject.Inject
import jakarta.servlet.http.HttpServletRequest
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.core.{Context, MediaType, MultivaluedMap}
import jakarta.ws.rs.ext.{MessageBodyReader, ParamConverter, ParamConverterProvider, Provider}

import java.io.InputStream
import java.lang.annotation.Annotation
import java.lang.reflect.Type
import scala.compiletime.uninitialized

@Provider
@Priority(3800)
@Consumes(Array(MediaType.APPLICATION_JSON))
class PreparedChangeReader extends MessageBodyReader[PreparedChange[BlogPost]] {
  @Inject var changes: PreparedChanges = uninitialized
  override def isReadable(clazz: Class[?], genericType: Type, annotations: Array[Annotation],
      mediaType: MediaType): Boolean = PreparedChanges.supports(clazz, genericType)

  override def readFrom(clazz: Class[PreparedChange[BlogPost]], genericType: Type,
      annotations: Array[Annotation], mediaType: MediaType, headers: MultivaluedMap[String, String],
      stream: InputStream): PreparedChange[BlogPost] =
    changes.create(RequestJson.read(stream))
}

@Provider
class PreparedChangeParamConverter extends ParamConverterProvider {
  @Inject var changes: PreparedChanges = uninitialized
  @Context var request: HttpServletRequest = uninitialized

  override def getConverter[T](clazz: Class[T], genericType: Type,
      annotations: Array[Annotation]): ParamConverter[T] =
    if (!PreparedChanges.supports(clazz, genericType)) null
    else new ParamConverter[T] {
      override def fromString(id: String): T =
        changes.update(id, RequestJson.read(request.getInputStream)).asInstanceOf[T]
      override def toString(value: T): String =
        value.asInstanceOf[PreparedChange[BlogPost]].getEntity().id.toString
    }
}
