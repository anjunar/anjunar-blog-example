package com.anjunar.blog

import dev.resteasy.embedded.server.{UndertowCdiEmbeddedServer, UndertowConfigurationOptions}
import io.undertow.servlet.api.{DeploymentInfo, ServletSessionConfig}
import io.undertow.server.handlers.SameSiteCookieHandler
import io.undertow.server.session.InMemorySessionManager
import jakarta.servlet.SessionTrackingMode
import jakarta.ws.rs.SeBootstrap

import java.net.URI
import java.nio.file.Path
import java.util.Set
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import scala.util.control.NonFatal

object ApplicationMain {

  def main(args: Array[String]): Unit = {
    val port = sys.env.getOrElse("BLOG_PORT", "8080").toInt
    val server = start(port)
    val stopped = new AtomicBoolean(false)

    def stop(): Unit =
      if (stopped.compareAndSet(false, true)) server.stop()

    Runtime.getRuntime.addShutdownHook(new Thread(() => stop(), "blog-shutdown"))
    println(s"Blog: http://127.0.0.1:$port/")
    println(s"API: http://127.0.0.1:$port/service/blog/posts")

    try new CountDownLatch(1).await()
    finally stop()
  }

  def start(port: Int, assets: Path = Path.of("target", "frontend"), security: SecurityConfig = SecurityConfig.load(),
      ssrEnabled: Boolean = sys.env.get("BLOG_SSR_ENABLED").forall(_ != "false")): UndertowCdiEmbeddedServer = {
    require(port >= 1 && port <= 65535, "BLOG_PORT must be between 1 and 65535")
    val publicOrigin = PublicSite.origin(sys.env, port)
    val server = new SessionServer()
    server.renderer = if (ssrEnabled) new SsrRenderer(assets.resolve("ssr/main.js"), URI.create(s"http://127.0.0.1:$port"), publicOrigin = Some(publicOrigin)) else null
    server.getDeployment.setApplication(new ServerApplication())
    val cookies = new ServletSessionConfig()
      .setName(SecurityConfig.cookieName).setPath("/").setHttpOnly(true)
      .setSecure(security.secureCookies)
      .setSessionTrackingModes(Set.of(SessionTrackingMode.COOKIE))
    val deployment = new DeploymentInfo()
      .addServletContextAttribute(PublicSite.OriginAttribute, publicOrigin)
      .setServletSessionConfig(cookies)
      .setDefaultSessionTimeout(SecurityConfig.idleSeconds)
      .setSessionManagerFactory(deployment => {
        val sessions = new InMemorySessionManager(deployment.getDeploymentInfo.getDeploymentName, 2048, false)
        server.sessions = sessions
        sessions
      })
      .addInitialHandlerChainWrapper(api =>
        new SameSiteCookieHandler(new FrontendHandler(api, assets, Option(server.renderer), publicOrigin), "Lax", SecurityConfig.cookieName))
    server.securityDomain = SoteriaIntegration.configure(deployment)
    val configuration = SeBootstrap.Configuration.builder()
      .property(UndertowConfigurationOptions.DEPLOYMENT_INFO, deployment)
      .host("127.0.0.1")
      .port(port)
      .rootPath("/")
      .build()

    try {
      server.start(configuration)
      server
    } catch {
      case NonFatal(error) =>
        server.stop()
        throw error
    }
  }
}
