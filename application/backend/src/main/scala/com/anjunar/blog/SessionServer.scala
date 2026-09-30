package com.anjunar.blog

import dev.resteasy.embedded.server.UndertowCdiEmbeddedServer
import io.undertow.server.session.SessionManager
import org.wildfly.security.auth.server.SecurityDomain

class SessionServer extends UndertowCdiEmbeddedServer {
  var sessions: SessionManager = null
  var securityDomain: SecurityDomain = null
  var renderer: SsrRenderer = null

  override def stop(): Unit = {
    // RESTEasy 7.0.5 stops Weld before undeploying Undertow. End session
    // listeners while their CDI container is still available.
    try {
      try { if (renderer != null) renderer.close() }
      finally if (sessions != null) sessions.stop()
    }
    finally {
      try super.stop()
      finally if (securityDomain != null) SecurityDomain.unregisterClassLoader(getClass.getClassLoader)
    }
  }
}
