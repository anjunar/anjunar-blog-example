package com.anjunar.blog

import com.anjunar.json.mapper.intermediate.model.{JsonArray, JsonBoolean, JsonNode, JsonNull, JsonNumber, JsonObject, JsonString}
import tools.jackson.core.{JacksonException, JsonParser, JsonToken, ObjectReadContext, StreamReadConstraints, StreamReadFeature}
import tools.jackson.core.json.JsonFactory

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.{CharacterCodingException, CodingErrorAction, StandardCharsets}
import java.util
import scala.util.Using

object RequestJson {
  val maxBytes = 1024 * 1024
  private val factory = JsonFactory.builder()
    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32)
      .maxStringLength(maxBytes).maxNumberLength(32).build())
    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()

  def read(stream: InputStream): JsonObject = {
    val bytes = stream.readNBytes(maxBytes + 1)
    if (bytes.length > maxBytes) throw new ApiProblem(413, "The JSON body exceeds 1 MiB.")
    try {
      val text = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString
      Using.resource(factory.createParser(ObjectReadContext.empty(), text)) { parser =>
        parser.nextToken()
        val result = node(parser) match {
          case value: JsonObject => value
          case _ => throw new ApiProblem(400, "The request body must be a JSON object.")
        }
        if (parser.nextToken() != null) throw new ApiProblem(400, "Send exactly one JSON object.")
        result
      }
    } catch {
      case _: JacksonException | _: CharacterCodingException =>
        throw new ApiProblem(400, "The request body is not valid UTF-8 JSON.")
    }
  }

  private def node(parser: JsonParser): JsonNode = parser.currentToken() match {
    case JsonToken.START_OBJECT =>
      val values = new util.LinkedHashMap[String, JsonNode]()
      while (parser.nextToken() != JsonToken.END_OBJECT) {
        if (parser.currentToken() != JsonToken.PROPERTY_NAME)
          throw new ApiProblem(400, "Invalid JSON object.")
        val name = parser.currentName()
        parser.nextToken()
        values.put(name, node(parser))
      }
      new JsonObject(values)
    case JsonToken.START_ARRAY =>
      val values = new util.ArrayList[JsonNode]()
      while (parser.nextToken() != JsonToken.END_ARRAY) values.add(node(parser))
      new JsonArray(values)
    case JsonToken.VALUE_STRING => new JsonString(parser.getString())
    case JsonToken.VALUE_NUMBER_INT | JsonToken.VALUE_NUMBER_FLOAT => new JsonNumber(parser.getString())
    case JsonToken.VALUE_TRUE => new JsonBoolean(true)
    case JsonToken.VALUE_FALSE => new JsonBoolean(false)
    case JsonToken.VALUE_NULL => new JsonNull()
    case _ => throw new ApiProblem(400, "Invalid JSON value.")
  }
}
