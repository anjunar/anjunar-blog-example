package com.anjunar.blog

import jakarta.ws.rs.WebApplicationException
import org.scalatest.funsuite.AnyFunSuite

import java.time.Instant
import java.util.UUID

class SecuritySpec extends AnyFunSuite {
  private val password = "a long tutorial passphrase"

  test("password hashing uses random salts and verifies only the matching password") {
    val first = PasswordHash.create(password)
    val second = PasswordHash.create(password)
    assert(first != second)
    assert(first.startsWith("pbkdf2-sha256$600000$"))
    assert(!first.contains(password))
    assert(PasswordHash.verify(password, first))
    assert(!PasswordHash.verify("another tutorial passphrase", first))
  }

  test("hash parsing rejects plaintext, malformed encodings and attacker-selected work factors") {
    val good = PasswordHash.create(password)
    for (stored <- Seq(password, "", "pbkdf2-sha256$600000$!$!",
      good.replace("$600000$", "$2147483647$"), good + "$extra")) {
      assert(!PasswordHash.verify(password, stored))
    }
    assert(!PasswordHash.verify(null, good))
    assert(!PasswordHash.verify("x" * 129, good))
  }

  test("new passwords have bounded length without composition or trimming rules") {
    assert(!PasswordHash.acceptable("x" * 14))
    assert(PasswordHash.acceptable("x" * 15))
    assert(PasswordHash.acceptable("x" * 128))
    assert(!PasswordHash.acceptable("x" * 129))
    val spaced = "  a long tutorial passphrase  "
    assert(PasswordHash.verify(spaced, PasswordHash.create(spaced)))
    assert(!PasswordHash.verify(spaced.trim, PasswordHash.create(spaced)))
  }

  test("email normalization is explicit and independent of the default locale") {
    assert(Account.canonicalEmail("  ADMIN@Example.COM ") == "admin@example.com")
    assert(Account.canonicalEmail(null).isEmpty)
  }

  test("session validity checks account lock, authentication version and absolute expiry") {
    val now = Instant.parse("2026-09-27T12:00:00Z")
    val account = new Account()
    account.id = UUID.randomUUID()
    val principal = SessionPrincipal(account.id, 0, now)
    assert(principal.active(account, now))
    assert(!principal.active(account, now.plusSeconds(SecurityConfig.absoluteSeconds)))
    assert(!principal.active(account, now.minusSeconds(1)))
    assert(!principal.active(null, now))
    account.locked = true
    assert(!principal.active(account, now))
    account.locked = false
    account.authenticationVersion = 1
    assert(!principal.active(account, now))
  }

  test("account throttling applies across addresses and expires at the window boundary") {
    val limiter = new LoginLimiter()
    for (i <- 1 to 5) limiter.check("person@example.com", s"address-$i", 100)
    val denied = intercept[WebApplicationException](limiter.check("person@example.com", "other", 159))
    assert(denied.getResponse.getStatus == 429)
    assert(denied.getResponse.getHeaderString("Retry-After") == "60")
    limiter.check("person@example.com", "other", 160)
  }

  test("address throttling applies even when callers change email") {
    val limiter = new LoginLimiter()
    for (i <- 1 to 30) limiter.check(s"$i@example.com", "address", 100)
    assert(intercept[WebApplicationException](limiter.check("other@example.com", "address", 100))
      .getResponse.getStatus == 429)
  }

  test("CSRF tokens are unpredictable fixed-size values") {
    val tokens = Vector.fill(100)(SessionIdentity.newToken())
    assert(tokens.distinct.size == tokens.size)
    assert(tokens.forall(_.matches("[A-Za-z0-9_-]{43}")))
  }
}
