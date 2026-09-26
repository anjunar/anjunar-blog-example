package com.anjunar.blog

import jakarta.enterprise.inject.spi.CDI
import jakarta.ws.rs.ApplicationPath
import jakarta.ws.rs.core.Application

import java.util

@ApplicationPath("/service")
class ServerApplication extends Application {

  override def getClasses: util.Set[Class[?]] =
    CDI.current().getBeanManager
      .getExtension(classOf[RestComponentsExtension])
      .classes

}
