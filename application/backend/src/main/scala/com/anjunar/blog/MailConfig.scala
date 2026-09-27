package com.anjunar.blog

import jakarta.ws.rs.ServiceUnavailableException

import java.net.URI

final class MailConfig(val host: String, val port: Int, val from: String, val origin: String,
    val startTls: Boolean, val username: Option[String], val password: Option[String])

object MailConfig {
  def load(environment: Map[String, String] = sys.env): MailConfig = {
    val host = environment.getOrElse("BLOG_SMTP_HOST", throw new ServiceUnavailableException())
    val origin = URI.create(environment.getOrElse("BLOG_PUBLIC_ORIGIN",
      throw new IllegalArgumentException("BLOG_PUBLIC_ORIGIN is required for account mail")))
    val localHosts = Set("127.0.0.1", "localhost", "[::1]")
    require(origin.getHost != null && origin.getRawUserInfo == null && origin.getRawQuery == null &&
      origin.getRawFragment == null && Option(origin.getRawPath).forall(path => path.isEmpty || path == "/"),
      "BLOG_PUBLIC_ORIGIN must be an origin without credentials, path, query or fragment")
    require(origin.getScheme == "https" || (origin.getScheme == "http" && localHosts.contains(origin.getHost)),
      "Account links require HTTPS except on localhost")
    val local = environment.getOrElse("BLOG_SMTP_MODE", "starttls") match {
      case "starttls" => false
      case "local" => true
      case _ => throw new IllegalArgumentException("BLOG_SMTP_MODE must be starttls or local")
    }
    require(!local || localHosts.contains(host), "Unencrypted SMTP is restricted to localhost")
    val port = environment.getOrElse("BLOG_SMTP_PORT", if (local) "1025" else "587").toInt
    require(port >= 1 && port <= 65535, "Invalid SMTP port")
    val from = AuthJson.email(environment.getOrElse("BLOG_MAIL_FROM",
      throw new IllegalArgumentException("BLOG_MAIL_FROM is required")))
    val username = environment.get("BLOG_SMTP_USERNAME").filter(_.nonEmpty)
    val password = environment.get("BLOG_SMTP_PASSWORD").filter(_.nonEmpty)
    require(username.isDefined == password.isDefined, "Configure both SMTP username and password")
    new MailConfig(host, port, from, origin.toString.stripSuffix("/"), !local, username, password)
  }
}
