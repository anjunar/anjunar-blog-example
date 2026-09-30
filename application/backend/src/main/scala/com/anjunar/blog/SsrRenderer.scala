package com.anjunar.blog

import org.graalvm.polyglot.{Context, Engine, HostAccess, Source, Value}
import org.graalvm.polyglot.proxy.{ProxyExecutable, ProxyObject}

import java.lang
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import java.time.Duration
import java.util.Map as JavaMap
import java.util.concurrent.{ArrayBlockingQueue, FutureTask, ThreadPoolExecutor, TimeUnit, TimeoutException}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

final case class RenderedPage(html: String, status: Int)

/** One bounded worker owns all guest execution. Every request gets a new JS context. */
final class SsrRenderer(bundle: Path, apiOrigin: URI, timeout: Duration = Duration.ofSeconds(15), publicOrigin: Option[String] = None)
    extends AutoCloseable {
  require(apiOrigin.getHost == "127.0.0.1" && apiOrigin.getScheme == "http")
  require(!timeout.isNegative && !timeout.isZero)
  private val worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
    new ArrayBlockingQueue[Runnable](8),
    (task: Runnable) => new Thread(task, "blog-ssr"),
    new ThreadPoolExecutor.AbortPolicy())
  private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
    .followRedirects(HttpClient.Redirect.NEVER).build()
  private val active = new AtomicReference[Request]()
  private val closed = new AtomicBoolean()
  // Only the worker reads these; the engine shares compiled code, never guest state.
  private var engine: Engine = null
  private lazy val module = Source.newBuilder("js", bundle.toFile)
    .mimeType("application/javascript+module").build()
  private lazy val host = {
    val stream = getClass.getResourceAsStream("/ssr/host.js")
    require(stream != null, "SSR host resource is missing")
    try new String(stream.readAllBytes(), UTF_8) finally stream.close()
  }

  private final class Request {
    val context = new AtomicReference[Context]()
    val cancelled = new AtomicBoolean()
    def cancel(): Unit = {
      cancelled.set(true)
      Option(context.get()).foreach(_.close(true))
    }
  }

  def render(url: String): RenderedPage = {
    require(url.startsWith("/") && !url.startsWith("//") && url.length <= 4096)
    val request = new Request
    val task = new FutureTask[RenderedPage](() => {
      active.set(request)
      try {
        if (request.cancelled.get()) throw new TimeoutException("SSR deadline exceeded")
        evaluate(url, request)
      } finally active.compareAndSet(request, null)
    })
    worker.execute(task) // A full queue fails immediately; the handler returns 503.
    try task.get(timeout.toMillis, TimeUnit.MILLISECONDS)
    catch {
      case error: TimeoutException =>
        task.cancel(true)
        request.cancel()
        throw error
      case error: InterruptedException =>
        task.cancel(true)
        request.cancel()
        Thread.currentThread().interrupt()
        throw error
    }
  }

  private def evaluate(url: String, request: Request): RenderedPage = {
    if (engine == null) engine = Engine.create()
    val context = Context.newBuilder("js").engine(engine).allowHostAccess(HostAccess.NONE)
      .option("js.esm-eval-returns-exports", "true").build()
    request.context.set(context)
    try {
      if (request.cancelled.get()) throw new TimeoutException("SSR deadline exceeded")
      var nextTimer = 0
      val timers = mutable.Map.empty[Int, (Long, Value)]
      val bindings = context.getBindings("js")
      bindings.putMember("fetchPublic", new ProxyExecutable {
        override def execute(args: Value*): AnyRef = fetch(args(0).asString(), args(1).asString())
      })
      bindings.putMember("scheduleTimer", new ProxyExecutable {
        override def execute(args: Value*): AnyRef = {
          nextTimer += 1
          val delay = math.max(0L, math.min(args(1).asDouble().toLong, timeout.toMillis))
          timers(nextTimer) = (System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delay), args(0))
          lang.Integer.valueOf(nextTimer)
        }
      })
      bindings.putMember("cancelTimer", new ProxyExecutable {
        override def execute(args: Value*): AnyRef = { timers.remove(args(0).asInt()); null }
      })
      context.eval("js", host)
      val exports = context.eval(module)
      var result: RenderedPage = null
      var failure: String = null
      val resolved = new ProxyExecutable {
        override def execute(args: Value*): AnyRef = {
          val value = args.head
          val status = value.getMember("status").asInt()
          require(Set(200, 400, 401, 403, 404, 503).contains(status), "Unexpected SSR status")
          result = RenderedPage(value.getMember("html").asString(), status)
          null
        }
      }
      val rejected = new ProxyExecutable {
        override def execute(args: Value*): AnyRef = { failure = args.head.toString; null }
      }
      exports.getMember("render").execute(url, publicOrigin.getOrElse(apiOrigin.toString)).invokeMember("then", resolved, rejected)
      while (result == null && failure == null && !request.cancelled.get()) {
        timers.toSeq.filter(_._2._1 <= System.nanoTime()).foreach { case (id, (_, callback)) =>
          if (timers.remove(id).isDefined) callback.execute()
        }
        if (result == null && failure == null) Thread.sleep(1)
      }
      if (failure != null) throw new IllegalStateException(s"SSR failed: $failure")
      if (result == null) throw new TimeoutException("SSR deadline exceeded")
      result
    } finally {
      context.close(true)
      request.context.compareAndSet(context, null)
    }
  }

  private def fetch(path: String, method: String): ProxyObject = {
    val relative = URI.create(path)
    require(method == "GET" && !relative.isAbsolute && relative.getRawAuthority == null &&
      relative.getRawFragment == null &&
      relative.getRawPath.matches("/service/blog/posts(?:/[a-z0-9]+(?:-[a-z0-9]+)*)?"),
      "SSR can only read the public posts API")
    val request = HttpRequest.newBuilder(apiOrigin.resolve(relative)).GET()
      .header("Accept", "application/json, application/problem+json")
      .timeout(Duration.ofSeconds(3)).build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
    val stream = response.body()
    val body = try {
      val bytes = stream.readNBytes(2 * 1024 * 1024 + 1)
      require(bytes.length <= 2 * 1024 * 1024, "SSR API response is too large")
      new String(bytes, UTF_8)
    } finally stream.close()
    ProxyObject.fromMap(JavaMap.of[String, Object](
      "status", lang.Integer.valueOf(response.statusCode()),
      "body", body,
      "contentType", response.headers().firstValue("Content-Type").orElse("")))
  }

  override def close(): Unit = if (closed.compareAndSet(false, true)) {
    worker.shutdownNow().asScala.foreach {
      case task: FutureTask[?] => task.cancel(false)
      case _ => ()
    }
    Option(active.get()).foreach(_.cancel())
    worker.awaitTermination(5, TimeUnit.SECONDS)
    client.close()
    if (engine != null) engine.close(true)
  }
}
