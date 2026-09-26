package com.anjunar.blog

import org.scalatest.concurrent.Eventually
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.time.{Millis, Seconds, Span}

import java.net.{InetAddress, ServerSocket, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration

class ServerLifecycleSpec extends AnyFunSuite with Eventually {

  test("discovered resources and providers share a request scope and release their contextual instances") {
    ScopeEvents.clear()
    val reservation = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    val port = try reservation.getLocalPort finally reservation.close()
    val server = ApplicationMain.start(port)
    val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    var applicationId = ""

    try {
      def probe(): (String, String) = {
        val request = HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port/service/_test/scopes"))
          .timeout(Duration.ofSeconds(10))
          .GET()
          .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assert(response.statusCode() == 200)
        val ids = response.body().split("\\|", -1)
        assert(ids.length == 2)
        assert(response.headers().firstValue("X-Probe-Request").orElse("") == ids(0))
        (ids(0), ids(1))
      }

      val (firstRequest, firstApplication) = probe()
      val (secondRequest, secondApplication) = probe()
      applicationId = firstApplication
      assert(firstRequest != secondRequest)
      assert(firstApplication == secondApplication)

      eventually(timeout(Span(5, Seconds)), interval(Span(20, Millis))) {
        assert(ScopeEvents.destroyedRequests.contains(firstRequest))
        assert(ScopeEvents.destroyedRequests.contains(secondRequest))
      }
      assert(!ScopeEvents.destroyedApplications.contains(applicationId))
    } finally {
      try client.close()
      finally server.stop()
    }

    assert(ScopeEvents.destroyedApplications.contains(applicationId))
  }

}
