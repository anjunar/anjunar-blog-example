package com.anjunar.blog.frontend

import ui.json.JsonProperty
import scala.annotation.meta.field

final class FieldError(var path: Seq[String] = Seq.empty, var message: String = "")

final class ProblemDetails(
    @(JsonProperty @field)("type") var problemType: String = "about:blank",
    var title: String = "",
    var status: Int = 0,
    var detail: String = "",
    var instance: String = "",
    var errors: Seq[FieldError] = Seq.empty,
    var errorId: Option[String] = None
)
