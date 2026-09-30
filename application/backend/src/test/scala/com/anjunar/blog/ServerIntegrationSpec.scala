package com.anjunar.blog

import org.scalatest.funsuite.AnyFunSuite

import java.net.{InetAddress, ServerSocket, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.Path
import java.time.Duration
import java.util.UUID

class ServerIntegrationSpec extends AnyFunSuite {

  test("Undertow serves the REST resource with its CDI dependency and returns 404 for unknown resources") {
    // Let the OS choose a currently available loopback port for this test.
    val reservation = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    val port = try reservation.getLocalPort finally reservation.close()

    val server = ApplicationMain.start(port)
    val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    try {
      def get(path: String): HttpResponse[String] = {
        val request = HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port$path"))
          .timeout(Duration.ofSeconds(10))
          .GET()
          .build()
        client.send(request, HttpResponse.BodyHandlers.ofString())
      }

      val response = get("/service/hello")
      assert(response.statusCode() == 200)
      assert(response.headers().firstValue("Content-Type").orElse("").startsWith("text/plain"))
      assert(response.body() == "Welcome to Anjunar Blog Tutorial!\n")
      val health = get("/service/health/live")
      assert(health.statusCode() == 200)
      assert(health.body() == "UP\n")
      assert(get("/service/missing").statusCode() == 404)
    } finally {
      client.close()
      server.stop()
    }
  }

  test("API liveness remains available before frontend assets are built") {
    val reservation = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    val port = try reservation.getLocalPort finally reservation.close()
    val missingAssets = Path.of("target", s"missing-assets-${UUID.randomUUID()}")
    val server = ApplicationMain.start(port, missingAssets)
    val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    try {
      def get(path: String): HttpResponse[String] =
        client.send(
          HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port$path"))
            .timeout(Duration.ofSeconds(10)).GET().build(),
          HttpResponse.BodyHandlers.ofString()
        )

      assert(get("/service/health/live").statusCode() == 200)
      assert(get("/").statusCode() == 308)
      assert(get("/en").statusCode() == 503)
      assert(get("/service/missing").statusCode() == 404)
    } finally {
      client.close()
      server.stop()
    }
  }
}
