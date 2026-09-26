# Article checkpoints

Each implementation article refers to an immutable source revision. Use the
checkpoint for the article you are reading so that later changes on main do not
alter its examples.

## 02 — From an Empty Directory to a Runnable Project

- Article slug: `abt-02-from-an-empty-directory-to-a-runnable-project`
- Source revision: [0b9ea6f](https://github.com/anjunar/anjunar-blog-example/tree/0b9ea6f9447069bbe291e486fcdc8fb633d10656)
- Source changes: [PR #1](https://github.com/anjunar/anjunar-blog-example/pull/1)

This chapter recreates the initial backend from an empty directory: the sbt
build, Undertow, RESTEasy, Weld/CDI, the greeting endpoint, and its HTTP
integration test.

### Check out this version

Run the following in a directory where `anjunar-blog-example` does not yet exist:

```text
git clone https://github.com/anjunar/anjunar-blog-example.git
cd anjunar-blog-example
git switch --detach 0b9ea6f9447069bbe291e486fcdc8fb633d10656
sbt --server "application-backend/testFull"
```

Use JDK 25 and sbt; the project selects sbt 2.0.9 and Scala 3.9.0. The test should
report one successful test. It exercises the real HTTP endpoint, CDI injection,
and the 404 response for an unknown resource. `testFull` runs the tests even when
sbt 2's incremental `test` task would skip previously successful tests.

### Start and check the server

```text
sbt --server "application-backend/run"
```

In another terminal:

```text
curl -i http://127.0.0.1:8080/service/hello
```

Use `curl.exe` in Windows PowerShell if necessary. Expect HTTP 200, a
`text/plain` content type, and `Welcome to Anjunar Blog Tutorial!`.

Set the `BLOG_PORT` environment variable if 8080 is occupied, and use the
selected port in the request URL. Stop the server with Ctrl+C.

The checkout is detached to keep the article reproducible. To continue your own
implementation from this point, create a branch with `git switch -c my-blog`.

## 03 — Starting an HTTP Server with Jakarta Components

- Article slug: `abt-03-starting-an-http-server-with-jakarta-components`
- Source revision: [3a30646](https://github.com/anjunar/anjunar-blog-example/tree/3a30646b4992dda24f30f1ebe1bdd560d6f3b8a6)

This chapter replaces the manual REST resource list with a portable CDI
extension. It adds `GET /service/health/live` and verifies resource and provider
discovery, request and application scopes, and destruction callbacks.

### Check out this version

Run this in a directory where `anjunar-blog-example` does not yet exist:

```text
git clone https://github.com/anjunar/anjunar-blog-example.git
cd anjunar-blog-example
git switch --detach 3a30646b4992dda24f30f1ebe1bdd560d6f3b8a6
sbt --server "application-backend/testFull"
```

Use the same JDK 25 and sbt setup as chapter 2. Expect two successful tests.
The lifecycle fixtures and their `beans.xml` live under `src/test` and are
excluded from a normal application run.

### Start and check the server

```text
sbt --server "application-backend/run"
```

In another terminal:

```text
curl -i http://127.0.0.1:8080/service/hello
curl -i http://127.0.0.1:8080/service/health/live
curl -i http://127.0.0.1:8080/service/missing
curl -i http://127.0.0.1:8080/service/_test/scopes
```

Expect HTTP 200 with the greeting, HTTP 200 with `UP`, then two HTTP 404
responses. The last request confirms that the test resource is absent.
Use `curl.exe` in Windows PowerShell if necessary, and adjust the URLs if
you set `BLOG_PORT`. Stop the application with Ctrl+C.

## 04 — Understanding Database Access and Transactions

- Article slug: `abt-04-understanding-database-access-and-transactions`
- Source revision: [e96365e](https://github.com/anjunar/anjunar-blog-example/tree/e96365e906bb14b212fe2b0b9e11664f6b5afd93)

This chapter adds PostgreSQL, Hibernate, Agroal, and Narayana. A request keeps
its persistence context through serialization, then commits successful writes
or rolls back reads and failures before sending the buffered response.

### Check out and configure this version

In a directory where `anjunar-blog-example` does not yet exist:

```text
git clone https://github.com/anjunar/anjunar-blog-example.git
cd anjunar-blog-example
git switch --detach e96365e906bb14b212fe2b0b9e11664f6b5afd93
```

Use JDK 25 and follow the [README database setup](../README.md#start-a-development-database).
Set `BLOG_DB_PASSWORD` in the terminal that runs sbt. The default URL is
`jdbc:postgresql://127.0.0.1:5433/anjunar_blog` and the default user is `blog`.
For an existing PostgreSQL installation, set `BLOG_DB_URL` and `BLOG_DB_USER`
to a separate tutorial database. There is no password default.

With the database running:

```text
sbt --server "application-backend/testFull"
sbt --server "application-backend/run"
```

Expect **12 successful tests**. Intentional SQL, serialization, and commit
failures produce server error logs. The suite creates and removes a uniquely
named probe table and checks committed state using separate JDBC connections.

In a second terminal:

```text
curl -i http://127.0.0.1:8080/service/health/live
curl -i http://127.0.0.1:8080/service/health/ready
```

Both return HTTP 200 and `UP` when PostgreSQL is available. Liveness remains
200 without the database; readiness currently returns 500 for database failure.
Use `curl.exe` in Windows PowerShell if needed.

Stop the application with Ctrl+C. If using Compose, `docker compose down`
stops the database while retaining its data volume.
