package com.anjunar.blog

import com.anjunar.json.mapper.provider.DTO
import jakarta.json.bind.annotation.JsonbProperty

import java.util
import scala.annotation.meta.field

class Table[C](
    @(JsonbProperty @field) val rows: util.List[C],
    @(JsonbProperty @field) val size: Long
) extends DTO
