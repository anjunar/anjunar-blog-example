package com.anjunar.blog

import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.mail.{Message, Session}
import jakarta.mail.internet.{InternetAddress, MimeMessage}

import java.net.URI
import java.util.{Date, Properties}
import java.util.concurrent.{ArrayBlockingQueue, RejectedExecutionException, ThreadPoolExecutor, TimeUnit}
import java.util.logging.Logger
import scala.util.control.NonFatal

@ApplicationScoped
class AccountMail {
  private val logger = Logger.getLogger(classOf[AccountMail].getName)
  private val executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.SECONDS,
    new ArrayBlockingQueue[Runnable](64), new ThreadPoolExecutor.AbortPolicy())

  def configuration(): MailConfig = MailConfig.load()

  def enqueue(config: MailConfig, email: String, purpose: String, token: String): Unit =
    try executor.execute(() => {
      try deliver(config, email, purpose, token)
      catch {
        // Provider errors may contain recipients or message text. Never log them here.
        case NonFatal(error) => logger.warning(s"Account email delivery failed (${error.getClass.getSimpleName}); request a new link.")
      }
    })
    catch {
      case _: RejectedExecutionException =>
        logger.warning("Account email queue is full or stopping; request a new link.")
    }

  private def deliver(config: MailConfig, email: String, purpose: String, token: String): Unit = {
    val properties = new Properties()
    properties.setProperty("mail.from", config.from)
    properties.setProperty("mail.smtp.localhost", URI.create(config.origin).getHost)
    properties.setProperty("mail.smtp.host", config.host)
    properties.setProperty("mail.smtp.port", config.port.toString)
    properties.setProperty("mail.smtp.auth", config.username.isDefined.toString)
    properties.setProperty("mail.smtp.starttls.enable", config.startTls.toString)
    properties.setProperty("mail.smtp.starttls.required", config.startTls.toString)
    properties.setProperty("mail.smtp.ssl.checkserveridentity", "true")
    properties.setProperty("mail.smtp.connectiontimeout", "5000")
    properties.setProperty("mail.smtp.timeout", "5000")
    properties.setProperty("mail.smtp.writetimeout", "5000")
    val session = Session.getInstance(properties)
    val registration = purpose == AccountToken.register
    val path = if (registration) "/en/confirm" else "/en/reset-password"
    val link = s"${config.origin}$path#token=$token"
    val message = new MimeMessage(session)
    message.setFrom(new InternetAddress(config.from))
    message.setRecipient(Message.RecipientType.TO, new InternetAddress(email))
    message.setSubject(if (registration) "Confirm your blog account" else "Reset your blog password", "UTF-8")
    message.setSentDate(new Date())
    message.setText(
      s"""Open this link to ${if (registration) "confirm your email and choose a password" else "choose a new password"}:
         |
         |$link
         |
         |This link expires in 30 minutes and works once. Requesting another link replaces it.
         |If you did not request this email, you can ignore it.
         |""".stripMargin, "UTF-8")
    val transport = session.getTransport("smtp")
    try {
      transport.connect(config.host, config.port, config.username.orNull, config.password.orNull)
      transport.sendMessage(message, message.getAllRecipients)
    } finally transport.close()
  }

  @PreDestroy
  def close(): Unit = {
    executor.shutdown()
    if (!executor.awaitTermination(10, TimeUnit.SECONDS)) executor.shutdownNow()
  }
}
