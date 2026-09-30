package com.anjunar.blog

import java.net.URI

object PublicSite {
  val OriginAttribute = "blog.publicOrigin"

  def origin(environment: Map[String, String], port: Int): String = {
    val uri = URI.create(environment.getOrElse("BLOG_PUBLIC_ORIGIN", s"http://127.0.0.1:$port"))
    require(uri.getHost != null && uri.getRawUserInfo == null && uri.getRawQuery == null &&
      uri.getRawFragment == null && Option(uri.getRawPath).forall(path => path.isEmpty || path == "/") &&
      (uri.getPort == -1 || (uri.getPort >= 1 && uri.getPort <= 65535)),
      "BLOG_PUBLIC_ORIGIN must be an origin without credentials, path, query or fragment")
    require(uri.getScheme == "https" ||
      (uri.getScheme == "http" && Set("127.0.0.1", "localhost", "[::1]").contains(uri.getHost)),
      "The public origin requires HTTPS except on localhost")
    val standardPort = (uri.getScheme == "https" && uri.getPort == 443) || (uri.getScheme == "http" && uri.getPort == 80)
    new URI(uri.getScheme, null, uri.getHost.toLowerCase, if (standardPort) -1 else uri.getPort, null, null, null).toString
  }
}
