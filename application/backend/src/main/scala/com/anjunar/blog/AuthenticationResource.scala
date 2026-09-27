package com.anjunar.blog

import com.anjunar.json.mapper.provider.DTO
import jakarta.inject.Inject
import jakarta.enterprise.context.RequestScoped
import jakarta.json.bind.annotation.JsonbProperty
import jakarta.persistence.EntityManager
import jakarta.security.enterprise.{AuthenticationStatus, SecurityContext}
import jakarta.security.enterprise.authentication.mechanism.http.AuthenticationParameters
import jakarta.security.enterprise.credential.UsernamePasswordCredential
import jakarta.servlet.http.{HttpServletRequest, HttpServletResponse}
import jakarta.ws.rs.{Consumes, GET, NotAuthorizedException, POST, Path, Produces, WebApplicationException}
import jakarta.ws.rs.core.{Context, MediaType, Response}

import scala.compiletime.uninitialized
import scala.annotation.meta.field

final class SessionState(
    @(JsonbProperty @field) val csrfToken: String,
    @(JsonbProperty @field) val account: Account
) extends DTO

@Path("/auth")
@RequestScoped
@Produces(Array(MediaType.APPLICATION_JSON))
class AuthenticationResource {
  @Inject var manager: EntityManager = uninitialized
  @Inject var identity: SessionIdentity = uninitialized
  @Inject var limiter: LoginLimiter = uninitialized
  @Inject var transaction: RequestTransaction = uninitialized
  @Inject var security: SecurityContext = uninitialized
  @Context var request: HttpServletRequest = uninitialized
  @Context var response: HttpServletResponse = uninitialized

  @GET
  @Path("/session")
  @EntityGraph("Account.self")
  def session(): SessionState = new SessionState(identity.csrfToken(), identity.account.orNull)

  @GET
  @Path("/me")
  @EntityGraph("Account.self")
  def me(): Data[Account] =
    new Data(identity.requireAccount(), Schema.forGraph(Account.schema, manager.getEntityGraph("Account.self")))

  @POST
  @Path("/login")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @EntityGraph("Account.self")
  def login(input: LoginRequest): SessionState = {
    if (identity.account.nonEmpty) throw new WebApplicationException(Response.status(409).build())
    limiter.check(input.email, request.getRemoteAddr)
    val credential = new UsernamePasswordCredential(input.email, input.password)
    val status = try security.authenticate(request, response,
      AuthenticationParameters.withParams().credential(credential))
    finally credential.clear()
    if (status != AuthenticationStatus.SUCCESS) throw new NotAuthorizedException("Session")
    val account = identity.requireAccount()
    val token = SessionIdentity.newToken()
    val principal = identity.principal
    transaction.afterCommit(() => identity.establish(principal, token))
    new SessionState(token, account)
  }

  @POST
  @Path("/logout")
  @EntityGraph("Account.self")
  def logout(): SessionState = {
    val token = SessionIdentity.newToken()
    transaction.afterCommit(() => identity.end(token))
    new SessionState(token, null)
  }
}
