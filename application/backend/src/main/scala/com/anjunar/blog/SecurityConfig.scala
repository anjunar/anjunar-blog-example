package com.anjunar.blog

final case class SecurityConfig(secureCookies: Boolean = true)

object SecurityConfig {
  val cookieName = "BLOGSESSION"
  val idleSeconds = 15 * 60
  val absoluteSeconds = 8 * 60 * 60

  def load(): SecurityConfig = sys.env.getOrElse("BLOG_COOKIE_SECURE", "true") match {
    case "true" => SecurityConfig(true)
    case "false" => SecurityConfig(false)
    case _ => throw new IllegalArgumentException("BLOG_COOKIE_SECURE must be true or false")
  }
}
