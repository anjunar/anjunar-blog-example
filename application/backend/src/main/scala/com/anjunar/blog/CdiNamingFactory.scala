package com.anjunar.blog

import jakarta.enterprise.inject.spi.CDI

import java.util.Hashtable
import javax.naming.{Context, InitialContext, Name, NameNotFoundException, OperationNotSupportedException}
import javax.naming.spi.InitialContextFactory

/** Embedded Weld has no java:comp namespace; Soteria looks up its BeanManager there. */
class CdiNamingFactory extends InitialContextFactory {
  override def getInitialContext(environment: Hashtable[?, ?]): Context = new InitialContext(true) {
    override def lookup(name: String): AnyRef = name match {
      case "java:comp/BeanManager" | "java:comp/env/BeanManager" => CDI.current().getBeanManager
      case _ => throw new NameNotFoundException(name)
    }
    override def lookup(name: Name): AnyRef = lookup(name.toString)
    override protected def getDefaultInitCtx(): Context =
      throw new OperationNotSupportedException("Only the CDI BeanManager lookup is provided")
    override def close(): Unit = ()
  }
}
