package com.anjunar.blog

import com.anjunar.json.mapper.ErrorRequest
import com.anjunar.json.mapper.intermediate.model.{JsonArray, JsonNode, JsonNumber, JsonObject, JsonString}
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response

import java.util
import scala.jdk.CollectionConverters.*

final class ApiProblem(val status: Int, val detail: String, val kind: String = "about:blank",
    val errors: Seq[ErrorRequest] = Seq.empty)
    extends WebApplicationException(Response.status(status).build())

object Problem {
  val mediaType = "application/problem+json"
  val validation = "/problems/validation"
  val conflict = "/problems/conflict"

  def invalidField(name: String, message: String): Nothing =
    throw new ApiProblem(400, "The request contains invalid values.", validation,
      Seq(new ErrorRequest(util.List.of[Any](name), message)))

  def response(status: Int, detail: String, instance: String, kind: String = "about:blank",
      errors: Seq[ErrorRequest] = Seq.empty, errorId: Option[String] = None): Response = {
    val title = Option(Response.Status.fromStatusCode(status)).map(_.getReasonPhrase).getOrElse(s"HTTP $status")
    val body = new JsonObject().put("type", kind).put("title", title)
      .put("status", Int.box(status)).put("detail", detail).put("instance", instance)
    if (errors.nonEmpty) {
      val fields = errors.map { error =>
        val path = error.path.asScala.map {
          case value: Number => new JsonNumber(value.toString): JsonNode
          case value => new JsonString(String.valueOf(value)): JsonNode
        }
        new JsonObject().put("path", new JsonArray(new util.ArrayList[JsonNode](path.asJava)))
          .put("message", error.message): JsonNode
      }
      body.put("errors", new JsonArray(new util.ArrayList[JsonNode](fields.asJava)))
    }
    errorId.foreach(value => body.put("errorId", value))
    // A plain encoded body keeps error serialization independent of entity graphs and CDI rules.
    Response.status(status).`type`(mediaType).entity(body.encode())
      .header("Cache-Control", "no-store").build()
  }
}
