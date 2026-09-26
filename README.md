# Anjunar Blog Tutorial

The companion repository, `anjunar-blog-example`, grows alongside the Anjunar Blog
Tutorial series into a complete web application built with Scala, Scala.js, and
PostgreSQL. Anjunar Stack is the technical reference. The tutorial builds one
application without multitenancy.

The articles, documentation, and examples are written in English.

## What we are building

- Visitors can read and search posts and filter them by tags.
- Users can register and sign in.
- Administrators can write, edit, and publish posts.
- Posts can contain formatted text, code examples, and images.
- The interface and posts support English and German, with English as the primary language.
- The server delivers HTML; the browser takes over interactivity through hydration.
- The application runs on its own domain over HTTPS.

Follow the [roadmap](docs/roadmap.md) and the immutable
[article checkpoints](docs/article-checkpoints.md).

## Current state: database access and request transactions

Undertow, RESTEasy, and Weld serve resources discovered through CDI.
Hibernate uses an Agroal connection pool with Narayana/JTA and PostgreSQL.
A request owns its EntityManager and transaction through response serialization.

There are no domain entities yet. The readiness endpoint executes `select 1`;
chapter 5 introduces `BlogPost`.

### Prerequisites

- JDK 25. We will use GraalVM for server-side rendering later.
- sbt; the project selects sbt 2.0.9 and Scala 3.9.0.
- PostgreSQL 18, either installed locally or started with Docker Compose.
- Access to Maven Central for the initial build.

All JVM library versions are pinned in `build.sbt`. Node/npm enters the project
in the frontend chapters. No other Anjunar repository needs a local build.

### Start a development database

Set a password in the same terminal that will run Docker Compose and sbt.
For this local tutorial, in PowerShell:

```powershell
$env:BLOG_DB_PASSWORD = "local-blog-password"
docker compose up -d --wait
```

In Bash:

```bash
export BLOG_DB_PASSWORD=local-blog-password
docker compose up -d --wait
```

The Compose file starts PostgreSQL 18.6 on `127.0.0.1:5433`, creates the
`anjunar_blog` database and the `blog` development user, and stores its data
in a named volume. `docker compose down` stops it and preserves that volume.
The image initializes credentials only for an empty data directory.

For an existing local PostgreSQL installation, create a separate tutorial
database and role, then set these variables to its connection details:

| Variable | Default |
| --- | --- |
| `BLOG_DB_URL` | `jdbc:postgresql://127.0.0.1:5433/anjunar_blog` |
| `BLOG_DB_USER` | `blog` |
| `BLOG_DB_PASSWORD` | Required; no default |

The application reads environment variables directly; it does not load a
`.env` file.

### Run the tests

With that development database running:

```text
sbt --server "application-backend/testFull"
```

Expect **12 successful tests**. The database suite creates its own uniquely
named probe table and drops it afterward. It verifies committed and rolled-back
rows through separate JDBC connections. It also checks serialization failures,
deferred constraint failures, rollback-only transactions, GET/HEAD, and
responses without a body. Intentional failure cases produce server error logs.

The original HTTP and CDI lifecycle tests still run. Use `testFull` because
sbt 2's incremental `test` can skip previously successful tests.

### Start the application

```text
sbt --server "application-backend/run"
```

In another terminal:

```text
curl -i http://127.0.0.1:8080/service/hello
curl -i http://127.0.0.1:8080/service/health/live
curl -i http://127.0.0.1:8080/service/health/ready
```

Use `curl.exe` in Windows PowerShell if necessary. Expect HTTP 200 for each:
the greeting, `UP`, and `UP`.

Liveness bypasses database access. Readiness runs a query through Hibernate
and the transaction boundary; a database failure currently produces HTTP 500.
Persistence initializes on the first database-backed request. The startup
message alone does not establish database readiness.

The server binds to `127.0.0.1`. Set `BLOG_PORT` if 8080 is occupied. Stop the
application with Ctrl+C; on Windows the sbt batch launcher may ask for confirmation.

## Following a database request

1. `RestComponentsExtension` registers the resources and `TransactionBoundary`.
2. The request filter starts a Narayana transaction and opens an EntityManager.
3. A resource receives that EntityManager through CDI.
4. The response filter flushes successful writes.
5. The writer serializes into a buffer while the EntityManager is still open.
6. The transaction commits successful writes, or rolls back reads and failures.
7. The EntityManager closes, then the buffered response is sent.

HEAD and responses without an entity finish in the response filter.
An unfinished request rolls back during CDI destruction. `Persistence` closes
the EntityManagerFactory and then the pool on shutdown.

This boundary is for the current synchronous, small REST responses. It buffers
the body in memory and does not implement asynchronous context propagation or
streaming downloads. A successful database commit cannot guarantee subsequent
network delivery.

Weld's startup message about unavailable transactional services refers to Weld's
own transactional integration. This chapter wires Narayana explicitly through
Agroal, Hibernate, and the request boundary; it does not enable CDI transaction
observers or automatic `@Transactional` interception.
