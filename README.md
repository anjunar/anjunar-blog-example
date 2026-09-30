# Anjunar Blog Tutorial

The companion repository, `anjunar-blog-example`, grows alongside the Anjunar Blog
Tutorial series into a complete web application built with Scala, Scala.js, and
PostgreSQL. Anjunar Stack is the technical reference. The tutorial builds one
application without multitenancy.

New articles are written in English and German together. Documentation and code
examples remain in English.

## What we are building

- Visitors can read and search posts and filter them by tags.
- Users can register and sign in.
- Administrators can write, edit, and publish posts.
- Posts can contain formatted text, code examples, and images.
- The interface and posts support English and German, with English as the primary language.
- The server delivers HTML; the browser takes over interactivity through hydration.
- Public pages provide metadata, canonical and alternate-language URLs, a sitemap, and a feed.

Follow the [roadmap](docs/roadmap.md) and the immutable
[article checkpoints](docs/article-checkpoints.md).
The series ends with chapter 24's completed public pages. Packaging, deployment
and production operations are outside its scope; tests accompany each feature.

## Current state: public pages rendered on the server

Chapter 22 returns complete HTML for public lists and articles, including German
translations, Markdown and the correct HTTP status. The browser currently
remounts that page; hydration follows in chapter 23. See
[Rendering pages on the server](docs/rendering-pages-on-the-server.md) for the
request lifecycle, two bundles, limitations and tests. No new migration is needed.

## Independently published content translations

Chapter 21 adds a German translation editor beside the English source, separate
versions and publication, localized search and whole-article English fallback.
The existing source data stays unchanged. Run the additive migration before
starting this revision; see [Translating blog content](docs/translating-blog-content.md).

## English and German interface

The URL now selects the UI language: /en or /de. A shared i18n catalog translates
navigation, forms and editor controls. Language switches preserve the current
route and search filters; unfinished forms disable the switch to protect input.
The UI catalog remains independent of editorial translations. Follow
[Translating the interface](docs/translating-the-interface.md) for chapter 20,
its catalog, navigation behavior and verification. No database migration is needed.

New posts use a Markdown-valued editor bound to the existing form. Administrators
can format text, write code blocks and embed uploaded images. Legacy posts stay
plain text until explicitly converted. Preview and public pages share the same
read-only document component. Follow
[Integrating a post editor](docs/integrating-a-post-editor.md) for chapter 19,
its migration, editor workflow and document/media validation.

Administrators can upload a JPEG/PNG cover, describe it, save its reference and
publish it with a post. Image delivery checks current visibility; bounded cleanup
collects abandoned uploads after 24 hours. Follow
[Uploading and serving media](docs/uploading-and-serving-media.md) for chapter 18,
its additive migration, upload limits and operator command.

Posts now reference an optional author and shared tags. Administrators manage
public names and tags, select them in the post form and safely remove links.
The mapper resolves ID-only references; public author graphs exclude account
credentials and email. Follow [Managing entity relationships](docs/entity-relationships.md)
for chapter 17, its additive migration and the executable API walkthrough.

Both post lists now search title, slug and summary, offer fixed sorting and
preserve their filters in the URL. Editorial adds a status filter; public
searches always exclude drafts. The stack's HibernateSearch architecture resolves annotated CDI providers and
executes typed Criteria queries that return compact list
projections and the matching total. Follow
[Searching, filtering and pagination](docs/searching-filtering-and-pagination.md)
for chapter 16 and its runnable examples.

Administrators can now create and edit posts through a form bound directly to
BlogPost. It displays field errors, sends the current version and preserves text
entered while a save is pending. Version conflicts keep the local edits until
the user explicitly discards and reloads them. Follow
[Building a form from end to end](docs/building-a-form-from-end-to-end.md) for
chapter 15 and its two-tab conflict walkthrough.

The API uses PreparedChange; the JSON mapper validates submitted fields and
Hibernate's existing callbacks protect the complete entity. See
[Applying changes safely](docs/applying-changes-safely.md) for chapter 14's
write contract and browser-console example.

The editorial workspace already lets administrators preview, publish and retract posts.
Endpoint and entity-state checks enforce access; mapper rules govern fields, and
response-specific $links drive the available UI actions. See
[Permissions and HATEOAS](docs/permissions-and-hateoas.md) for chapter 13.

The journal reads published posts from PostgreSQL and has an account page
at /en/account. An operator creates the first administrator with an explicit
command. Sign-in uses Jakarta Security/Soteria, an IdentityStore, and Elytron's
Undertow integration. The container supplies the authenticated principal and roles;
server-side sessions retain CSRF protection, rotation, revocation, and logout.

