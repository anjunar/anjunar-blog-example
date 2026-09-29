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

## 10 — Connecting the Frontend and Backend

- Article slug: `abt-10-connecting-the-frontend-and-backend`
- Source revision: [db99261](https://github.com/anjunar/anjunar-blog-example/tree/db992619d48e8938308216641856849e8be9bd97)

This chapter replaces local previews with mirrored BlogPost models, a typed
HTTP service, shared actions, and list/detail route loaders. It adds loading,
empty, invalid-page, missing-post, and unavailable states, basic page links, and
cancellation of superseded requests.

### Check out and open this version

```text
git switch --detach db992619d48e8938308216641856849e8be9bd97
sbt --server "application-frontend/update"
sbt --server frontendAssets "application-backend/run"
```

Prepare a separate local PostgreSQL database using the README and SchemaMain.
Existing chapter 6–9 databases need no schema change. Optionally load
database/examples/public-posts.sql as described in the
[connection guide](connecting-rest.md#run-with-real-data).

Open http://127.0.0.1:8080/. Follow a post to /en/posts/:slug and reload that
address. The English router generates /en URLs; / remains an entry point.
The static document still answers 200 for an unknown post, followed by an API
404 and a missing-post UI. Server-rendered status codes follow in chapter 19.

### Verify

```text
npm ci
npx playwright install chromium
sbt --server "application-frontend/testFull"
npm run test:browser
```

Expect 6 mapping tests and 11 browser contract tests. The browser project starts
the actual backend but intercepts data requests for deterministic edge cases.

With a dedicated migrated test database seeded with the unchanged SQL examples:

```text
sbt --server "application-backend/testFull"
npm run test:browser:database
```

Expect 42 backend tests and 2 real database browser tests. Keep port 18080 free;
Playwright starts and stops its own server. See the
[connection guide](connecting-rest.md#verify) for test data requirements and limits.

## 11 — User Accounts and Sign-In

- Article slug: `abt-11-user-accounts-and-sign-in`
- Source revision: [8c7f7a9](https://github.com/anjunar/anjunar-blog-example/tree/8c7f7a93fa164a3a3f64395e529a358605621f22)

This chapter adds the Account entity, an explicit first-administrator command,
Jakarta Security/Soteria authentication through Elytron and Undertow, password
verification, server-side sessions, CSRF protection, and /en/account.
The public blog remains anonymous. Registration, email confirmation, and
password recovery follow in chapter 12; later roadmap topics move forward by one.

### Check out and prepare this version

```text
git switch --detach 8c7f7a93fa164a3a3f64395e529a358605621f22
sbt --server "application-backend/update" "application-frontend/update"
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain migrate"
```

Configure a separate local PostgreSQL database first. A chapter 10 database
advances to revision 2 and preserves existing posts; a fresh database starts at
revision 1 with both tables. Repeating migrate reports AlreadyApplied.

Follow the [account setup guide](user-accounts.md) to create the first
administrator with BootstrapAdminMain. The command refuses a second bootstrap
and never replaces existing credentials.

For local HTTP, set BLOG_COOKIE_SECURE=false in the terminal that starts sbt;
Secure defaults to true and requires HTTPS outside that development setup.

```text
sbt --server frontendAssets "application-backend/run"
```

Open http://127.0.0.1:8080/en/account. Sign in, reload, and sign out.

### Verify

```text
sbt --server "application-backend/testFull"
sbt --server "application-frontend/testFull"
npm ci
npx playwright install chromium
npm run test:browser
```

Expect 64 backend tests, 9 Scala.js model tests, and 16 browser contract tests.
The backend suite needs its dedicated migrated database. Browser contract
tests intercept data requests and need no database.
The backend also compares Servlet/JAX-RS/Jakarta Security identity and roles,
and checks that failed login/logout responses or commits do not change sessions.

With the sample posts loaded, run npm run test:browser:database for two real
blog workflows. With an administrator bootstrapped in that test database and
BLOG_TEST_ADMIN_EMAIL/PASSWORD set, run npm run test:browser:auth for one real
sign-in/reload/sign-out workflow. The guide documents fixture setup.

The checkpoint uses in-memory sessions and rate limits in one server process.
HTTPS deployment, registration/recovery, and editorial permissions remain
separate roadmap steps.

## 12 — Registration and Account Recovery

- Article slug: `abt-12-registration-and-account-recovery`
- Source revision: [f1599a3](https://github.com/anjunar/anjunar-blog-example/tree/f1599a3904737ba63ce413e2f5db14fd2f38569f)
- Starting revision: chapter 11's Soteria source, `8c7f7a93fa164a3a3f64395e529a358605621f22`.

Readers can request a confirmation email, prove address ownership and choose
their first password. Existing users can request a single-use reset link;
a successful reset revokes older sessions through authenticationVersion.
Sign-in continues through Soteria.

### Check out and migrate

```text
git switch --detach f1599a3904737ba63ce413e2f5db14fd2f38569f
sbt --server "application-backend/update"
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain preview"
```

Configure a dedicated local database and stop its HTTP server before migration.
The preview proposes only blog_account_token. The existing named publication
check causes the familiar INCOMPLETE/exit 3 result from chapter 6.
Review it, then run migrate as a separate command:

```text
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain migrate"
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain migrate"
```

A chapter 11 database advances to revision 3 with one CREATE TABLE. Repeating
migrate reports AlreadyApplied and zero statements. A fresh database starts
at revision 1 with all three tables.

### Start and verify

Follow [the recovery guide](registration-and-recovery.md) for local Mailpit,
SMTP settings, the configured public origin and local HTTP cookie settings.
Start at /en/register; confirmation and reset links arrive in the local inbox.

The guide also gives the separate SMTP capture configuration for automated tests.
Keep its chosen SMTP port and HTTP port 18080 free, and run the suites sequentially:

```text
sbt --server "application-backend/testFull" "application-frontend/testFull" frontendAssets
npx playwright test --project=contracts
npx playwright test --project=recovery
```

Expect 79 backend tests, 9 Scala.js tests, 20 browser contracts and one real
registration/recovery browser workflow. With the earlier sample posts and test
administrator configured, all four browser projects pass 24 tests.

The backend suite covers token storage, expiry, replacement, purpose separation,
parallel consumption, generic responses, limits, CSRF, rollback and session
revocation. The browser workflow uses captured SMTP and real PostgreSQL data.

Mail uses an after-commit worker with a bounded in-memory queue. Delivery is
not durable; a lost message requires requesting a new link. External SMTP/TLS
and the optional Docker Mailpit service were not exercised on the Windows test
host; the tests use native PostgreSQL and a local SMTP capture server.

## 13 — Permissions and HATEOAS

- Article slug: `abt-13-permissions-and-hateoas`
- Source revision: [37f2a3e](https://github.com/anjunar/anjunar-blog-example/tree/37f2a3e6a7d440be4bbc99730bc430915f1bdaa4)
- Starting revision: chapter 12, `f1599a3904737ba63ce413e2f5db14fd2f38569f`.

Administrators can preview drafts, publish and retract posts. Explicit endpoint
policies, entity-state checks and request-scoped mapper rules enforce access.
Response-specific $links advertise the operations the editorial UI can offer.

### Check out and run

```text
git switch --detach 37f2a3e6a7d440be4bbc99730bc430915f1bdaa4
sbt --server frontendAssets
sbt --server "application-backend/run"
```

Use the chapter 12 development database and an explicitly bootstrapped
administrator. Follow [the chapter guide](permissions-and-hateoas.md) for local
cookie settings, sample data and the editorial walkthrough. Chapter 13 changes
no database mapping; migration reports AlreadyApplied with zero statements.

### Verify

Configure the isolated database and local SMTP capture settings from chapter 12.
Keep HTTP port 18080 and the configured capture port free; run backend and
browser suites sequentially.

```text
sbt --server "application-backend/testFull" "application-frontend/testFull" frontendAssets
npx playwright test --project=contracts
npx playwright test --project=editorial
```

Expect 89 backend tests, 9 Scala.js model tests, 28 browser contracts and one
real editorial workflow. The editorial project also needs the dedicated
BLOG_TEST_ADMIN_EMAIL/PASSWORD and psql on PATH, or BLOG_PSQL pointing to it.
It inserts and removes its own UUID draft in the test database.

The earlier database, authentication and recovery browser projects contribute
four more checks, for 33 browser tests overall. Publication tests cover current
roles, CSRF, state conflicts, mapper rules, version updates, concurrency,
serialization/commit rollback and visibility before/after publication.

The actions are narrow state transitions. PreparedChange, general post editing
and form binding remain chapters 14 and 15.

## 14 — Applying Changes Safely

- Article slug: `abt-14-applying-changes-safely`
- Source revision: [bb212a1](https://github.com/anjunar/anjunar-blog-example/tree/bb212a133dc20f9647ead13d5347be42e5829592)
- Starting revision: chapter 13, `37f2a3e6a7d440be4bbc99730bc430915f1bdaa4`.

The editorial API creates drafts and applies partial updates through PreparedChange.
Controllers authorize the original entity before applying it. The JSON mapper
validates submitted values; Hibernate's existing callbacks validate persisted
entities. Required versions and request rollback protect edits; problem details carry
field errors and conflicts to the client.

### Check out and run

```text
git switch --detach bb212a133dc20f9647ead13d5347be42e5829592
sbt --server frontendAssets
sbt --server "application-backend/run"
```

Use chapter 13's development database and administrator. No database mapping
changes; migration reports AlreadyApplied at revision 3 with zero statements.
Follow [the chapter guide](applying-changes-safely.md) for local configuration,
the partial-update contract, error details and concurrency behavior.

Sign in at /en/account, then paste [the console example](examples/post-changes.js)
into that page's developer console. It creates a draft, saves a partial edit
and verifies that the old version receives 409. It leaves the draft available
at the returned preview address. Editing forms follow in chapter 15.

### Verify

Use the isolated migrated database and chapter 12's local SMTP capture settings.
Keep port 18080 and the capture port free; run backend and browser suites
sequentially.

```text
sbt --server "application-backend/testFull" "application-frontend/testFull" frontendAssets
npx playwright test --project=contracts --project=changes --project=editorial --project=database --project=authentication --project=recovery
```

The checkpoint passes 112 backend tests, 12 Scala.js tests and 35 browser tests
(29 controlled contracts and six real workflows). The changes project executes
the exact article example against real Soteria, REST and PostgreSQL. It needs
BLOG_TEST_ADMIN_EMAIL/PASSWORD and psql on PATH or BLOG_PSQL, and deletes its
own generated post.

Coverage includes partial/null semantics, preparation before mutation, single-use
application, required versions, simultaneous edits, racing unique slugs,
full-entity validation, CSRF/roles and serialization/commit rollback.
Problem parsing preserves the real HTTP status even when the body is malformed
or contains null values.

The current providers support BlogPost only. Its contract has no relationships;
reference loading is rejected until chapter 17 adds authorized target loading.


## 15 — Building a Form from End to End

- Article slug: `abt-15-building-a-form-from-end-to-end`
- Source revision: [9e3edb2](https://github.com/anjunar/anjunar-blog-example/tree/9e3edb2af6d498cf299ffbb2c898bf37aeeedf1b)
- Corrected editor: [214eeb6](https://github.com/anjunar/anjunar-blog-example/blob/214eeb6d5a2c6062013d95abf7f990a1cb032cce/application/frontend/src/main/scala/com/anjunar/blog/frontend/PostEditorPage.scala). The run commands apply this DSL correction to the chapter 15 snapshot.
- Starting revision: chapter 14, `bb212a133dc20f9647ead13d5347be42e5829592`.

The editorial form binds directly to the frontend BlogPost and uses the chapter
14 create/update API. Mapper-generated partial payloads carry the current version.
Acknowledged values update the model without erasing newer typing, and typed
server errors return to the affected inputs.

### Check out and run

```text
git switch --detach 9e3edb2af6d498cf299ffbb2c898bf37aeeedf1b
git restore --source 214eeb6d5a2c6062013d95abf7f990a1cb032cce -- application/frontend/src/main/scala/com/anjunar/blog/frontend/PostEditorPage.scala
sbt --server frontendAssets
sbt --server "application-backend/run"
```

Use the existing migrated development database and explicitly bootstrapped
administrator. No database mapping or dependency versions change. Follow
[the chapter guide](building-a-form-from-end-to-end.md) for the complete walkthrough.

Sign in at /en/account and choose New post in editorial. Save a draft, edit its
title and clear its summary. Reload to verify persistence. Open its edit URL in
two tabs: after saving in one, the second keeps its input when its stale version
receives 409. Discard my edits and reload explicitly adopts the current server
state. Publishing remains available in the existing preview.

### Verify

Use the isolated database, sample posts, administrator and local SMTP capture
settings from the earlier chapters. Keep HTTP port 18080 and the SMTP capture
port free, and run backend and browser suites sequentially.

```text
sbt --server "application-frontend/testFull" frontendAssets
sbt --server "application-backend/testOnly com.anjunar.blog.ServerIntegrationSpec com.anjunar.blog.PostChangesSpec"
npx playwright test --project=contracts --project=forms --project=changes --project=editorial --project=database --project=authentication --project=recovery
```

This chapter passes 21 Scala.js tests, 18 affected backend integration tests and
all 49 browser tests: 42 controlled contracts and seven real workflows. The full,
unchanged backend suite still contains 112 tests; the targeted command above
runs the two affected suites. The forms project needs BLOG_TEST_ADMIN_EMAIL,
BLOG_TEST_ADMIN_PASSWORD and psql on PATH or BLOG_PSQL. It deletes only its own
created post UUID.

Coverage includes mapper partial writes, version zero, explicit nulls, local
constraints, server field errors, slow responses, edits during creation, duplicate
submission guards, stale versions and disposal. Desktop and mobile form layouts
were also checked. Navigating away or reloading discards unsaved text; automatic
retries, autosave and offline storage are outside this chapter.


## 16 — Searching, Filtering and Pagination

- Article slug: `abt-16-searching-filtering-and-pagination`
- Source revision: [214eeb6](https://github.com/anjunar/anjunar-blog-example/tree/214eeb6d5a2c6062013d95abf7f990a1cb032cce)
- Starting revision: chapter 15, `9e3edb2af6d498cf299ffbb2c898bf37aeeedf1b`.

Public and editorial lists use the stack's HibernateSearch architecture with
AbstractSearch, annotated fields and CDI predicate/sort providers. Both rows
and count use the same search context and typed EntitySchema attributes.
Editorial adds a status filter.
Whitelisted sorting, UUID tie-breakers and filter-preserving URLs make the
page navigation predictable. SQL constructor projections omit the post body.
Form attributes use AttributeDsl.setAttribute inside their DSL blocks; reactive
attribute observers are attached and disposed within their target control.

### Check out and run

```text
git switch --detach 214eeb6d5a2c6062013d95abf7f990a1cb032cce
sbt --server frontendAssets
sbt --server "application-backend/run"
```

Use the existing migrated development database and administrator. There are no
database mapping or dependency version changes. Follow
[the chapter guide](searching-filtering-and-pagination.md) for the request
contract, implementation map and complete walkthrough.

Open /en, enter a phrase, choose a sort and page size, then submit Search.
Follow Next page, go back and reload: the controls and URL retain the search.
Changing filters starts at offset zero. Sign in at /en/account to use the
editorial status filter. Public searches exclude drafts even for administrators.

### Verify

Use the isolated database with the previous sample posts, bootstrapped test
administrator and chapter 12 SMTP capture settings. Keep port 18080 and the
capture port free; run backend and browser suites sequentially.

```text
sbt --server "application-backend/testFull" "application-frontend/testFull" frontendAssets
npx playwright test --project=contracts
npx playwright test --project=search --project=forms --project=changes --project=editorial --project=database --project=authentication --project=recovery
```

This checkpoint passes 125 backend tests, 26 Scala.js tests and all 56 browser
tests: 48 controlled contracts and eight real workflows. Desktop and mobile
search layouts were checked.

The search project uses BLOG_TEST_ADMIN_EMAIL/PASSWORD and psql on PATH or
BLOG_PSQL. It creates four UUID-owned posts and removes only those rows in
finally. It checks real query matching, page changes, reload, the list
projection, editorial drafts and public visibility after sign-in.

Coverage includes literal LIKE punctuation, URL-encoded Unicode, percent escapes
and braces, equal-key ties, null-date ordering, filtered counts, empty pages,
invalid query values and a delayed search disposed by later navigation.
HibernateSearchSpec also covers an independent CDI provider with entity/scalar
projections, missing-provider failures and generic page bounds.

Substring search can scan rows; offset pagination is not a snapshot under
concurrent changes. These limits and the projection's authorization boundary
are explained in the guide and article.


## 17 — Managing Entity Relationships

- Article slug: `abt-17-managing-entity-relationships`
- Source revision: [a16b0c5](https://github.com/anjunar/anjunar-blog-example/tree/a16b0c53d65b6ef5e9351e42f00637948dd884c8)
- Starting revision: chapter 16, `214eeb6d5a2c6062013d95abf7f990a1cb032cce`.

Posts reference an optional author and reusable tags. Administrators manage public
names and tags through their own versioned endpoints/forms, then select references
in the post editor. JSON Mapper 1.1.6 resolves authorized IDs from Maven Central.
Account graphs publish only the intended public fields; nested writes stay rejected.

### Check out and run

```text
git switch --detach a16b0c53d65b6ef5e9351e42f00637948dd884c8
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain preview"
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain migrate"
sbt --server frontendAssets "application-backend/run"
```

Use the existing chapter 16 database and administrator. The additive migration
adds public names, author references, tags and the join table. It preserves the
old rows and stable mapping IDs. The verified upgrade applied seven statements;
a repeat applied none. Follow [the guide](entity-relationships.md) for preview's
existing CHECK verification behavior and the complete walkthrough.

### Verify

```text
sbt --server "application-backend/testFull" "application-frontend/testFull" frontendAssets
npx playwright test
```

Use the isolated test database, public-post fixtures, bootstrapped administrator,
chapter 12 SMTP settings and psql on PATH or BLOG_PSQL. Keep port 18080 free.
Backend and browser suites run sequentially. The full browser run honors the
account login limit and may wait its Retry-After interval.

The checkpoint passes 136 backend tests, 33 Scala.js tests and 62 browser tests
(52 controlled contracts and ten real workflows). The new relationships project
runs docs/examples/entity-relationships.js unchanged and exercises tag creation,
post selection/publication, reload, public metadata and unlinking through the UI.
It removes only its own join rows, posts and tags.

Coverage also includes unknown and ineligible references, duplicate IDs, nested
edits, the collection limit, full rollback, metadata versions, CSRF, private-field
exclusion, paging and delayed replies that must preserve newer selections.
Desktop and mobile layouts were checked.

No cascading tag deletion, media upload, author/tag filtering or historical byline
snapshot is added. Lists retain chapter 16's compact projection. Chapter 18 handles
uploads, media ownership, image delivery and cleanup.

## Chapter 18: Uploading and serving media

- Slug: `abt-18-uploading-and-serving-media`
- Implementation: [`c31d860`](https://github.com/anjunar/anjunar-blog-example/commit/c31d860175df373be9c4a82af806596a4ac7ba2c)
- [Guide at this checkpoint](https://github.com/anjunar/anjunar-blog-example/blob/c31d860175df373be9c4a82af806596a4ac7ba2c/docs/uploading-and-serving-media.md)

A cover image now travels from a native file input through a bounded JPEG/PNG
decoder into PostgreSQL, then back through authorized binary delivery. The post
stores an ID-only reference and description. Uploads stay private until a
published post references them; retraction closes subsequent anonymous reads.
Unused uploads become eligible for bounded cleanup after 24 hours from creation.

Use the chapter 17 database and administrator. Preview and apply SchemaMain's
additive migration before starting the server, then rebuild frontendAssets.
The tested migration applied four statements; a repeat applied none. Preview
retains the existing publication CHECK's INCOMPLETE verification behavior.

Verification passed: **148 backend tests, 38 Scala.js tests and 67 browser checks**
(56 controlled contracts and 11 real workflows). The browser covers uploading,
saving, reload, publication, retraction and unlinking. HTTP tests verify ownership,
invalid/oversized files, rollback, binary writes after transaction completion and
cleanup racing with an attachment. Mobile and desktop image layouts were checked.

Image delivery uses no-store. This checkpoint handles still JPEG/PNG covers;
resizing, EXIF orientation correction and inline editor images are outside this
chapter. Cleanup has an explicit operator command and small upload-triggered
batches, with no background scheduler.

## Chapter 19: Integrating a post editor

- Slug: `abt-19-integrating-a-post-editor`
- Implementation: [`618af6f`](https://github.com/anjunar/anjunar-blog-example/commit/618af6fb722bdc2d9ccf07c9d612b1a40afa5fad)
- [Guide at this checkpoint](https://github.com/anjunar/anjunar-blog-example/blob/618af6fb722bdc2d9ccf07c9d612b1a40afa5fad/docs/integrating-a-post-editor.md)

The Markdown-valued editor binds directly to the existing post form. New posts
support formatted text, code blocks and uploaded inline images. Historical posts
remain literal plain text until explicitly converted. Editorial preview and
public detail share the same read-only document component.

Start with the chapter 18 database and administrator. Run npm ci, preview and
apply SchemaMain's additive migration, then build frontendAssets. The migration
adds nullable content_format and blog_post_media with two foreign keys: four
statements, with none on a repeat. Historical text remains unchanged. Preview
retains the existing publication CHECK's INCOMPLETE verification behavior.

The build resolves scalajs-ui 1.0.12 and CommonMark 0.30.0 from Maven Central.
It targets ES2021 and serves its toolbar font and license locally. No sibling
checkout or locally published framework artifact is required.

Verification passed: **158 backend tests, 41 Scala.js tests and 71 browser checks**
(59 controlled contracts and 12 real workflows). The full browser run passed 70;
the document workflow passed on a targeted rerun after its assertions were
restricted to the visible reading surface rather than the hidden fallback.
Desktop/mobile editing, the image dialog and the public document were inspected.

The document workflow formats text, enters a code block, uploads an image, edits
its alternative text, saves, reloads, publishes and retracts. Other checks cover
legacy conversion, slow saves, document validation, private/missing references,
rollback and cleanup. Inline references are derived from parsed image nodes;
image-looking code is not an attachment. Both cover and inline references
protect media, and published references determine anonymous delivery.

The supported format is the Markdown produced by this configured editor.
Raw HTML, arbitrary extensions and syntax highlighting are outside this chapter.

## Chapter 20: Translating the interface

- Slug: `abt-20-translating-the-interface`
- Implementation: [`645acea`](https://github.com/anjunar/anjunar-blog-example/commit/645aceabb859696ae05bed7d09b4e079e5a75519)
- [Guide at this checkpoint](https://github.com/anjunar/anjunar-blog-example/blob/645aceabb859696ae05bed7d09b4e079e5a75519/docs/translating-the-interface.md)
- Starting revision: chapter 19 merged at `a1aa8ddd3689eb1709d35ab8b064f3228f7f5e08`.

The URL initializes the English or German interface. Application messages and the
editor's public labels use one macro-based catalog with named placeholders.
The DSL accepts macro messages directly for text, buttons, attributes and select
options; reactive message properties follow both their values and the locale.
Language navigation keeps the route, query and current fragment. Forms own guards
that disable the switch while input is unfinished or operations are pending.
Account confirmation/reset tokens are consumed from the fragment and never
restored by the language action.

Use the chapter 19 database and configuration; no database migration is needed.
The build resolves scalajs-ui 1.0.13 from Maven Central. Build frontendAssets and
open /en or /de. Post content, names, tags and slugs remain unchanged; chapter 21
introduces editorial translations.

Verification passed: **159 backend tests, 46 Scala.js tests and 77 browser checks**
(65 controlled contracts and 12 real workflows). The full backend and browser
suites passed sequentially against the dedicated PostgreSQL database. New coverage
checks direct German entry, localized links, query/fragment retention, history,
reload, unfinished forms, saved post values, editor dialogs, recovery tokens,
error routes and private-page cache headers. Desktop and mobile layouts were
inspected.

Detailed backend problems, built-in validation diagnostics and transactional
emails retain their existing English strings. The outer html.lang updates at
browser boot; the initial static shell is still English. Language-button guards
are not a general guard against reload or all navigation. SSR follows in chapter 22.


## Chapter 21: translating blog content

Implementation checkpoint: [887ac955d3566580229a545d368bc03f4c2559b8](https://github.com/anjunar/anjunar-blog-example/tree/887ac955d3566580229a545d368bc03f4c2559b8).

```text
git switch --detach 887ac955d3566580229a545d368bc03f4c2559b8
```

The English source stays on BlogPost. A German BlogPostTranslation has its own
version, Markdown document and publication state. Missing or unpublished German
text falls back to the complete English article; the editor loads the actual
draft instead. Shared slug, author, tags and cover remain on the parent.

The [chapter guide](https://github.com/anjunar/anjunar-blog-example/blob/887ac955d3566580229a545d368bc03f4c2559b8/docs/translating-blog-content.md)
covers the additive migration, endpoint contract, editor workflow and limits.
Before starting, stop the server and run SchemaMain preview/migrate on the
compile classpath. The upgrade adds two tables and three foreign keys, leaving
existing English data in place. The verified chapter 20 upgrade applies five
statements; repeating migrate reports AlreadyApplied with zero statements.

LocalizedPostFields keeps HibernateSearch filtering, title ordering, projection
and count consistent with the selected language. Inline images require both
publication states for public access; saved draft references protect cleanup.
The bound translation form preserves newer typing and handles version conflicts,
with direct i18n macros and a continuous compose tree.

Verified: **171 backend tests, 53 Scala.js tests and 80 distinct browser checks**.
The full browser run passed 78 checks; the 21 affected/related checks all passed
on rerun after two old request mocks were adapted to the locale parameter.
Desktop and mobile translation layouts were inspected. The real workflow saves,
publishes, searches, conflicts, reloads and retracts a German translation while
preserving the English source.

Both English and German article drafts use this same checkpoint. The series
ends at chapter 24: server rendering (22), hydration (23) and public-page
metadata/discovery (24) are the remaining chapters.
