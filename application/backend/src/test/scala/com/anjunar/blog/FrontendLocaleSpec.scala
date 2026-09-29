package com.anjunar.blog

import org.scalatest.funsuite.AnyFunSuite

import java.net.{InetAddress, ServerSocket, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.Files
import java.time.Duration

class FrontendLocaleSpec extends AnyFunSuite {
  test("English and German browser routes share the shell and preserve private-page cache rules") {
    val reservation = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    val port = try reservation.getLocalPort finally reservation.close()
    val assets = Files.createTempDirectory("abt20-locale-assets-")
    val index = Files.writeString(assets.resolve("index.html"), "<!doctype html><title>Locale shell</title>")
    val server = ApplicationMain.start(port, assets)
    val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    try {
      def request(path: String, method: String = "GET"): HttpResponse[String] =
        client.send(HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port$path"))
          .timeout(Duration.ofSeconds(10)).method(method, HttpRequest.BodyPublishers.noBody()).build(),
          HttpResponse.BodyHandlers.ofString())

      Seq("en", "de").foreach { language =>
        Seq("", "/", "/posts/example", "/not-found").foreach { path =>
          val result = request(s"/$language$path")
          assert(result.statusCode() == 200)
          assert(result.headers().firstValue("Cache-Control").orElse("") == "no-cache")
          assert(result.body().contains("Locale shell"))
        }
        Seq("/account", "/register", "/confirm", "/forgot-password", "/reset-password",
          "/editorial", "/editorial/new", "/editorial/relationships",
          "/editorial/posts/8a1c1582-e841-4a27-a506-1a630337df48/edit").foreach { path =>
          val result = request(s"/$language$path")
          assert(result.statusCode() == 200)
          assert(result.headers().firstValue("Cache-Control").orElse("") == "no-store")
        }
        assert(request(s"/$language/account", "HEAD").statusCode() == 200)
        assert(request(s"/$language/account", "POST").statusCode() == 405)
        assert(request(s"/$language/service/hello").statusCode() == 404)
      }
      assert(request("/fr/account").statusCode() == 404)
      assert(request("/de/not-a-route").statusCode() == 404)
      assert(request("/service/hello").statusCode() == 200)
    } finally {
      client.close()
      server.stop()
      Files.deleteIfExists(index)
      Files.deleteIfExists(assets)
    }
  }
}
