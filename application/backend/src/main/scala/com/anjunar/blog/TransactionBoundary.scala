package com.anjunar.blog

import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.ws.rs.container.{ContainerRequestContext, ContainerRequestFilter, ContainerResponseContext, ContainerResponseFilter, ResourceInfo}
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.ext.{Provider, WriterInterceptor, WriterInterceptorContext}

import java.io.ByteArrayOutputStream
import scala.compiletime.uninitialized
import scala.util.control.NonFatal

@Provider
@RequestScoped
class TransactionBoundary
    extends ContainerRequestFilter
    with ContainerResponseFilter
    with WriterInterceptor {
  @Inject
  var transaction: RequestTransaction = uninitialized

  @Context
  var resource: ResourceInfo = uninitialized

  private var successful = false

  override def filter(request: ContainerRequestContext): Unit =
    if (resource.getResourceClass != classOf[HealthResource]) {
      transaction.begin(request.getMethod == "GET" || request.getMethod == "HEAD")
    }

  override def filter(request: ContainerRequestContext, response: ContainerResponseContext): Unit = {
    successful = response.getStatus < 400
    try {
      transaction.flush(successful)
      if (!response.hasEntity || request.getMethod == "HEAD") transaction.finish(successful)
    } catch {
      case NonFatal(error) =>
        abort(error)
        throw error
    }
  }

  override def aroundWriteTo(context: WriterInterceptorContext): Unit =
    if (!transaction.active) context.proceed()
    else {
      // Finish serialization and the transaction before sending a success body.
      val output = context.getOutputStream
      val buffer = new ByteArrayOutputStream()
      context.setOutputStream(buffer)
      try {
        context.proceed()
        transaction.finish(successful)
      } catch {
        case NonFatal(error) =>
          abort(error)
          throw error
      } finally {
        context.setOutputStream(output)
      }
      buffer.writeTo(output)
    }

  private def abort(error: Throwable): Unit =
    try transaction.finish(false)
    catch { case NonFatal(cleanup) => error.addSuppressed(cleanup) }
}
