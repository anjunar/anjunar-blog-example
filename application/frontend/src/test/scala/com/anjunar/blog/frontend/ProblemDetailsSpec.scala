package com.anjunar.blog.frontend

import org.scalatest.funsuite.AnyFunSuite

class ProblemDetailsSpec extends AnyFunSuite {
  test("HTTP failures retain typed field errors for the upcoming forms") {
    val failure = HttpJson.failure(400, "application/problem+json; charset=utf-8",
      """{"type":"/problems/validation","title":"Bad Request","status":400,"detail":"Invalid values.",
        |"instance":"/service/editorial/posts","errors":[{"path":["title"],"message":"must not be blank"}]}""".stripMargin)
    val problem = failure.problem.get
    assert(failure.status == 400 && problem.problemType == "/problems/validation")
    assert(problem.errors.head.path == Seq("title") && problem.errors.head.message == "must not be blank")
  }

  test("an inconsistent body cannot override the actual HTTP status") {
    val failure = HttpJson.failure(403, "application/problem+json",
      """{"title":"OK","status":200}""")
    assert(failure.status == 403 && failure.problem.isEmpty)
  }

  test("missing or malformed problem bodies retain the original status") {
    for ((contentType, body) <- Seq(("application/json", "{}"), ("text/html", "<html>error</html>"),
      ("application/problem+json", "{broken"), ("application/problem+json", "{}"),
      ("application/problem+json", "null"), ("application/problem+json", """{"status":503,"title":null}"""))) {
      val failure = HttpJson.failure(503, contentType, body)
      assert(failure.status == 503 && failure.problem.isEmpty)
    }
  }
}
