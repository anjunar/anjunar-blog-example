package com.anjunar.blog

import com.anjunar.hibernateddl.executor.{ExecutionOptions, PreviewOutcome, PreviewRendering}
import com.anjunar.hibernateddl.integration.HibernateSchemaMigration
import jakarta.enterprise.inject.se.SeContainerInitializer
import org.hibernate.boot.{Metadata, MetadataSources}
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.postgresql.ds.PGSimpleDataSource

import javax.sql.DataSource
import scala.util.Using

object SchemaMain {
  def main(args: Array[String]): Unit = {
    val (command, adoptExisting) = args.toList match {
      case List("preview") => ("preview", false)
      case List("preview", "--adopt-existing") => ("preview", true)
      case List("migrate") => ("migrate", false)
      case List("migrate", "--adopt-existing") => ("migrate", true)
      case _ => throw new IllegalArgumentException(
        "Usage: SchemaMain preview|migrate [--adopt-existing]")
    }

    val config = DatabaseConfig.load()
    val exitCode = Using.resource(SeContainerInitializer.newInstance().initialize()) { container =>
      val entities = container.select(classOf[EntityRegistry]).get()
      withMetadata(config, entities) { (metadata, dataSource) =>
        val options = ExecutionOptions(adoptExistingSchema = adoptExisting)
        command match {
          case "preview" =>
            val report = HibernateSchemaMigration.preview(metadata, dataSource, options)
            println(PreviewRendering.text(report))
            report.outcome match {
              case PreviewOutcome.Ready => 0
              case PreviewOutcome.Blocked => 2
              case PreviewOutcome.Incomplete => 3
            }
          case "migrate" =>
            val result = HibernateSchemaMigration.migrate(metadata, dataSource, options)
            println(s"${result.status}: revision ${result.revision}, ${result.statementCount} SQL statements")
            0
        }
      }
    }
    if (exitCode != 0) sys.exit(exitCode)
  }

  private[blog] def withMetadata[A](config: DatabaseConfig, entities: EntityRegistry)(
      work: (Metadata, DataSource) => A): A = {
    val dataSource = new PGSimpleDataSource()
    dataSource.setUrl(config.url)
    dataSource.setUser(config.user)
    dataSource.setPassword(config.password)
    dataSource.setLoginTimeout(5)

    // The migration executor owns a JDBC transaction, outside the request's JTA boundary.
    val registry = new StandardServiceRegistryBuilder()
      .applySetting("hibernate.connection.datasource", dataSource)
      .applySetting("hibernate.hbm2ddl.auto", "validate")
      .build()
    try {
      val sources = new MetadataSources(registry)
      entities.entityClasses.foreach(sources.addAnnotatedClass)
      work(sources.buildMetadata(), dataSource)
    } finally StandardServiceRegistryBuilder.destroy(registry)
  }
}
