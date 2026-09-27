package com.anjunar.blog

import com.anjunar.json.mapper.intermediate.JsonParser
import com.anjunar.json.mapper.intermediate.model.{JsonObject, JsonString}
import jakarta.mail.internet.InternetAddress
import jakarta.ws.rs.{BadRequestException, Consumes, WebApplicationException}
import jakarta.ws.rs.core.{MediaType, MultivaluedMap, Response}
import jakarta.ws.rs.ext.{MessageBodyReader, Provider}

import java.io.InputStream
import java.lang.annotation.Annotation
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

final case class EmailRequest(email: String)
final case class TokenPasswordRequest(token: String, password: String)

object AuthJson {
  def read(stream: InputStream, fields: Map[String, Int]): Map[String, String] = {
    val bytes = stream.readNBytes(4097)
    if (bytes.length > 4096) throw new WebApplicationException(Response.status(413).build())
    try {
      val json = JsonParser.parse(new String(bytes, UTF_8)) match {
        case value: JsonObject => value
        case _ => throw new BadRequestException()
      }
      if (json.value.keySet().asScala.toSet != fields.keySet) throw new BadRequestException()
      fields.map { (name, max) =>
        val value = json.value.get(name) match {
          case value: JsonString if value.value.length <= max => value.value
          case _ => throw new BadRequestException()
        }
        name -> value
      }
    } catch { case NonFatal(_) => throw new BadRequestException("Invalid authentication request") }
  }

  def email(value: String): String = {
    val email = Account.canonicalEmail(value)
    try {
      require(email.nonEmpty && email.length <= 254 && email.forall(c => c > ' ' && c < 127))
      val address = new InternetAddress(email, true)
      address.validate()
      require(address.getAddress == email && address.getPersonal == null && email.contains("@"))
      email
    } catch { case NonFatal(_) => throw new BadRequestException("Enter a valid email address") }
  }
}

@Provider
@Consumes(Array(MediaType.APPLICATION_JSON))
class EmailRequestReader extends MessageBodyReader[EmailRequest] {
  override def isReadable(clazz: Class[?], genericType: Type,
      annotations: Array[Annotation], mediaType: MediaType): Boolean = clazz == classOf[EmailRequest]

  override def readFrom(clazz: Class[EmailRequest], genericType: Type, annotations: Array[Annotation],
      mediaType: MediaType, headers: MultivaluedMap[String, String], stream: InputStream): EmailRequest =
    EmailRequest(AuthJson.email(AuthJson.read(stream, Map("email" -> 254))("email")))
}

@Provider
@Consumes(Array(MediaType.APPLICATION_JSON))
class TokenPasswordRequestReader extends MessageBodyReader[TokenPasswordRequest] {
  override def isReadable(clazz: Class[?], genericType: Type,
      annotations: Array[Annotation], mediaType: MediaType): Boolean = clazz == classOf[TokenPasswordRequest]

  override def readFrom(clazz: Class[TokenPasswordRequest], genericType: Type, annotations: Array[Annotation],
      mediaType: MediaType, headers: MultivaluedMap[String, String], stream: InputStream): TokenPasswordRequest = {
    val values = AuthJson.read(stream, Map("token" -> 43, "password" -> 128))
    if (!AccountToken.wellFormed(values("token")) || !PasswordHash.acceptable(values("password")))
      throw new BadRequestException("Use a valid link and a password with 15 to 128 characters")
    TokenPasswordRequest(values("token"), values("password"))
  }
}
