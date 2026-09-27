package com.anjunar.blog

import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response

import java.time.Instant
import java.util.concurrent.Semaphore
import scala.collection.mutable

private final case class LoginWindow(start: Long, count: Int)

@ApplicationScoped
class LoginLimiter {
  private val windows = mutable.Map.empty[String, LoginWindow]
  private val hashing = new Semaphore(4)

  def check(email: String, address: String, now: Long = Instant.now().getEpochSecond): Unit = synchronized {
    windows.filterInPlace((_, window) => now - window.start < 60)
    val keys = Seq(("email:" + email, 5), ("address:" + address, 30))
    if (keys.exists((key, limit) => windows.get(key).exists(_.count >= limit)) ||
        windows.size + keys.count((key, _) => !windows.contains(key)) > 4096) reject()
    keys.foreach { (key, _) =>
      val old = windows.getOrElse(key, LoginWindow(now, 0))
      windows.update(key, old.copy(count = old.count + 1))
    }
  }

  def withHashing[A](work: => A): A = {
    if (!hashing.tryAcquire()) reject()
    try work finally hashing.release()
  }

  private def reject(): Nothing =
    throw new WebApplicationException(Response.status(429).header("Retry-After", "60").build())
}
