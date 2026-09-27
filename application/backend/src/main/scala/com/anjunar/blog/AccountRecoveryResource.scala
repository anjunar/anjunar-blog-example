package com.anjunar.blog

import com.anjunar.json.mapper.provider.DTO
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.json.bind.annotation.JsonbProperty
import jakarta.servlet.http.HttpServletRequest
import jakarta.ws.rs.{Consumes, POST, Path, Produces}
import jakarta.ws.rs.core.{Context, MediaType, Response}

import scala.annotation.meta.field
import scala.compiletime.uninitialized

final class RecoveryResult(@(JsonbProperty @field) val outcome: String) extends DTO

@Path("/auth")
@RequestScoped
@Consumes(Array(MediaType.APPLICATION_JSON))
@Produces(Array(MediaType.APPLICATION_JSON))
class AccountRecoveryResource {
  @Inject var recovery: AccountRecovery = uninitialized
  @Inject var limiter: RecoveryLimiter = uninitialized
  @Context var request: HttpServletRequest = uninitialized

  @POST @Path("/register")
  def register(input: EmailRequest): Response = issue(input, AccountToken.register)

  @POST @Path("/forgot-password")
  def forgotPassword(input: EmailRequest): Response = issue(input, AccountToken.reset)

  @POST @Path("/confirm")
  def confirm(input: TokenPasswordRequest): RecoveryResult = complete(input, AccountToken.register)

  @POST @Path("/reset-password")
  def resetPassword(input: TokenPasswordRequest): RecoveryResult = complete(input, AccountToken.reset)

  private def issue(input: EmailRequest, purpose: String): Response = {
    limiter.check(purpose + ":" + input.email, request.getRemoteAddr)
    recovery.requestLink(input.email, purpose)
    Response.accepted(new RecoveryResult("accepted")).build()
  }

  private def complete(input: TokenPasswordRequest, purpose: String): RecoveryResult = {
    limiter.check("consume:" + AccountToken.digest(input.token), request.getRemoteAddr)
    recovery.complete(input, purpose)
    new RecoveryResult("completed")
  }
}
