# Chapter 13: permissions and HATEOAS

An administrator can open **Account → Open editorial**, preview drafts, publish a
nonempty draft and retract a published post. The public list and slug endpoint
still return published posts only, including when the caller is an administrator.

## Run the chapter

Start with chapter 12's database and administrator setup:
[user accounts](user-accounts.md) and [registration/recovery](registration-and-recovery.md).
No new database objects or columns are introduced. Against the chapter 12
database, SchemaMain migrate reports AlreadyApplied with zero statements.

Optional development fixtures are in database/examples/public-posts.sql.
That script contains one public post and one draft; re-running preserves existing
rows. Apply it to the development database using psql or your SQL client.

Build and start from the repository root, with BLOG_DB_URL, BLOG_DB_USER and
BLOG_DB_PASSWORD configured:

```text
sbt --server frontendAssets
sbt --server "application-backend/run"
```

Use BLOG_COOKIE_SECURE=false for local HTTP only. Open /en/account, sign in
with the administrator, and follow Open editorial. Select Our private draft.

1. Preview shows Draft and Publish. Its public slug still returns 404.
2. Publish changes the response to Published and offers Retract and Open public post.
3. The public post is readable; the returned entity version has increased.
4. Retract returns it to Draft, removes the public link and increases the version again.
5. Signed out, the editorial API returns 401. A registered READER receives 403.

An empty draft has no Publish action. Calling its publish URL directly returns
409. Unknown UUIDs return 404 for an authorized administrator. The UI distinguishes
401, 403 and 409 and offers account navigation or reload as appropriate.

## Permission contract

| Layer | Implementation | Responsibility |
| --- | --- | --- |
| Identity | Soteria + CallerAccess | Current principal and container roles. |
| Endpoint | EndpointPolicy + AuthorizationFilter | Explicit public access, role requirements, or denied access. |
| Object and state | PostAccess | Visibility and valid publication transitions on the loaded post. |
| Field | PostReadRule / PostEditRule in BlogPost.Schema | Mapper read/write decisions for this caller and entity. |
| Response | PostLinks + Data/Table/SessionState | Advertised actions for this response. |

A method's policy overrides its class policy. Unannotated endpoints are closed
by this application's policy. Conflicting annotations fail instead of choosing
one silently. Existing public resources explicitly declare PermitAll.

Request order: TransactionBoundary (2000), AuthenticationFilter (2100), then
AuthorizationFilter (2200). AuthorizationFilter also checks CSRF on unsafe
requests to authentication, recovery and editorial resources. When introducing
another write resource, include it in that protection. Authorization does not
replace CSRF.

Our embedded REST deployment explicitly enforces the Jakarta annotations in a
JAX-RS filter. Adding RolesAllowed alone is not the integration.

Editorial commands lock the current database row, check its state, and call
BlogPost.publish/retract. Two simultaneous publishes yield one 200 and one 409.
These are state-transition commands without user-editable fields, not a general
entity save. Chapter 14 introduces PreparedChange and submitted-version checks.

PostEditRule allows an administrator to write title, slug, content and summary
when the mapper is used. PostReadRule leaves status/publishedAt read-only;
publication changes those through the domain methods. ID and version retain
the default read-only rule. There is no generic edit endpoint in this chapter.

The cached EntitySchema stores rule classes, never a caller's permission result.
RuntimeContext resolves each rule through CDI. Schema.forGraph describes selected
field structure; it is not a catalogue of writable fields.

## Links

Each link has rel, url, method and the mapper's @type field. The relation describes
the action. URLs are API destinations; the browser maintains its own page routes.

| Context | Relations |
| --- | --- |
| ADMIN session | editorial |
| Public post | self; preview for ADMIN |
| Nonempty draft in editorial | self, publish |
| Empty draft in editorial | self |
| Published post in editorial | self, retract, public |
| Editorial table | self; previous and next when applicable |

Links live on the response envelopes, not on the JPA entity. The frontend maps
the JSON name $links to a links member and treats omitted collections as empty.
It follows the supplied command URL/method, checks that the destination remains
under /service/ on the same origin, then supplies the session CSRF token.

Link absence removes an action from the UI. Link presence is not authorization:
roles, state and CSRF are checked again on every request. On failure, the UI
discards the stale action links and offers reload; it does not retry a POST
automatically. Successful responses replace the entire detail model, including
version and links.

Responses with caller-dependent links use Cache-Control: no-store. Editorial
HTML shells also use no-store. This chapter has no shared-response cache.

## Verify

Use a dedicated local test database, never production. The backend suite also
requires the local SMTP capture settings documented in the chapter 12 guide;
keep that port free. The browser server owns port 18080.

```text
sbt --server "application-backend/testFull" "application-frontend/testFull" frontendAssets
npx playwright test --project=contracts
```

The checkpoint has 89 backend tests, 9 Scala.js model tests and 28 browser
contracts. PermissionsSpec covers actual anonymous/READER/ADMIN requests,
current database roles, CSRF, field writes, public visibility, pagination,
conflicts, concurrent publication, version updates and writer/commit rollback.

For the real publication workflow, bootstrap a dedicated test administrator and
set BLOG_TEST_ADMIN_EMAIL/PASSWORD. Install the PostgreSQL command-line client:
psql must be on PATH, or set BLOG_PSQL to its executable path. This test uses the
same BLOG_DB_URL/USER/PASSWORD as the server, inserts one uniquely identified
draft and removes only that draft in finally.

```text
npx playwright test --project=editorial
```

The browser contract tests run with controlled responses; the editorial project
uses real Soteria, REST, PostgreSQL and the built Scala.js application. Together
with the earlier database, authentication and recovery projects, there are
33 browser checks. Run them sequentially; backend SMTP tests and browser recovery
tests must not share the capture port at the same time.

The database projection still uses the existing entity graphs. General editing,
ownership/author relationships and full form error handling are later chapters.
