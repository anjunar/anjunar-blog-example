package com.anjunar.blog

final class DatabaseConfig(val url: String, val user: String, val password: String)

object DatabaseConfig {
  def load(environment: Map[String, String] = sys.env): DatabaseConfig = {
    val url = environment.getOrElse("BLOG_DB_URL", "jdbc:postgresql://127.0.0.1:5433/anjunar_blog")
    val user = environment.getOrElse("BLOG_DB_USER", "blog")
    val password = environment.getOrElse("BLOG_DB_PASSWORD",
      throw new IllegalArgumentException("Set BLOG_DB_PASSWORD before accessing the database"))
    require(url.startsWith("jdbc:postgresql:"), "BLOG_DB_URL must be a PostgreSQL JDBC URL")
    require(user.nonEmpty && password.nonEmpty, "Database user and password must not be empty")
    new DatabaseConfig(url, user, password)
  }
}
