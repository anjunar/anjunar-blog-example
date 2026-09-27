package com.anjunar.blog.frontend

import org.scalatest.funsuite.AnyFunSuite
import ui.json.JsonMapper

import scala.scalajs.js

class AccountJsonSpec extends AnyFunSuite {
  test("an anonymous state retains CSRF and has no account") {
    val state = JsonMapper.deserialize[SessionState](js.JSON.parse("""{"csrfToken":"token","@type":"SessionState"}"""))
    assert(state.csrfToken == "token")
    assert(state.account.isEmpty)
  }

  test("the authenticated account mirrors the safe fields and preserves version zero") {
    val state = JsonMapper.deserialize[SessionState](js.JSON.parse(
      """{"csrfToken":"token","account":{"id":"id","version":0,"email":"admin@example.com","role":"ADMIN"}}"""
    ))
    val account = state.account.get
    assert(account.id.get == "id" && account.version.get == 0L)
    assert(account.email.get == "admin@example.com" && account.role.get == "ADMIN")
  }

  test("the login command sends exactly email and password") {
    val body = JsonMapper.serialize(new LoginCommand("admin@example.com", "a long tutorial passphrase"))
    assert(js.Object.keys(body.asInstanceOf[js.Object]).toSeq.toSet == Set("email", "password"))
    assert(body.email.asInstanceOf[String] == "admin@example.com")
    assert(body.password.asInstanceOf[String] == "a long tutorial passphrase")
  }
}
