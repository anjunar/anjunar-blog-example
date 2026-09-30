package com.anjunar.blog

import io.undertow.Undertow
import org.scalatest.funsuite.AnyFunSuite

import java.net.{InetAddress, ServerSocket, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.Files
import java.time.Duration

class SsrHandlerSpec extends AnyFunSuite {
  test("a missing or failed SSR bundle returns 503 while the API and private shell remain available") {
    val reservation = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    val port = try reservation.getLocalPort finally reservation.close()
    val origin = URI.create(s"http://127.0.0.1:$port")
    val assets = Files.createTempDirectory("abt22-handler-")
    val index = Files.writeString(assets.resolve("index.html"), "<!doctype html><div id=\"app\"></div>")
    val bundle = assets.resolve("server.mjs")
    val client = HttpClient.newHttpClient()
    try {
      for (exists <- Seq(false, true)) {
        if (exists) Files.writeString(bundle, """export function render() { throw new Error("internal secret"); }""")
        val renderer = new SsrRenderer(bundle, origin)
        val handler = new FrontendHandler(exchange => exchange.getResponseSender.send("UP"), assets, Some(renderer))
        val server = Undertow.builder().addHttpListener(port, "127.0.0.1").setHandler(handler).build()
        server.start()
        try {
          def request(path: String, method: String = "GET") =
            client.send(HttpRequest.newBuilder(origin.resolve(path)).timeout(Duration.ofSeconds(20))
              .method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString())
          val publicPage = request("/en")
          assert(publicPage.statusCode() == 503)
          assert(!publicPage.body().contains("internal secret"))
          assert(publicPage.headers().firstValue("Cache-Control").orElse("") == "no-store")
          val head = request("/en", "HEAD")
          assert(head.statusCode() == 503 && head.body().isEmpty)
          assert(request("/service/health/live").body() == "UP")
          assert(request("/de/account").statusCode() == 200)
          assert(request("/de/account").body().contains("id=\"app\""))
          assert(request("/server.mjs").statusCode() == 404)
        } finally {
          renderer.close()
          server.stop()
        }
      }
    } finally {
      client.close()
      Files.deleteIfExists(bundle)
      Files.deleteIfExists(index)
      Files.deleteIfExists(assets)
    }
  }
}
