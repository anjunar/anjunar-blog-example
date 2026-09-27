package com.anjunar.blog

import jakarta.annotation.security.{DenyAll, PermitAll, RolesAllowed}
import org.scalatest.funsuite.AnyFunSuite

@RolesAllowed(Array("ADMIN"))
class PolicyFixture {
  def inherited(): Unit = ()
  @PermitAll def publicMethod(): Unit = ()
  @DenyAll def closed(): Unit = ()
  @RolesAllowed(Array("READER")) def reader(): Unit = ()
  @PermitAll @DenyAll def conflicting(): Unit = ()
}
class UnannotatedFixture { def closed(): Unit = () }

class EndpointPolicySpec extends AnyFunSuite {
  private def policy(name: String) =
    EndpointPolicy.of(classOf[PolicyFixture].getMethod(name), classOf[PolicyFixture])

  test("class roles apply, method policies override them and role names are exact") {
    assert(policy("inherited").allows(_ == "ADMIN"))
    assert(!policy("inherited").allows(_ == "admin"))
    assert(policy("publicMethod").allows(_ => false))
    assert(!policy("closed").allows(_ => true))
    assert(policy("reader").allows(_ == "READER"))
    assert(!policy("reader").allows(_ == "ADMIN"))
  }

  test("unannotated endpoints are closed and conflicting policies fail") {
    assert(!EndpointPolicy.of(classOf[UnannotatedFixture].getMethod("closed"),
      classOf[UnannotatedFixture]).allows(_ => true))
    intercept[IllegalArgumentException](policy("conflicting"))
  }
}
