package com.anjunar.blog

import dev.resteasy.embedded.server.UndertowCdiEmbeddedServer
import io.undertow.server.session.SessionManager

class SessionServer extends UndertowCdiEmbeddedServer {
  var sessions: SessionManager = null

  override def stop(): Unit = {
    // RESTEasy 7.0.5 stops Weld before undeploying Undertow. End session
    // listeners while their CDI container is still available.
    try { if (sessions != null) sessions.stop() }
    finally super.stop()
  }
}
