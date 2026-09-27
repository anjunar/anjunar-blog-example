package com.anjunar.blog

import com.anjunar.json.mapper.intermediate.JsonParser
import com.anjunar.json.mapper.intermediate.model.{JsonObject, JsonString}
import jakarta.ws.rs.{BadRequestException, Consumes, WebApplicationException}
import jakarta.ws.rs.core.{MediaType, MultivaluedMap, Response}
import jakarta.ws.rs.ext.{MessageBodyReader, Provider}

import java.io.InputStream
import java.lang.annotation.Annotation
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

final case class LoginRequest(email: String, password: String)

@Provider
@Consumes(Array(MediaType.APPLICATION_JSON))
class LoginRequestReader extends MessageBodyReader[LoginRequest] {
  override def isReadable(clazz: Class[?], genericType: Type,
      annotations: Array[Annotation], mediaType: MediaType): Boolean =
    clazz == classOf[LoginRequest]

  override def readFrom(clazz: Class[LoginRequest], genericType: Type,
      annotations: Array[Annotation], mediaType: MediaType,
      headers: MultivaluedMap[String, String], stream: InputStream): LoginRequest = {
    val bytes = stream.readNBytes(4097)
    if (bytes.length > 4096) throw new WebApplicationException(Response.status(413).build())
    try {
      val json = JsonParser.parse(new String(bytes, UTF_8)) match {
        case value: JsonObject => value
        case _ => throw new BadRequestException()
      }
      if (json.value.keySet().asScala.toSet != Set("email", "password")) throw new BadRequestException()
      def string(name: String, max: Int): String = json.value.get(name) match {
        case value: JsonString if value.value.length <= max => value.value
        case _ => throw new BadRequestException()
      }
      LoginRequest(Account.canonicalEmail(string("email", 254)), string("password", 128))
    } catch {
      case NonFatal(_) => throw new BadRequestException("Invalid sign-in request")
    }
  }
}
