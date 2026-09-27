package com.anjunar.blog

import jakarta.ws.rs.Consumes
import jakarta.ws.rs.core.{MediaType, MultivaluedMap}
import jakarta.ws.rs.ext.{MessageBodyReader, Provider}

import java.io.InputStream
import java.lang.annotation.Annotation
import java.lang.reflect.Type

final case class LoginRequest(email: String, password: String)

@Provider
@Consumes(Array(MediaType.APPLICATION_JSON))
class LoginRequestReader extends MessageBodyReader[LoginRequest] {
  override def isReadable(clazz: Class[?], genericType: Type,
      annotations: Array[Annotation], mediaType: MediaType): Boolean = clazz == classOf[LoginRequest]

  override def readFrom(clazz: Class[LoginRequest], genericType: Type, annotations: Array[Annotation],
      mediaType: MediaType, headers: MultivaluedMap[String, String], stream: InputStream): LoginRequest = {
    val fields = AuthJson.read(stream, Map("email" -> 254, "password" -> 128))
    LoginRequest(Account.canonicalEmail(fields("email")), fields("password"))
  }
}
