package com.anjunar.blog

import com.anjunar.json.mapper.converter.JacksonJsonConverter
import com.anjunar.scala.universe.ResolvedClass

import java.time.Instant

class InstantConverter extends JacksonJsonConverter {
  override def toJson(input: Any, resolvedClass: ResolvedClass): String =
    input.asInstanceOf[Instant].toString

  override def toJava(json: String, resolvedClass: ResolvedClass): Any =
    Instant.parse(json)
}
