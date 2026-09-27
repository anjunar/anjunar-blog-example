package com.anjunar.blog

import io.undertow.servlet.api.{DeploymentInfo, ServletContainerInitializerInfo}
import io.undertow.servlet.handlers.ServletRequestContext
import jakarta.security.auth.message.config.AuthConfigFactory
import jakarta.servlet.http.HttpServletRequest
import jakarta.ws.rs.NotAuthorizedException
import org.wildfly.elytron.web.undertow.server.servlet.AuthenticationManager
import org.wildfly.security.auth.jaspi.ElytronAuthConfigFactory
import org.wildfly.security.auth.permission.LoginPermission
import org.wildfly.security.auth.server.{SecurityDomain, SecurityRealm}
import org.wildfly.security.permission.PermissionVerifier

import java.lang
import java.util.Set
import javax.naming.Context

object SoteriaIntegration {
  val requestKey = "blog.authentication.ready"

  def configure(deployment: DeploymentInfo): SecurityDomain = synchronized {
    if (System.getProperty(Context.INITIAL_CONTEXT_FACTORY) == null)
      System.setProperty(Context.INITIAL_CONTEXT_FACTORY, classOf[CdiNamingFactory].getName)
    if (AuthConfigFactory.getFactory == null) AuthConfigFactory.setFactory(new ElytronAuthConfigFactory())
    val domain = SecurityDomain.builder()
      .addRealm("soteria", SecurityRealm.EMPTY_REALM).build()
      .setDefaultRealmName("soteria")
      .setPermissionMapper((_, _) => PermissionVerifier.from(LoginPermission.getInstance()))
      .build()
    deployment.setClassLoader(getClass.getClassLoader).setHostName("localhost")
      .addSecurityRole("ADMIN").addSecurityRole("READER")
      .addServletContainerInitializer(new ServletContainerInitializerInfo(classOf[SoteriaInitializer], Set.of()))
    AuthenticationManager.builder().setSecurityDomain(domain)
      .setEnableJaspi(true).setIntegratedJaspi(false).build().configure(deployment)
    domain
  }

  def resolve(request: HttpServletRequest): Unit = {
    request.setAttribute(requestKey, lang.Boolean.TRUE)
    // This is the real Undertow security context; RESTEasy reads its principal
    // through HttpServletRequest rather than a replacement JAX-RS context.
    if (!ServletRequestContext.requireCurrent().getExchange.getSecurityContext.authenticate())
      throw new NotAuthorizedException("Session")
  }
}
