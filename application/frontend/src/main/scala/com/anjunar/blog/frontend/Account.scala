package com.anjunar.blog.frontend

import ui.core.state.Property
import ui.forms.validators.Size
import ui.json.{JsonId, JsonIgnore, JsonMapper, JsonProperty}
import scala.scalajs.js
import scala.annotation.meta.field

final class Account {
  @JsonId val id: Property[String] = Property("")
  val version: Property[Long] = Property(-1L)
  @JsonIgnore(deserializable = true) val email: Property[String] = Property("")
  @JsonIgnore(deserializable = true) val role: Property[String] = Property("")
  @(Size @field)(min = 2, max = 80)
  val displayName: Property[String] = Property(null)

  def writeBody(): js.Dynamic = {
    val body = JsonMapper.serialize(this)
    body.updateDynamic("version")(version.get.toDouble)
    if (!js.isUndefined(body.displayName) && body.displayName.asInstanceOf[String] == "")
      body.updateDynamic("displayName")(null)
    body
  }
}

final class SessionState(var csrfToken: String = "", var account: Option[Account] = None,
    @(JsonProperty @field)("$links") var links: Seq[ApiLink] = Seq.empty)

final class LoginCredentials {
  val email: Property[String] = Property("")
  val password: Property[String] = Property("")
}

final class LoginCommand(var email: String = "", var password: String = "")
