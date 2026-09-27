# Chapter 6: schema evolution

Stop the HTTP server before migrating. Set BLOG_DB_URL, BLOG_DB_USER and
BLOG_DB_PASSWORD as described in the README. Commands below use the compile
classpath: test-only entities are excluded.

## A new, empty database

With the final chapter 6 code:

```text
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain preview"
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain migrate"
```

The migration creates the current table, constraints and history. Do not apply
the old SQL scripts on this path. A repeated migrate reports AlreadyApplied.

## Upgrade a chapter 5 database

Adoption must happen before the summary field is added. Use the immutable baseline:

```text
git switch --detach b693dba5c8e3402a1249bdebf38bc9503d45e99a
```

The existing database must have the chapter 5 table from database/001-blog-post.sql.
Do not rerun that script or recreate a populated table.

Rename the enum check once; its new name corresponds to the status column's
stable ID and DRAFT/PUBLISHED values. This preserves the predicate and rows.

With Compose:

```text
docker compose cp database/002-adopt-check-name.sql postgres:/tmp/002-adopt-check-name.sql
docker compose exec -T postgres psql -U blog -d anjunar_blog --set ON_ERROR_STOP=1 --single-transaction --file /tmp/002-adopt-check-name.sql
```

With native PostgreSQL (adjust the port and database):

```text
psql -h 127.0.0.1 -p 5433 -U blog -d anjunar_blog --set ON_ERROR_STOP=1 --single-transaction --file database/002-adopt-check-name.sql
```

Then inspect and adopt:

```text
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain preview --adopt-existing"
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain migrate --adopt-existing"
```

The preview reports INCOMPLETE (exit code 3) because it cannot normalize the
named publication check without creating a probe table. Inspect its findings:
there should be no blocker. Migration verifies the predicate under its lock
and reports Adopted: revision 1, 0 SQL statements. The zero excludes history writes.

Now switch to the completed chapter 6 source and run:

```text
git switch --detach 185a0fd7634f1da3e7f7b420a806a022033cd242
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain preview"
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain migrate"
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain migrate"
sbt --server "application-backend/testFull"
```

The preview shows ADD COLUMN summary varchar(300), with the same INCOMPLETE
predicate finding. Migration reports Applied: revision 2, 1 SQL statements.
The repeated migration reports AlreadyApplied: revision 2, 0 SQL statements.

Expect 28 successful tests. Existing posts have NULL summaries and retain their
other values. The database continues to reject PUBLISHED without published_at.

## What the command owns

SchemaMain uses the EntityRegistry discovered by CDI and a PGSimpleDataSource.
Hibernate builds metadata but no SessionFactory. The manager owns its JDBC
transaction, separate from request-time JTA. Normal application startup still
uses Hibernate validate; always run migrate successfully before starting it.

Preview exit codes are 0 for READY, 2 for BLOCKED and 3 for INCOMPLETE. A nonzero
preview exit is intentional and is reported by sbt. Migration exceptions also
fail the command. A preview never replaces execution's checks.

Do not regenerate SchemaId values or edit __hibernate_ddl.schema_history.
Named check changes and column renames/drops in a checked table require a manual
migration; the small tutorial CLI does not expose that advanced workflow.
