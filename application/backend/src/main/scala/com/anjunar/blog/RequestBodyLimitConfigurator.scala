package com.anjunar.blog

import dev.resteasy.embedded.server.UndertowBuilderConfigurator
import io.undertow.{Undertow, UndertowOptions}

import java.lang

class RequestBodyLimitConfigurator extends UndertowBuilderConfigurator {
  override def configure(builder: Undertow.Builder): Unit =
    // Configure the listener before HTTP/1.1 and HTTP/2 capture their body limit.
    // ImageContent enforces 5 MiB; JSON readers retain their smaller limits.
    builder.setServerOption(UndertowOptions.MAX_ENTITY_SIZE, lang.Long.valueOf(8L * 1024L * 1024L))
}
