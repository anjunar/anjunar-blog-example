package com.anjunar.blog

import com.anjunar.json.mapper.{ErrorRequest, ErrorRequestException}
import com.anjunar.json.mapper.intermediate.JsonParser
import com.anjunar.json.mapper.intermediate.model.{JsonArray, JsonObject}
import org.hibernate.exception.ConstraintViolationException
import org.scalatest.funsuite.AnyFunSuite

import java.sql.SQLException
import java.util

class ProblemResponsesSpec extends AnyFunSuite {
  test("wrapped mapper errors retain field paths in a problem response") {
    val error = new ErrorRequestException(util.List.of(
      new ErrorRequest(util.List.of[Any]("summary"), "size must be between 0 and 300")))
    val response = ProblemResponses.render(new RuntimeException(error), "/service/editorial/posts")
    assert(response.getStatus == 400 && response.getMediaType.toString == Problem.mediaType)
    val json = JsonParser.parse(response.getEntity.toString).asInstanceOf[JsonObject]
    assert(json.getString("type") == Problem.validation)
    val fields = json.value.get("errors").asInstanceOf[JsonArray]
    assert(fields.value.size() == 1)
  }

  test("both adopted and explicitly named slug constraints become safe field conflicts") {
    for (name <- Seq("blog_post_slug_key", "uq_blog_post_slug")) {
      val error = new ConstraintViolationException("sensitive driver detail",
        new SQLException("private value", "23505"), "insert into private_table", name)
      val response = ProblemResponses.render(new RuntimeException(error), "/service/editorial/posts")
      assert(response.getStatus == 409)
      val body = response.getEntity.toString
      assert(body.contains("slug") && !body.contains("private") && !body.contains("sensitive"))
    }
  }
}
