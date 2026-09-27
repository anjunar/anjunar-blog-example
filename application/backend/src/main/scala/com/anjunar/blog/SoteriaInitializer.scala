package com.anjunar.blog

import jakarta.security.auth.message.config.AuthConfigFactory
import jakarta.servlet.{ServletContainerInitializer, ServletContext}
import org.glassfish.soteria.mechanisms.jaspic.Jaspic
import org.glassfish.soteria.servlet.SamRegistrationInstaller

import java.util.Set

/** Fail startup if Soteria cannot install its authentication module. */
class SoteriaInitializer extends ServletContainerInitializer {
  override def onStartup(classes: Set[Class[?]], context: ServletContext): Unit = {
    new SamRegistrationInstaller().onStartup(classes, context)
    require(AuthConfigFactory.getFactory.getConfigProvider("HttpServlet", Jaspic.getAppContextID(context), null) != null,
      "Soteria did not register its Jakarta Authentication module")
  }
}