Follow [User accounts and sign-in](docs/user-accounts.md) to migrate the
database, bootstrap the administrator, configure local HTTP cookies, and test
the account page. Chapter 12 adds email-first registration and single-use password
reset links. Follow [Registration and account recovery](docs/registration-and-recovery.md)
for migration, local Mailpit, SMTP configuration and the complete browser workflow.

The [REST connection guide](docs/connecting-rest.md) explains the public
list/detail flow. The [chapter 9 guide](docs/first-interface.md) describes its
historical local-data checkpoint.

Undertow, RESTEasy, and Weld serve resources discovered through CDI.
Hibernate uses an Agroal connection pool with Narayana/JTA and PostgreSQL.
A JSON request owns its EntityManager and transaction through response serialization.
Fully materialized image responses finish the transaction before writing bytes.

`BlogPost` now maps to PostgreSQL with a generated UUID, optimistic-lock version,
unique slug, title, content, optional summary, publication status, and publication time.
Bean Validation checks fields and publication consistency before inserts and updates.
Visitors can now list published posts and read a complete post by slug through REST.

`EntityExtension` discovers `@Entity` classes through CDI and supplies an injectable
`EntityRegistry` to the Hibernate bootstrap. Entity-containing archives use
`bean-discovery-mode="all"`. The extension excludes entity classes from CDI bean
registration; Hibernate manages their instances. No entity list is maintained in
`Persistence`.

`BlogPost.Schema` describes all fourteen persistent fields with typed JPA attributes.
The mapper uses the same schema to apply field rules; default rules allow reading
and ignore incoming writes. Chapter 13 adds request-scoped read/edit rules to
BlogPost's editorial fields; status changes remain domain commands. `findPublishedBySlug` uses the schema directly in a
Criteria query and excludes drafts. Named entity graphs describe the list/detail response fields; chapter 16
selects list columns explicitly through BlogPostSummary. Detail still returns
the real BlogPost entity.

The schema is initialized lazily through the active CDI request's EntityManager.
Do not access it during bootstrap or cache caller-specific permissions in it.
`InstantConverter` preserves publication timestamps as ISO-8601 strings. Mapper
tests verify all populated fields, omitted nulls, version zero, and denied writes.

The public API returns summary projections and entity details in Data/Table
envelopes through a custom JSON writer. Lists exclude content; details include it. Draft and unknown slugs return
404. Pagination is bounded, and each response describes its selected fields.
See the [public API guide](docs/public-rest.md) for the JSON contract, optional
example posts, and curl commands.

### Prerequisites

- JDK 25. The build includes GraalJS/Polyglot; no separate GraalVM installation is required.
- sbt; the project selects sbt 2.0.9 and Scala 3.9.0.
- PostgreSQL 18 for API data and backend tests, either local or through Docker Compose.
- Node.js and npm for browser tests (verified with Node 26.4.0).
- Access to Maven Central for the initial build.

JVM and UI library versions are pinned in build.sbt, the Scala.js plugin in
project/plugins.sbt, and browser-test dependencies in package-lock.json.
No other Anjunar repository needs a local build.

The build resolves application dependencies exclusively from Maven Central.
When updating an existing checkout to this chapter, refresh resolution once:

```text
sbt --server "application-backend/update" "application-frontend/update"
```

### Open the blog

From the repository root:

```text
sbt --server frontendAssets "application-backend/run"
```

