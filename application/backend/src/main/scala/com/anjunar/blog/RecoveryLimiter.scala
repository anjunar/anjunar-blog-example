package com.anjunar.blog

import jakarta.enterprise.context.ApplicationScoped

// Separate budgets prevent mail requests from consuming the sign-in allowance.
@ApplicationScoped
class RecoveryLimiter {
  private val delegate = new LoginLimiter()
  def check(key: String, address: String): Unit = delegate.check(key, address)
}
