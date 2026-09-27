package com.anjunar.blog

import com.anjunar.json.mapper.provider.DTO
import com.anjunar.json.mapper.schema.Link
import jakarta.json.bind.annotation.JsonbProperty

import java.util
import scala.annotation.meta.field

class Data[E](
    @(JsonbProperty @field) val data: E,
    @(JsonbProperty @field) val schema: Schema,
    @(JsonbProperty @field)("$links") val links: util.List[Link] = util.List.of()
) extends DTO