First prepare the database and schema using the steps below. Then open
http://127.0.0.1:8080/. An empty database shows an empty list; load the optional
SQL examples from [the API guide](docs/public-rest.md) to read the first post.
Article links open /en/posts/:slug or /de/posts/:slug. The language buttons switch
between /en and /de while retaining the current route, query and fragment.
For sign-in over this local HTTP origin, set BLOG_COOKIE_SECURE=false before
starting sbt. See [the account setup](docs/user-accounts.md#open-the-account-page-locally)
for PowerShell/Bash commands and administrator bootstrap.

After editing Scala, HTML, or CSS, run `sbt --server frontendAssets` in
another terminal, then restart the backend and reload so its cached SSR module
matches the browser bundle. Stop the application with Ctrl+C.
The database setup below is needed for the blog data and backend tests.

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

### Prepare the schema

For a new, empty tutorial database:

```text
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain preview"
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain migrate"
```

Hibernate DDL Manager creates the current tables, constraints, and history.
A repeated migration checks the database and reports AlreadyApplied.

For an existing chapter 5 database, follow the
[adoption and upgrade instructions](docs/schema-evolution.md#upgrade-a-chapter-5-database).
They use a separate baseline checkpoint before adding summary. Do not run the
final mapping's adoption command against an older table or recreate that table.

The role must own the managed tables and be able to create the history schema.
SchemaMain uses CDI entity discovery and a non-JTA JDBC connection; normal
requests retain their Agroal/Narayana transaction boundary. Hibernate still
uses validate. Run migrations successfully before starting the HTTP server.

Read-only previews of existing named CHECK constraints report INCOMPLETE and
exit with code 3. Execution verifies their normalized predicates under the
migration lock; see the guide for the expected output and limits.

### Run the tests

With a separate test database running and migrated to the current schema:

```text
sbt --server "application-backend/testFull"
```

Expect **171 successful backend tests**. The recovery suite also needs the local SMTP capture environment from
[the recovery guide](docs/registration-and-recovery.md#verify); it starts its own capture server.
The account tests verify password
hashing, CSRF, session rotation, logout, revocation, private responses, and limits.
They also verify the same caller through Servlet/JAX-RS/Jakarta Security and
failed login/logout serialization or commit without a persistent session change. The BlogPost tests cover field and publication
validation, optional summaries and their length limit, persisted values, unique
slugs, version increments, and stale edits. PreparedChange HTTP tests also cover
partial/null updates, concurrent saves, required versions, safe field errors,
and rollback after validation, serialization or commit failures.
They remove only the rows they created. The transaction suite creates its own uniquely
named probe table and drops it afterward. It verifies committed and rolled-back
rows through separate JDBC connections. It also checks serialization failures,
deferred constraint failures, rollback-only transactions, GET/HEAD, and
responses without a body. Intentional failure cases produce server error logs.

The persistence suite starts CDI and also checks discovery of a second test-only
entity, its Hibernate mapping, and the exclusion of entities from CDI bean resolution.
Schema tests compare the field model with Hibernate, query a published slug using
typed attributes, and exercise real JSON serialization and protected writes.
Eight public REST tests also verify list/detail projections, total counts, stable
pagination, draft exclusion, invalid input, unsupported writes, and HEAD.
The original HTTP and CDI lifecycle tests still run. Use `testFull` because
sbt 2's incremental `test` can skip previously successful tests.

### Check the browser interface

```text
npm ci
npx playwright install chromium
npm run test:browser
```

Expect **67 successful browser contract tests**. They build the frontend, start
the real HTTP server on port 18080, and stop it after testing. Keep that port free.
These tests intercept data requests; PostgreSQL is not required.

Run the 53 Scala.js model, form-state, search, problem-details, document, i18n and translation tests with
`sbt --server "application-frontend/testFull"`. With a dedicated migrated test
database containing database/examples/public-posts.sql, run
`npm run test:browser:database` for two additional PostgreSQL-to-browser tests.
Run `npm run test:browser:auth` for one additional real administrator workflow.
See [the account guide](docs/user-accounts.md#verify) for credentials and setup.
Run `npm run test:browser:recovery` with the local SMTP test environment for one
complete registration, confirmation, reset and session-revocation workflow.
Run `npm run test:browser:editorial` for publication/retraction and
`npm run test:browser:changes` for the exact article example: create, edit, then
reject a stale edit. Both require the test administrator and psql on PATH or
BLOG_PSQL; each removes its own post. Run `npm run test:browser:forms` with the
same settings to create/edit a post and resolve a real version conflict through
the form. `npm run test:browser:search` covers real filtering and pagination
using the same dedicated test database/admin/psql setup. Run
`npm run test:browser:relationships` for the chapter 17 console example and
real tag/post form workflow. Run `npm run test:browser:media` for the cover upload/publication workflow.
Run `npm run test:browser:documents` for the formatted post and inline-image workflow.
Run `npm run test:browser:i18n` for the six locale/navigation checks without PostgreSQL.
Run `npm run test:browser:translations` for German drafts, publication, search,
conflicts and English fallback against the dedicated database.
All twelve browser projects contain 80 tests.

### Start the application

```text
sbt --server frontendAssets "application-backend/run"
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
Persistence initializes and validates the mapped table on the first database-backed
request. A missing table makes readiness fail. The startup
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

Responses without an entity finish in the response filter. RESTEasy also invokes
the writer for implicit HEAD responses before suppressing the body, so those
requests keep the EntityManager open through serialization.
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
