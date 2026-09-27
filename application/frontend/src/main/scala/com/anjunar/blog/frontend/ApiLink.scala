package com.anjunar.blog.frontend

import org.scalajs.dom

final class ApiLink(var rel: String = "", var url: String = "", var method: String = "") {
  def path(expectedMethod: String): String = {
    require(method == expectedMethod, "Unexpected action method")
    val target = new dom.URL(url, dom.window.location.origin)
    require(target.origin == dom.window.location.origin && target.pathname.startsWith("/service/") &&
      target.username.isEmpty && target.password.isEmpty && target.hash.isEmpty, "Invalid API link")
    target.pathname + target.search
  }
}
