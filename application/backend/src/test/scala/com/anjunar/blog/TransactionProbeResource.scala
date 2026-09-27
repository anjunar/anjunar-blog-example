package com.anjunar.blog

import com.arjuna.ats.jta.{UserTransaction as NarayanaUserTransaction}
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.transaction.Status
import jakarta.ws.rs.{GET, POST, Path, PathParam, Produces}
import jakarta.ws.rs.core.{Response, StreamingOutput}

import java.lang
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.compiletime.uninitialized

object TransactionProbe {
  // Set once by the database suite; the generated identifier contains only letters and digits.
  var table: String = ""
  val committed = ConcurrentHashMap.newKeySet[UUID]()
}

@Path("/_test/transactions")
@RequestScoped
@Produces(Array("text/plain"))
class TransactionProbeResource {
  @Inject
  var entityManager: EntityManager = uninitialized

  @Inject
  var transaction: RequestTransaction = uninitialized

  private def insert(id: UUID, parent: UUID = null): Unit = {
    entityManager.createNativeQuery(s"insert into ${TransactionProbe.table} (id, parent_id) values (:id, :parent)")
      .setParameter("id", id)
      .setParameter("parent", parent)
      .executeUpdate()
  }

  @POST
  @Path("/{mode}/{id}")
  def write(@PathParam("mode") mode: String, @PathParam("id") id: UUID): Response = {
    transaction.afterCommit(() => { TransactionProbe.committed.add(id); () })
    insert(id, if (mode == "commit-failure") UUID.randomUUID() else null)
    mode match {
      case "rejected" => Response.status(400).entity("Rejected\n").build()
      case "sql-failure" =>
        entityManager.createNativeQuery("select 1 / 0", classOf[lang.Integer]).getSingleResult
        Response.ok().build()
      case "rollback-only" =>
        NarayanaUserTransaction.userTransaction().setRollbackOnly()
        Response.ok("Saved\n").build()
      case "empty" => Response.noContent().build()
      case "serialize" | "writer-failure" =>
        val body: StreamingOutput = output => {
          require(entityManager.isOpen, "EntityManager closed before serialization")
          require(NarayanaUserTransaction.userTransaction().getStatus == Status.STATUS_ACTIVE)
          val count = entityManager.createNativeQuery(
            s"select count(*) from ${TransactionProbe.table} where id = :id", classOf[lang.Long])
            .setParameter("id", id).getSingleResult
          output.write(s"Rows: $count\n".getBytes(UTF_8))
          output.flush()
          if (mode == "writer-failure") throw new IOException("Deliberate serialization failure")
        }
        Response.ok(body).build()
      case _ => Response.ok("Saved\n").build()
    }
  }

  @GET
  @Path("/read/{id}")
  def read(@PathParam("id") id: UUID): String = {
    insert(id)
    "This write must be rolled back\n"
  }
}
