package com.anjunar.blog

import jakarta.enterprise.context.control.RequestContextController
import jakarta.enterprise.inject.se.SeContainerInitializer

import java.time.Instant
import scala.util.Using

object MediaCleanupMain {
  def main(args: Array[String]): Unit = {
    require(args.isEmpty, "Usage: MediaCleanupMain (one batch of up to 100 expired orphan images)")
    Using.resource(SeContainerInitializer.newInstance().initialize()) { container =>
      val context = container.select(classOf[RequestContextController]).get()
      context.activate()
      val transaction = container.select(classOf[RequestTransaction]).get()
      try {
        transaction.begin(false)
        val removed = container.select(classOf[MediaLifecycle]).get()
          .cleanup(Instant.now().minusSeconds(86400), None, 100)
        transaction.finish(true)
        println(s"Removed $removed unreferenced images older than 24 hours.")
      } finally {
        transaction.finish(false)
        context.deactivate()
      }
    }
  }
}
