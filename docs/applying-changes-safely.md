# Chapter 14: applying changes safely

The editorial API now creates drafts and applies partial edits through the
published JSON mapper's PreparedChange. The controller receives the original
entity, checks access, applies once, validates the complete result and flushes.
The existing transaction boundary still waits for serialization and commit
before sending a successful body.

## Start and try it

Use chapter 13's database and development administrator. No new database mapping
is introduced. On the existing database, SchemaMain migrate reports AlreadyApplied
at revision 3 with zero SQL statements. Refresh dependency resolution if needed;
Jackson Core 3.1.1 is now declared directly for the HTTP parser, matching the
version already brought in by json-mapper 1.1.5.

```text
sbt --server frontendAssets
sbt --server "application-backend/run"
```

Configure BLOG_DB_URL, BLOG_DB_USER and BLOG_DB_PASSWORD as before. For local
HTTP only, set BLOG_COOKIE_SECURE=false. Sign in at /en/account.

Open [the runnable console example](examples/post-changes.js) and paste its
contents into that page's developer console. It uses the same-origin session,
follows the create/update links and returns a result like:

```json
{
  "id": "the-generated-UUID",
  "createdVersion": 0,
  "savedVersion": 1,
  "conflictStatus": 409,
  "conflict": "The post changed. Reload it before saving again.",
  "preview": "/en/editorial/posts/the-generated-UUID"
}
```

The example deliberately leaves one draft to inspect at the returned preview
address. Run it only against the development application. The automated browser
test executes this exact file and deletes its own created post afterwards.
General editing forms arrive in chapter 15.

## Request contract

| Request | Input | Result |
| --- | --- | --- |
| POST /service/editorial/posts | A plain post object with slug/title and optional content/summary. | 201, Location and Data[BlogPost]. |
| PATCH /service/editorial/posts/{id} | Required current version plus fields to change. | 200 and refreshed Data[BlogPost], or 409 for a stale version. |

Both endpoints require ADMIN, CSRF and application/json. The collection advertises
a create POST link; each editable post advertises an update PATCH link. The
existing publish/retract commands remain separate.

PATCH is this API's partial entity format, not JSON Patch or JSON Merge Patch.
A body such as {"version":0,"title":"A clearer title"} keeps every omitted field.
A supplied null clears optional summary. Null for a required field fails validation.

Writable fields are slug, title, content and summary. The EntitySchema rules
still ignore attempted status/publishedAt writes; publication uses the domain
commands. The server generates IDs and versions. An optional ID in an edit must
match the URL. Creation permits an absent/empty ID and an absent/-1 version.
An optional @type must be BlogPost. Unknown field names are rejected.

Versions are nonnegative whole JSON numbers on updates. Missing, null, quoted,
fractional or overflowing versions produce a 400 field error. A valid but stale
version produces 409. A no-op keeps its version. Actual changes return the
version incremented by Hibernate.

Changing slug changes the public URL. Redirects are a later chapter.

## Request path

1. TransactionBoundary opens the request transaction.
2. Soteria resolves the caller; AuthorizationFilter checks ADMIN and CSRF.
3. RequestJson accepts at most 1 MiB, validates UTF-8, rejects duplicate keys and
   limits nesting to 32 levels. Its streaming parser creates mapper JSON nodes;
   it does not bind an entity.
4. PreparedChangeReader prepares a new BlogPost. For edits, PreparedChangeParamConverter
   loads the URL entity with PESSIMISTIC_WRITE, then compares the required version.
5. PreparedChanges checks metadata and scalar JSON shapes, then calls JsonMapper.prepare.
   Its providers deliberately support only PreparedChange[BlogPost] in this chapter.
6. The controller checks change.getEntity() before calling applyChanges once.
7. Mapper property rules and field validation apply. PostValidation then validates
   the whole resulting entity, including missing required creation fields and
   publication consistency.
8. The slug check queries with flush mode COMMIT, so it does not flush the pending
   edit before checking. The database unique constraint resolves racing inserts.
9. Explicit flush assigns/checks database state. The response includes current
   values, selected schema fields, version and fresh links; commit still follows
   response serialization.

PreparedChange is deferred binding, not an immutable diff or a nested transaction.
Applying can change some fields before another field fails. Let that exception
escape and roll the request back; do not catch it and return success. Do not
retry the same change after a failed application. After success, a second
applyChanges call is rejected by the library.

The row lock is acquired before comparing versions, so two simultaneous edits
from the same saved version cannot both overwrite it. Other Hibernate writers
still participate in the entity's @Version contract. Locks last through the
existing request transaction; this example has no detached write queue.

BlogPost has no entity relationships yet. The EntityLoader passed to the mapper
fails closed if reference loading is attempted. Do not replace it with unrestricted
EntityManager.find: chapter 17 must authorize each target before adding reference
fields to the published contract.

## Errors and the frontend

Problems use application/problem+json with type, title, status, detail and
instance. Validation/conflict responses can include errors: an array of objects
with path and message. Current post paths are scalar field names. Unexpected
errors expose an errorId that correlates with the server log, not stack traces
or SQL. Existing HTTP challenges, allowed-method and rate-limit headers survive.

The database error mapper recognizes both the adopted blog_post_slug_key and
the explicit uq_blog_post_slug name. Other unique constraints receive a generic
409. Do not rename an existing database constraint just to match an error handler.

HttpFailure retains optional typed ProblemDetails for the upcoming forms. An
absent, malformed or inconsistent body does not overwrite the HTTP status.
Existing pages continue to use status-based messages.

The mapper omits empty strings in responses. On an authorized draft detail,
EditorialService restores omitted content to an empty string in the model;
public/published details still require content. This avoids treating a legitimate
empty draft as a broken response.

## Verification

Use the dedicated migrated database and chapter 12's SMTP capture environment.
Keep its capture port free for the backend suite.

```text
sbt --server "application-backend/testFull" "application-frontend/testFull" frontendAssets
npx playwright test --project=contracts
npx playwright test --project=changes
```

The checkpoint has 112 backend tests, 12 Scala.js tests and 29 browser contracts.
The changes project tests the article's exact console example with real Soteria,
REST and PostgreSQL. It needs BLOG_TEST_ADMIN_EMAIL/PASSWORD and psql on PATH or
BLOG_PSQL, like the editorial project. It deletes only its own generated UUID.

All six browser projects contain 35 tests: contracts, changes, editorial,
database, authentication and recovery. Run browser and backend SMTP suites
sequentially, never concurrently on the same capture port.

PostChangesSpec checks creation, partial/null semantics, required versions,
concurrent updates/creates, validation rollback, publication invariants, roles,
CSRF, metadata, unknown fields, errors and writer/commit failures. A test-only
resource proves that preparation leaves the entity untouched until authorization
and that a successfully applied change is single-use.
