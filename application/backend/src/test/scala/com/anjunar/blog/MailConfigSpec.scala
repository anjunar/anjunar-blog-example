package com.anjunar.blog

import jakarta.ws.rs.ServiceUnavailableException
import org.scalatest.funsuite.AnyFunSuite

class MailConfigSpec extends AnyFunSuite {
  private val base = Map("BLOG_SMTP_HOST" -> "mail.example.com",
    "BLOG_PUBLIC_ORIGIN" -> "https://blog.example.com", "BLOG_MAIL_FROM" -> "blog@example.com")

  test("mail uses required STARTTLS and a configured public origin by default") {
    val config = MailConfig.load(base)
    assert(config.startTls && config.port == 587)
    assert(config.origin == "https://blog.example.com")
    assert(config.username.isEmpty)
  }
  test("missing SMTP disables public mail requests") {
    intercept[ServiceUnavailableException](MailConfig.load(Map.empty))
  }
  test("only localhost permits plaintext SMTP and HTTP account links") {
    val config = MailConfig.load(base ++ Map("BLOG_SMTP_HOST" -> "127.0.0.1",
      "BLOG_SMTP_MODE" -> "local", "BLOG_PUBLIC_ORIGIN" -> "http://127.0.0.1:8080/"))
    assert(!config.startTls && config.port == 1025 && config.origin == "http://127.0.0.1:8080")
    intercept[IllegalArgumentException](MailConfig.load(base + ("BLOG_SMTP_MODE" -> "local")))
    intercept[IllegalArgumentException](MailConfig.load(base + ("BLOG_PUBLIC_ORIGIN" -> "http://blog.example.com")))
  }
  test("account link origins reject credentials, paths, query strings and fragments") {
    for (origin <- Seq("https://user:password@blog.example.com", "https://blog.example.com/path",
      "https://blog.example.com?redirect=evil", "https://blog.example.com/#token", "//blog.example.com"))
      intercept[IllegalArgumentException](MailConfig.load(base + ("BLOG_PUBLIC_ORIGIN" -> origin)))
  }
  test("SMTP credentials are paired and message addresses cannot contain headers or multiple recipients") {
    intercept[IllegalArgumentException](MailConfig.load(base + ("BLOG_SMTP_USERNAME" -> "user")))
    for (email <- Seq("a@example.com\r\nBcc: b@example.com", "a@example.com,b@example.com", "Name <a@example.com>"))
      intercept[Exception](AuthJson.email(email))
  }
}
