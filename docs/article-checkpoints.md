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

## 05 — Our First Domain Model

- Article slug: `abt-05-our-first-domain-model`
- Source revision: [7259db3](https://github.com/anjunar/anjunar-blog-example/tree/7259db379231b095c503069f6a5950f385302e01)

This chapter introduces BlogPost, its UUID and optimistic-lock version, unique
slug, publication state, Bean Validation, and the first PostgreSQL table. A CDI extension discovers entity classes and supplies them to Hibernate through an injectable registry.

### Check out this version

In a directory where `anjunar-blog-example` does not yet exist:

```text
git clone https://github.com/anjunar/anjunar-blog-example.git
cd anjunar-blog-example
git switch --detach 7259db379231b095c503069f6a5950f385302e01
```

Use JDK 25 and the [README database setup](../README.md#start-a-development-database).
Set `BLOG_DB_PASSWORD` in the terminal that runs sbt. Set `BLOG_DB_URL` and
`BLOG_DB_USER` when using a different local database or role.

### Create the first table once

With the Compose database running:

```text
docker compose cp database/001-blog-post.sql postgres:/tmp/001-blog-post.sql
docker compose exec -T postgres psql -U blog -d anjunar_blog --set ON_ERROR_STOP=1 --single-transaction --file /tmp/001-blog-post.sql
```

For native PostgreSQL, use `psql --file database/001-blog-post.sql` with your
connection details, `--set ON_ERROR_STOP=1`, and `--single-transaction`.
The README contains the full command.

The script creates `public.blog_post`. Apply it only once to a database without
that table. Hibernate validates its mapping and does not modify the schema.

### Run and verify

```text
sbt --server "application-backend/testFull"
sbt --server "application-backend/run"
```

Expect **26 successful tests**. The model tests cover generated values,
publication transitions, Bean Validation on inserts and updates, slug
uniqueness, and stale versions. They remove only their own rows. CDI discovery
is exercised with a second test-only entity mapped to the same table; no
manual registration or extra table is needed. The existing HTTP, CDI lifecycle,
and transaction tests still run.

In another terminal:

```text
curl -i http://127.0.0.1:8080/service/health/ready
```

Expect HTTP 200 and `UP`; an absent table causes readiness to fail with HTTP 500.
Use `curl.exe` in Windows PowerShell if needed. Stop the application with Ctrl+C.
Public post endpoints follow in chapter 8.

## 06 — Evolving the Data Model

- Article slug: `abt-06-evolving-the-data-model`
- Adoption baseline: [b693dba](https://github.com/anjunar/anjunar-blog-example/tree/b693dba5c8e3402a1249bdebf38bc9503d45e99a)
- Completed source: [185a0fd](https://github.com/anjunar/anjunar-blog-example/tree/185a0fd7634f1da3e7f7b420a806a022033cd242)

This chapter uses Hibernate DDL Manager 1.1.0 from Maven Central. SchemaMain
discovers entities through CDI, previews or applies migrations through a JDBC
transaction, and keeps normal application startup in Hibernate validate mode.

For an existing chapter 5 database, first use the adoption baseline. Rename the
enum check once with database/002-adopt-check-name.sql, inspect the preview and
run migrate --adopt-existing. Only then switch to the completed source and run
migrate to add the nullable summary column. Existing rows and publication checks
remain in place.

For an empty database, use the completed source directly and run migrate without
the old SQL scripts or the adoption flag.

See the [complete command sequence](schema-evolution.md). Expect 28 successful
tests after migration. Read-only previews of the existing publication check
report INCOMPLETE (exit 3); execution verifies the predicate under its lock.

## 07 — Describing an Entity with EntitySchema

- Article slug: `abt-07-describing-an-entity-with-entity-schema`
- Source revision: [ee01b68](https://github.com/anjunar/anjunar-blog-example/tree/ee01b68ac30a1a2f93f1d8153b7f08114637bef2)

BlogPost.Schema describes every persistent field with SingularProperty. The
same schema drives mapper rules and typed Criteria attributes. A published-slug
query excludes drafts; mapper tests verify all populated fields, precise Instant
values, omitted nulls, version zero, and the default prohibition on incoming writes.

### Check out and run this version

```text
git switch --detach ee01b68ac30a1a2f93f1d8153b7f08114637bef2
sbt --server "application-backend/update"
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain migrate"
sbt --server "application-backend/testFull"
```

Use JDK 25 and set BLOG_DB_URL, BLOG_DB_USER and BLOG_DB_PASSWORD for a separate
local PostgreSQL database as described in the README. When starting with an older
chapter 5 database, follow chapter 6's adoption sequence before this checkout.

A chapter 6 database reports AlreadyApplied with zero SQL statements. An empty
database is initialized by migrate. Expect 33 successful tests. The build
explicitly resolves application dependencies from Maven Central; update refreshes
resolution for existing checkouts that may previously have used local Ivy artifacts.

The persistence tests activate CDI's request context before beginning a transaction.
BlogPost.schema must first be evaluated with an active request EntityManager and
initialized Hibernate metamodel. Normal startup and SchemaMain do not evaluate it.

## 08 — Serving Posts through REST

- Article slug: `abt-08-serving-posts-through-rest`
- Source revision: [54fac60](https://github.com/anjunar/anjunar-blog-example/tree/54fac6042b34be4c8d7ab9b3f4ed054272220b81)

This chapter completes the first milestone with public list and detail endpoints.
Named entity graphs select the fields; Data/Table envelopes carry entities and
structural field metadata through the JSON mapper. Queries exclude drafts.

### Check out and run this version

```text
git switch --detach 54fac6042b34be4c8d7ab9b3f4ed054272220b81
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain migrate"
sbt --server "application-backend/testFull"
sbt --server "application-backend/run"
```

Use JDK 25 and a separate local PostgreSQL database with BLOG_DB_URL,
BLOG_DB_USER and BLOG_DB_PASSWORD configured as in the README.
A chapter 6/7 database reports AlreadyApplied with zero SQL statements.
An empty database is initialized by migrate. Follow chapter 6's adoption sequence
first when starting from chapter 5.

Expect 41 successful tests. The [public API guide](public-rest.md) supplies optional
example posts, curl commands, and the exact list/detail and empty-page contracts.
The example script can be applied twice without replacing the existing examples.

BlogPost now implements EntityProvider and uses Scala Long for its version,
initialized to -1 and assigned 0 on insertion. Its PostgreSQL column is unchanged.
The transaction boundary also keeps implicit HEAD responses open through the
writer, because RESTEasy invokes serialization before suppressing the body.

## 09 — Building the First Interface with Scala.js

- Article slug: `abt-09-building-the-first-interface-with-scala-js`
- Source revision: [b7c8f76](https://github.com/anjunar/anjunar-blog-example/tree/b7c8f7674c759a27432925353a7b2090038b8c5b)

This chapter adds a separate Scala.js module and an English journal page with
local post previews. Property, when, and the DSL foreach drive its summary toggle
and ordering. The whole component tree stays in compose, with i18n UI messages,
semantic HTML, keyboard controls, and responsive styles.

### Check out and open this version

From the repository root:

```text
git switch --detach b7c8f7674c759a27432925353a7b2090038b8c5b
sbt --server frontendAssets "application-backend/run"
```

Open http://127.0.0.1:8080/. This preview does not need PostgreSQL.
The existing Undertow server serves the linked JavaScript, HTML, and CSS.
Stop with Ctrl+C; rebuild frontendAssets and reload after editing the frontend.

Scala.js UI 1.0.9 is resolved from Maven Central. The sbt plugin is 1.22.0,
with the existing JDK 25, Scala 3.9.0, and sbt 2.0.9 setup.

### Verify

```text
npm ci
npx playwright install chromium
npm run test:browser
```

Expect four browser tests. Playwright builds the assets and starts the real
backend on port 18080, which must be free. Node/npm is needed for these tests.

With a separate PostgreSQL database configured and migrated, run
`sbt --server "application-backend/testFull"` for 42 backend tests.
An existing chapter 6/7/8 database needs no schema change.

The [first interface guide](first-interface.md) explains the source files,
asset workflow, test coverage, and current local-data scope. API loading and
post routing follow in chapter 10.
