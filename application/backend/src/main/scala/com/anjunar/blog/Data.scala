package com.anjunar.blog

import com.anjunar.json.mapper.provider.DTO
import jakarta.json.bind.annotation.JsonbProperty

import scala.annotation.meta.field

class Data[E](
    @(JsonbProperty @field) val data: E,
    @(JsonbProperty @field) val schema: Schema
) extends DTO
