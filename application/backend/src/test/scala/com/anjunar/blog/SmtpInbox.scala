package com.anjunar.blog

import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage

import java.io.{BufferedReader, ByteArrayInputStream, InputStreamReader, PrintWriter}
import java.net.{InetAddress, ServerSocket, SocketException}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Properties
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}
import scala.util.Using

// Local capture server: implements only the SMTP commands exercised by Angus.
final class SmtpInbox(port: Int) extends AutoCloseable {
  private val messages = new LinkedBlockingQueue[MimeMessage]()
  private val server = new ServerSocket(port, 10, InetAddress.getByName("127.0.0.1"))
  private val thread = Thread.ofVirtual().name("test-smtp").start(() => {
    try {
      while (!server.isClosed) {
        Using.resource(server.accept()) { socket =>
          socket.setSoTimeout(10000)
          val input = new BufferedReader(new InputStreamReader(socket.getInputStream, UTF_8))
          val output = new PrintWriter(socket.getOutputStream, true, UTF_8)
          def reply(value: String): Unit = { output.print(value + "\r\n"); output.flush() }
          reply("220 localhost test SMTP")
          var running = true
          while (running) {
            val line = input.readLine()
            if (line == null) running = false
            else if (line.startsWith("EHLO") || line.startsWith("HELO")) reply("250 localhost")
            else if (line == "DATA") {
              reply("354 Send message")
              val body = new StringBuilder()
              var data = input.readLine()
              while (data != null && data != ".") {
                body.append(if (data.startsWith("..")) data.drop(1) else data).append("\r\n")
                data = input.readLine()
              }
              messages.add(new MimeMessage(Session.getInstance(new Properties()),
                new ByteArrayInputStream(body.toString.getBytes(UTF_8))))
              reply("250 Captured")
            } else if (line == "QUIT") { reply("221 Bye"); running = false }
            else reply("250 OK")
          }
        }
      }
    } catch { case _: SocketException if server.isClosed => () }
  })

  def next(): MimeMessage = {
    val message = messages.poll(10, TimeUnit.SECONDS)
    require(message != null, "No account email reached the SMTP inbox")
    message
  }
  def empty: Boolean = messages.isEmpty
  override def close(): Unit = { server.close(); thread.join(2000) }
}
