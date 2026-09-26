package com.anjunar.blog

import jakarta.ws.rs.ApplicationPath
import jakarta.ws.rs.core.Application

import java.util

@ApplicationPath("/service")
class ServerApplication extends Application {

  override def getClasses: util.Set[Class[?]] =
    util.Set.of[Class[?]](classOf[HelloResource])

}
