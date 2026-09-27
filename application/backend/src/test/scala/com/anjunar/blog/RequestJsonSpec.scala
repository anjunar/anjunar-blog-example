package com.anjunar.blog

import org.scalatest.funsuite.AnyFunSuite

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets.UTF_8

class RequestJsonSpec extends AnyFunSuite {
  private def read(text: String) = RequestJson.read(new ByteArrayInputStream(text.getBytes(UTF_8)))

  test("the boundary retains Unicode and exact numeric tokens") {
    val json = read("""{"title":"Grüße 👋","version":9007199254740993,"summary":null}""")
    assert(json.getString("title") == "Grüße 👋")
    assert(json.value.get("version").value == "9007199254740993")
    assert(json.value.containsKey("summary"))
  }

  test("duplicate decoded keys and unescaped control characters are rejected") {
    Seq("""{"version":0,"version":1}""", """{"title":"a","\u0074itle":"b"}""",
      "{\"title\":\"a\nb\"}").foreach { text =>
      assert(intercept[ApiProblem](read(text)).status == 400)
    }
  }

  test("empty, non-object and trailing JSON do not become changes") {
    Seq("", "[]", "null", """{}{}""", """{"title":"x",}""").foreach { text =>
      assert(intercept[ApiProblem](read(text)).status == 400)
    }
  }

  test("invalid UTF-8 is rejected instead of replaced with another character") {
    val bytes = Array[Byte](123, 34, 116, 34, 58, 34, 0xc3.toByte, 0x28, 34, 125)
    assert(intercept[ApiProblem](RequestJson.read(new ByteArrayInputStream(bytes))).status == 400)
  }

  test("byte and nesting limits reject oversized input before entity binding") {
    assert(intercept[ApiProblem](read(" " * (RequestJson.maxBytes + 1))).status == 413)
    val nested = "{\"x\":" * 33 + "null" + "}" * 33
    assert(intercept[ApiProblem](read(nested)).status == 400)
  }
}
