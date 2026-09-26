ThisBuild / organization := "com.anjunar"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.9.0"

lazy val backend = Project("application-backend", file("application/backend"))
  .settings(
    libraryDependencies ++= Seq(
      "jakarta.ws.rs" % "jakarta.ws.rs-api" % "4.0.0",
      "jakarta.enterprise" % "jakarta.enterprise.cdi-api" % "4.1.0",
      "org.jboss.resteasy" % "resteasy-undertow-cdi" % "7.0.5.Final",
      "io.undertow" % "undertow-core" % "2.4.3.Final",
      "io.undertow.ee" % "undertow-servlet" % "2.0.2.Final",
      "org.jboss.weld.servlet" % "weld-servlet-core" % "6.0.4.Final",
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
  .aggregate(backend)
  .settings(publish / skip := true)
