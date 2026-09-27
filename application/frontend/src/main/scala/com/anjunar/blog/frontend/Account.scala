package com.anjunar.blog.frontend

import ui.core.state.Property
import ui.json.{JsonId, JsonProperty}
import scala.annotation.meta.field

final class Account {
  @JsonId val id: Property[String] = Property("")
  val version: Property[Long] = Property(-1L)
  val email: Property[String] = Property("")
  val role: Property[String] = Property("")
}

final class SessionState(var csrfToken: String = "", var account: Option[Account] = None,
    @(JsonProperty @field)("$links") var links: Seq[ApiLink] = Seq.empty)

final class LoginCredentials {
  val email: Property[String] = Property("")
  val password: Property[String] = Property("")
}

final class LoginCommand(var email: String = "", var password: String = "")
