package com.anjunar.blog

import com.anjunar.json.mapper.{ErrorRequest, ErrorRequestException}
import jakarta.persistence.OptimisticLockException
import jakarta.validation.{ConstraintViolationException as ValidationException}
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.{Context, Response, UriInfo}
import jakarta.ws.rs.ext.{ExceptionMapper, Provider}
import org.hibernate.StaleStateException
import org.hibernate.exception.{ConstraintViolationException as SqlConstraintException}
import org.slf4j.LoggerFactory

import java.util
import java.util.UUID
import scala.compiletime.uninitialized
import scala.jdk.CollectionConverters.*

object ProblemResponses {
  private val log = LoggerFactory.getLogger(getClass)

  def render(error: Throwable, path: String): Response = {
    val chain = Iterator.iterate(error)(_.getCause).takeWhile(_ != null).take(20).toSeq
    chain.collectFirst {
      case problem: ApiProblem => Problem.response(problem.status, problem.detail, path, problem.kind, problem.errors)
      case invalid: ErrorRequestException =>
        Problem.response(400, "The request contains invalid values.", path, Problem.validation, invalid.errors.asScala.toSeq)
      case invalid: ValidationException =>
        val errors = invalid.getConstraintViolations.asScala.toSeq.map(value =>
          new ErrorRequest(util.List.of[Any](value.getPropertyPath.toString), value.getMessage))
        Problem.response(400, "The request contains invalid values.", path, Problem.validation, errors)
      case violation: SqlConstraintException if Set("uq_blog_post_slug", "blog_post_slug_key").contains(violation.getConstraintName) =>
        Problem.response(409, "This slug is already used by another post.", path, Problem.conflict,
          Seq(new ErrorRequest(util.List.of[Any]("slug"), "Choose an unused slug.")))
      case violation: SqlConstraintException if violation.getKind == SqlConstraintException.ConstraintKind.UNIQUE =>
        Problem.response(409, "This conflicts with data that already exists.", path, Problem.conflict)
      case _: OptimisticLockException | _: StaleStateException =>
        Problem.response(409, EntityVersions.conflictDetail, path, Problem.conflict)
    }.getOrElse {
      chain.collectFirst { case value: WebApplicationException => value } match {
        case Some(web) =>
          val status = web.getResponse.getStatus
          val detail = Option(Response.Status.fromStatusCode(status)).map(_.getReasonPhrase).getOrElse(s"HTTP $status")
          val result = Response.fromResponse(Problem.response(status, detail, path))
          // Preserve authentication challenges, allowed methods and rate-limit headers.
          web.getResponse.getHeaders.asScala.foreach { (name, values) =>
            if (!Set("content-type", "content-length").contains(name.toLowerCase))
              values.asScala.foreach(value => result.header(name, value))
          }
          result.build()
        case None =>
          val id = UUID.randomUUID().toString
          log.error(s"Unexpected request failure $id at $path", error)
          Problem.response(500, "An unexpected error occurred.", path, errorId = Some(id))
      }
    }
  }
}

@Provider
class ProblemExceptionMapper extends ExceptionMapper[Throwable] {
  @Context var uriInfo: UriInfo = uninitialized
  override def toResponse(error: Throwable): Response =
    ProblemResponses.render(error, Option(uriInfo).map(_.getRequestUri.getPath).getOrElse(""))
}

// RESTEasy has built-in handling for these types; register explicit application mappers.
@Provider
class WebProblemExceptionMapper extends ExceptionMapper[WebApplicationException] {
  @Context var uriInfo: UriInfo = uninitialized
  override def toResponse(error: WebApplicationException): Response =
    ProblemResponses.render(error, Option(uriInfo).map(_.getRequestUri.getPath).getOrElse(""))
}
