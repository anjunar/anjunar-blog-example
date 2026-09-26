package com.anjunar.blog

import com.arjuna.ats.jta.{UserTransaction as NarayanaUserTransaction}
import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.persistence.{EntityManager, FlushModeType}
import jakarta.transaction.Status

import scala.compiletime.uninitialized
import scala.util.control.NonFatal

@RequestScoped
class RequestTransaction {
  @Inject
  var persistence: Persistence = uninitialized

  private var manager: EntityManager = null
  private var started = false
  private var readOnly = false
  private def transaction = NarayanaUserTransaction.userTransaction()

  def active: Boolean = started

  def entityManager: EntityManager = {
    require(started && manager != null, "No database transaction is active for this request")
    manager
  }

  def begin(readRequest: Boolean): Unit = {
    require(transaction.getStatus == Status.STATUS_NO_TRANSACTION, "A transaction is already active")
    transaction.setTransactionTimeout(30)
    transaction.begin()
    started = true
    readOnly = readRequest
    try {
      manager = persistence.openEntityManager()
      manager.joinTransaction()
      if (readOnly) manager.setFlushMode(FlushModeType.COMMIT)
    } catch {
      case NonFatal(error) =>
        try finish(false)
        catch { case NonFatal(cleanup) => error.addSuppressed(cleanup) }
        throw error
    }
  }

  def flush(successful: Boolean): Unit =
    if (started && successful && !readOnly) entityManager.flush()

  def finish(successful: Boolean): Unit =
    if (started) {
      started = false
      try {
        if (successful && !readOnly) transaction.commit()
        else if (transaction.getStatus != Status.STATUS_NO_TRANSACTION) transaction.rollback()
      } catch {
        case NonFatal(error) =>
          try {
            if (transaction.getStatus != Status.STATUS_NO_TRANSACTION) transaction.rollback()
          } catch { case NonFatal(cleanup) => error.addSuppressed(cleanup) }
          throw error
      } finally {
        if (manager != null) {
          try manager.close()
          finally manager = null
        }
      }
    }

  @PreDestroy
  def abortUnfinishedRequest(): Unit = finish(false)
}
