package com.anjunar.blog

import io.undertow.Undertow
import io.undertow.util.Headers
import org.scalatest.funsuite.AnyFunSuite

import java.net.{InetAddress, ServerSocket, URI}
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.{ExecutionException, TimeoutException}
import java.util.concurrent.atomic.AtomicBoolean

class SsrRendererSpec extends AnyFunSuite {
  private val script = """
    let requests = 0;
    export async function render(url) {
      requests += 1;
      if (url === "/spin") { while (true) {} }
      if (url === "/timer") await new Promise(resolve => setTimeout(resolve, 10));
      if (url.startsWith("/fetch?")) {
        const response = await fetch(url.substring(7), {method: "GET"});
        return {html: await response.text(), status: response.status};
      }
      return {html: url + ":" + requests, status: 200};
    }
  """

  private def withRenderer(test: (SsrRenderer, AtomicBoolean) => Unit): Unit = {
    val reservation = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    val port = try reservation.getLocalPort finally reservation.close()
    val anonymous = new AtomicBoolean()
    val server = Undertow.builder().addHttpListener(port, "127.0.0.1")
      .setHandler(exchange => {
        anonymous.set(exchange.getRequestHeaders.getFirst(Headers.COOKIE) == null &&
          exchange.getRequestHeaders.getFirst(Headers.AUTHORIZATION) == null)
        exchange.getResponseHeaders.put(Headers.CONTENT_TYPE, "application/json")
        exchange.getResponseSender.send("""{"title":"Quotes \\" and <markup>"}""")
      }).build()
    val bundle = Files.createTempFile("abt22-renderer-", ".mjs")
    Files.writeString(bundle, script)
    val renderer = new SsrRenderer(bundle, URI.create(s"http://127.0.0.1:$port"), Duration.ofSeconds(5))
    server.start()
    try test(renderer, anonymous)
    finally {
      renderer.close()
      server.stop()
      Files.deleteIfExists(bundle)
    }
  }

  test("request state is isolated while timers and the public fetch bridge work") {
    withRenderer { (renderer, anonymous) =>
      assert(renderer.render("/first") == RenderedPage("/first:1", 200))
      assert(renderer.render("/timer") == RenderedPage("/timer:1", 200))
      val fetched = renderer.render("/fetch?/service/blog/posts?q=hello")
      assert(fetched.status == 200)
      assert(fetched.html.contains("<markup>"))
      assert(anonymous.get())
      assert(renderer.render("/second").html == "/second:1")
    }
  }

  test("the bridge rejects arbitrary origins, traversal, private endpoints and fragments") {
    withRenderer { (renderer, _) =>
      Seq("http://example.com/service/blog/posts", "//example.com/service/blog/posts",
        "/service/auth/session", "/service/blog/posts/../editorial", "/service/blog/posts#fragment")
        .foreach { path =>
          intercept[ExecutionException](renderer.render("/fetch?" + path))
        }
      assert(renderer.render("/healthy").status == 200)
    }
  }

  test("a CPU-bound render is cancelled at the deadline and the next request recovers") {
    withRenderer { (renderer, _) =>
      assert(renderer.render("/warm").status == 200)
      intercept[TimeoutException](renderer.render("/spin"))
      assert(renderer.render("/recovered") == RenderedPage("/recovered:1", 200))
    }
  }

  test("invalid and oversized request URLs never enter the render queue") {
    withRenderer { (renderer, _) =>
      Seq("https://example.com", "//example.com", "/" + "x" * 4096).foreach { url =>
        intercept[IllegalArgumentException](renderer.render(url))
      }
    }
  }
}
