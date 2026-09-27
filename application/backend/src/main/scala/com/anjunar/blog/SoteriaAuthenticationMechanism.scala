package com.anjunar.blog

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.security.enterprise.AuthenticationStatus
import jakarta.security.enterprise.authentication.mechanism.http.{HttpAuthenticationMechanism, HttpMessageContext}
import jakarta.security.enterprise.credential.UsernamePasswordCredential
import jakarta.security.enterprise.identitystore.{CredentialValidationResult, IdentityStoreHandler}
import jakarta.servlet.http.{HttpServletRequest, HttpServletResponse}

import java.lang
import java.util.Set
import scala.compiletime.uninitialized

@ApplicationScoped
class SoteriaAuthenticationMechanism extends HttpAuthenticationMechanism {
  @Inject var stores: IdentityStoreHandler = uninitialized
  @Inject var identity: SessionIdentity = uninitialized

  override def validateRequest(request: HttpServletRequest, response: HttpServletResponse,
      context: HttpMessageContext): AuthenticationStatus = {
    // Undertow can call before JAX-RS/CDI request work. Database authentication
    // starts explicitly after TransactionBoundary has opened the persistence context.
    if (request.getAttribute(SoteriaIntegration.requestKey) != lang.Boolean.TRUE) return context.doNothing()
    context.getAuthParameters.getCredential match {
      case credential: UsernamePasswordCredential =>
        val result = stores.validate(credential)
        if (result.getStatus != CredentialValidationResult.Status.VALID) AuthenticationStatus.SEND_FAILURE
        else {
          identity.accept(request, result.getCallerPrincipal.asInstanceOf[SessionPrincipal])
          context.notifyContainerAboutLogin(result)
        }
      case null =>
        identity.resolve(request) match {
          case Some(principal) => context.notifyContainerAboutLogin(principal, Set.of(identity.requireAccount().role))
          case None => context.doNothing()
        }
      case _ => AuthenticationStatus.SEND_FAILURE
    }
  }

  override def cleanSubject(request: HttpServletRequest, response: HttpServletResponse,
      context: HttpMessageContext): Unit = {
    identity.clear(request)
    context.cleanClientSubject()
  }
}
