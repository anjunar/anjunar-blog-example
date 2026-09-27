import org.scalajs.linker.interface.ModuleKind
import org.scalajs.sbtplugin.ScalaJSPlugin

ThisBuild / organization := "com.anjunar"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.9.0"
// Keep local publishLocal artifacts out of the reproducible tutorial build.
ThisBuild / externalResolvers := Seq(Resolver.mavenCentral)

lazy val backend = Project("application-backend", file("application/backend"))
  .settings(
    libraryDependencies ++= Seq(
      "jakarta.ws.rs" % "jakarta.ws.rs-api" % "4.0.0",
      "jakarta.enterprise" % "jakarta.enterprise.cdi-api" % "4.1.0",
      "org.jboss.resteasy" % "resteasy-undertow-cdi" % "7.0.5.Final",
      "io.undertow" % "undertow-core" % "2.4.3.Final",
      "io.undertow.ee" % "undertow-servlet" % "2.0.2.Final",
      "org.jboss.weld.servlet" % "weld-servlet-core" % "6.0.4.Final",
      "jakarta.security.enterprise" % "jakarta.security.enterprise-api" % "4.0.0",
      "jakarta.authentication" % "jakarta.authentication-api" % "3.1.0",
      "jakarta.authorization" % "jakarta.authorization-api" % "3.0.0",
      "jakarta.enterprise" % "jakarta.enterprise.cdi-el-api" % "4.1.0",
      "jakarta.json" % "jakarta.json-api" % "2.1.3",
      "org.glassfish.soteria" % "soteria" % "4.0.2",
      "org.glassfish.soteria" % "soteria.spi.bean.decorator.weld" % "4.0.2",
      "com.nimbusds" % "nimbus-jose-jwt" % "10.10",
      "org.wildfly.security.elytron-web" % "undertow-server-servlet" % "4.2.1.Final",
      "org.wildfly.security" % "wildfly-elytron-auth-server-http" % "2.8.4.Final",
      "org.wildfly.security" % "wildfly-elytron-http-util" % "2.8.4.Final",
      "org.wildfly.security.jakarta" % "jakarta-authentication" % "4.0.0.Final",
      "com.anjunar" %% "json-mapper" % "1.1.5",
      "com.anjunar.hibernateddl" %% "schema-integration" % "1.1.0",
      "org.hibernate.orm" % "hibernate-core" % "7.4.10.Final",
      "org.hibernate.validator" % "hibernate-validator" % "9.1.4.Final",
      "org.glassfish.expressly" % "expressly" % "6.0.0",
      "org.postgresql" % "postgresql" % "42.7.13",
      "org.jboss.narayana.jta" % "narayana-jta" % "7.3.4.Final",
      "io.agroal" % "agroal-pool" % "3.2.1",
      "io.agroal" % "agroal-narayana" % "3.2.1",
      "org.scalatest" %% "scalatest" % "3.2.20" % Test
    ),
    // Undertow 2.4 uses the separately published Servlet 6.1 integration.
    excludeDependencies += ExclusionRule("io.undertow", "undertow-servlet"),
    Compile / run / mainClass := Some("com.anjunar.blog.ApplicationMain"),
    Compile / run / fork := true,
    Test / fork := true,
    Test / parallelExecution := false
  )

lazy val root = Project("anjunar-blog-tutorial", file("."))
  .aggregate(backend, frontend)
  .settings(publish / skip := true)

lazy val frontend = Project("application-frontend", file("application/frontend"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "com.anjunar" %% "scalajs-ui-core" % "1.0.9",
      "com.anjunar" %% "scalajs-ui-json" % "1.0.9",
      "com.anjunar" %% "scalajs-ui-router" % "1.0.9",
      "com.anjunar" %% "scalajs-ui-forms" % "1.0.9",
      "org.scalatest" %% "scalatest" % "3.2.20" % Test
    ),
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig := scalaJSLinkerConfig.value.withModuleKind(ModuleKind.ESModule)
  )

lazy val frontendAssets = taskKey[File]("Build and copy the browser application")
frontendAssets / aggregate := false

frontendAssets := Def.uncached {
  val _ = (frontend / Compile / fastLinkJS).value
  val linked = (frontend / Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value
  val destination = (LocalRootProject / baseDirectory).value / "target" / "frontend"
  IO.createDirectory(destination)
  IO.copyDirectory(linked, destination)
  IO.copyDirectory((frontend / Compile / resourceDirectory).value, destination)
  destination
}
