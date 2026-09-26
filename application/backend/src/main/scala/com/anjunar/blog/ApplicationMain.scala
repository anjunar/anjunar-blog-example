package com.anjunar.blog

import dev.resteasy.embedded.server.UndertowCdiEmbeddedServer
import jakarta.ws.rs.SeBootstrap

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
    println(s"Blog server: http://127.0.0.1:$port/service/hello")

    try new CountDownLatch(1).await()
    finally stop()
  }

  def start(port: Int): UndertowCdiEmbeddedServer = {
    require(port >= 1 && port <= 65535, "BLOG_PORT must be between 1 and 65535")
    val server = new UndertowCdiEmbeddedServer()
    server.getDeployment.setApplication(new ServerApplication())
    val configuration = SeBootstrap.Configuration.builder()
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
