# Anjunar Blog Tutorial

The companion repository, `anjunar-blog-example`, grows alongside the Anjunar Blog
Tutorial series into a complete web application built with Scala, Scala.js, and PostgreSQL. Anjunar Stack serves as
the technical reference. The tutorial builds a single application without
multitenancy.

The articles, documentation, and examples are written in English.

## What we are building

- Visitors can read and search posts and filter them by tags.
- Users can register and sign in.
- Administrators can write, edit, and publish posts.
- Posts can contain formatted text, code examples, and images.
- The interface and posts support English and German, with English as the primary language.
- The server delivers HTML; the browser takes over interactivity through hydration.
- The application runs on its own domain over HTTPS.

The [roadmap](docs/roadmap.md) describes the planned steps.

The [article checkpoints](docs/article-checkpoints.md) identify the exact source
revision and commands for each implementation chapter.

## Current state: HTTP endpoints discovered through CDI

The project contains an sbt build and one backend module. Undertow handles HTTP,
RESTEasy routes requests to REST resources, and Weld manages their dependencies
and lifetimes through CDI. A portable CDI extension registers resources and
providers automatically. The database and frontend will follow in later steps.

### Prerequisites

- JDK 25. We will use GraalVM for server-side rendering later in the series.
- sbt. The project version, 2.0.9, is pinned in `project/build.properties`.
- Access to Maven Central for the initial build.

Scala 3.9.0 and the library versions are pinned in `build.sbt`.
Node/npm and PostgreSQL are only needed when we reach their respective chapters.
You do not need to build any other Anjunar repositories locally.

### Run the tests

From the project directory:

```text
sbt --server "application-backend/testFull"
```

The two integration tests start the actual HTTP server on available local ports.
They check the greeting and liveness endpoints, CDI injection, and a 404 response.
Test-only resources and a response filter also verify automatic registration,
request and application scopes, and destruction callbacks on request completion
and server shutdown.

Use `testFull` to execute the tests on every invocation. In sbt 2, `test` is
incremental and may skip tests that already passed.

### Start the application

```text
sbt --server "application-backend/run"
```

In a second terminal:

```text
curl http://127.0.0.1:8080/service/hello
```

In Windows PowerShell, use `curl.exe` if `curl` resolves to a PowerShell alias.
Expect HTTP 200, `Content-Type: text/plain`, and:

```text
Welcome to Anjunar Blog Tutorial!
```

Check liveness with:

```text
curl -i http://127.0.0.1:8080/service/health/live
```

Expect HTTP 200 and `UP`. This endpoint checks that the HTTP request can reach
a REST resource; it does not check external dependencies.

Press Ctrl+C to stop the application. `--server` runs sbt in the foreground.

The server binds to `127.0.0.1`. If port 8080 is already in use, set the
`BLOG_PORT` environment variable before starting the application:

```powershell
$env:BLOG_PORT = "8081"
sbt --server "application-backend/run"
```

In Bash:

```bash
BLOG_PORT=8081 sbt --server "application-backend/run"
```

## Following a request through the code

1. `ApplicationMain` starts the server and registers its shutdown hook.
2. `ServerApplication` defines the `/service` API prefix and reads the resource and provider classes collected by `RestComponentsExtension` during CDI bootstrap.
3. `HelloResource` handles `GET /hello`.
4. Weld injects `GreetingService`, which supplies the response text.

The source files are in `application/backend/src/main/scala/com/anjunar/blog`.
`META-INF/beans.xml` enables CDI discovery for annotated beans. The extension
is registered in `META-INF/services/jakarta.enterprise.inject.spi.Extension`.
Add new resources with `@Path` and an explicit CDI scope, such as
`@RequestScoped`; providers use `@Provider` and a scope.

We start with this single module. Platform and feature modules will be added
as the tutorial introduces their responsibilities. Each published article
should have a corresponding runnable commit or tag.
