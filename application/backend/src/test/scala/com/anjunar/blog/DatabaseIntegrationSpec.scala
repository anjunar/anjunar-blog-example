package com.anjunar.blog

import dev.resteasy.embedded.server.UndertowCdiEmbeddedServer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

import java.net.{InetAddress, ServerSocket, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.sql.DriverManager
import java.time.Duration
import java.util.UUID

class DatabaseIntegrationSpec extends AnyFunSuite with BeforeAndAfterAll {
  private var server: UndertowCdiEmbeddedServer = null
  private var client: HttpClient = null
  private var port = 0
  private val table = "abtprobe" + UUID.randomUUID().toString.replace("-", "")

  private def connection() = {
    val config = DatabaseConfig.load()
    DriverManager.getConnection(config.url, config.user, config.password)
  }

  private def sql(statement: String): Unit = {
    val conn = connection()
    try {
      val command = conn.createStatement()
      try command.execute(statement)
      finally command.close()
    } finally conn.close()
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    sql(s"create table $table (id uuid primary key, parent_id uuid references $table(id) deferrable initially deferred)")
    TransactionProbe.table = table
    val reservation = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    port = try reservation.getLocalPort finally reservation.close()
    server = ApplicationMain.start(port)
    client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
  }

  override protected def afterAll(): Unit =
    try {
      if (client != null) client.close()
    } finally {
      try {
        if (server != null) server.stop()
      } finally {
        try sql(s"drop table if exists $table")
        finally super.afterAll()
      }
    }

  private def request(method: String, path: String): HttpResponse[String] = {
    val request = HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port/service/$path"))
      .timeout(Duration.ofSeconds(20))
      .method(method, HttpRequest.BodyPublishers.noBody())
      .build()
    client.send(request, HttpResponse.BodyHandlers.ofString())
  }

  private def exists(id: UUID): Boolean = {
    val conn = connection()
    try {
      val query = conn.prepareStatement(s"select count(*) from $table where id = ?")
      try {
        query.setObject(1, id)
        val result = query.executeQuery()
        try { result.next(); result.getInt(1) == 1 }
        finally result.close()
      } finally query.close()
    } finally conn.close()
  }

  test("readiness reaches PostgreSQL through Hibernate and the JTA pool") {
    val response = request("GET", "health/ready")
    assert(response.statusCode() == 200)
    assert(response.body() == "UP\n")
  }

  test("a successful write commits before its response is delivered") {
    val id = UUID.randomUUID()
    val response = request("POST", s"_test/transactions/save/$id")
    assert(response.statusCode() == 200)
    assert(response.body() == "Saved\n")
    assert(exists(id))
  }

  test("an error status rolls back completed SQL statements") {
    val id = UUID.randomUUID()
    assert(request("POST", s"_test/transactions/rejected/$id").statusCode() == 400)
    assert(!exists(id))
  }

  test("an SQL exception rolls back the preceding insert") {
    val id = UUID.randomUUID()
    assert(request("POST", s"_test/transactions/sql-failure/$id").statusCode() == 500)
    assert(!exists(id))
  }

  test("the writer can query through the same open persistence context") {
    val id = UUID.randomUUID()
    val response = request("POST", s"_test/transactions/serialize/$id")
    assert(response.statusCode() == 200)
    assert(response.body() == "Rows: 1\n")
    assert(exists(id))
  }

  test("a writer failure rolls back and does not send its partial success body") {
    val id = UUID.randomUUID()
    val response = request("POST", s"_test/transactions/writer-failure/$id")
    assert(response.statusCode() == 500)
    assert(!response.body().contains("Rows:"))
    assert(!exists(id))
  }

  test("a deferred constraint failure does not send a success response") {
    val id = UUID.randomUUID()
    val response = request("POST", s"_test/transactions/commit-failure/$id")
    assert(response.statusCode() == 500)
    assert(!response.body().contains("Saved"))
    assert(!exists(id))
  }

  test("a rollback-only transaction cannot produce a success response") {
    val id = UUID.randomUUID()
    val response = request("POST", s"_test/transactions/rollback-only/$id")
    assert(response.statusCode() == 500)
    assert(!response.body().contains("Saved"))
    assert(!exists(id))
  }

  test("GET and HEAD discard writes") {
    for (method <- Seq("GET", "HEAD")) {
      val id = UUID.randomUUID()
      assert(request(method, s"_test/transactions/read/$id").statusCode() == 200)
      assert(!exists(id))
    }
  }

  test("a response without a body still completes its transaction") {
    val id = UUID.randomUUID()
    val response = request("POST", s"_test/transactions/empty/$id")
    assert(response.statusCode() == 204)
    assert(response.body().isEmpty)
    assert(exists(id))
  }
}
