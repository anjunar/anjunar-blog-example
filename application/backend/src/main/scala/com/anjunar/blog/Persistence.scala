package com.anjunar.blog

import com.arjuna.ats.jta.{TransactionManager as NarayanaTransactionManager}
import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple
import io.agroal.api.AgroalDataSource
import io.agroal.api.configuration.supplier.{AgroalConnectionFactoryConfigurationSupplier, AgroalConnectionPoolConfigurationSupplier, AgroalDataSourceConfigurationSupplier}
import io.agroal.api.security.{NamePrincipal, SimplePassword}
import io.agroal.narayana.NarayanaTransactionIntegration
import jakarta.annotation.{PostConstruct, PreDestroy}
import jakarta.enterprise.context.{ApplicationScoped, RequestScoped}
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import jakarta.persistence.{EntityManager, EntityManagerFactory}
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.{StandardServiceRegistry, StandardServiceRegistryBuilder}
import org.hibernate.engine.transaction.jta.platform.internal.NarayanaJtaPlatform
import org.postgresql.xa.PGXADataSource

import java.time.Duration
import scala.compiletime.uninitialized
import scala.util.control.NonFatal

@ApplicationScoped
class Persistence {
  @Inject
  var entityRegistry: EntityRegistry = uninitialized

  private var pool: AgroalDataSource = null
  private var factory: EntityManagerFactory = null

  @PostConstruct
  def initialize(): Unit = {
    val config = DatabaseConfig.load()
    val connection = new AgroalConnectionFactoryConfigurationSupplier()
      .connectionProviderClass(classOf[PGXADataSource])
      .jdbcUrl(config.url)
      .principal(new NamePrincipal(config.user))
      .credential(new SimplePassword(config.password))
      .loginTimeout(Duration.ofSeconds(5))
    val pooling = new AgroalConnectionPoolConfigurationSupplier()
      .maxSize(8)
      .acquisitionTimeout(Duration.ofSeconds(5))
      .connectionFactoryConfiguration(connection)
      .transactionIntegration(new NarayanaTransactionIntegration(
        NarayanaTransactionManager.transactionManager(),
        new TransactionSynchronizationRegistryImple()))
    pool = AgroalDataSource.from(new AgroalDataSourceConfigurationSupplier()
      .connectionPoolConfiguration(pooling))

    var registry: StandardServiceRegistry = null
    try {
      registry = new StandardServiceRegistryBuilder()
        .applySetting("jakarta.persistence.jtaDataSource", pool)
        .applySetting("hibernate.transaction.coordinator_class", "jta")
        .applySetting("hibernate.transaction.jta.platform",
          new NarayanaJtaPlatform())
        .applySetting("hibernate.hbm2ddl.auto", "validate")
        .applySetting("jakarta.persistence.validation.mode", "CALLBACK")
        .build()
      val sources = new MetadataSources(registry)
      entityRegistry.entityClasses.foreach(sources.addAnnotatedClass)
      factory = sources.buildMetadata().buildSessionFactory()
    } catch {
      case NonFatal(error) =>
        try {
          if (registry != null) StandardServiceRegistryBuilder.destroy(registry)
        } catch { case NonFatal(cleanup) => error.addSuppressed(cleanup) }
        try pool.close()
        catch { case NonFatal(cleanup) => error.addSuppressed(cleanup) }
        throw error
    }
  }

  def openEntityManager(): EntityManager = factory.createEntityManager()

  @Produces
  @RequestScoped
  def entityManager(transaction: RequestTransaction): EntityManager =
    transaction.entityManager

  @PreDestroy
  def close(): Unit =
    try {
      if (factory != null) factory.close()
    } finally {
      if (pool != null) pool.close()
    }
}
