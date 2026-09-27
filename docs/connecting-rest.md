# Connecting the frontend and backend

Chapter 10 replaces local previews with the published REST API. The browser maps
the same eight BlogPost fields, opens a complete post, and renders loading,
empty, missing-post, invalid-page, and unavailable states.

## Run with real data

Use JDK 25, sbt, and a separate PostgreSQL database configured as described in
[the README](../README.md). Run SchemaMain migrate before starting the server.
No schema change is introduced in this chapter.

Optionally load database/examples/public-posts.sql. With the tutorial Compose database:

```text
docker compose cp database/examples/public-posts.sql postgres:/tmp/public-posts.sql
docker compose exec -T postgres psql -U blog -d anjunar_blog --set ON_ERROR_STOP=1 --file /tmp/public-posts.sql
sbt --server frontendAssets "application-backend/run"
```

The database password must be in BLOG_DB_PASSWORD in the sbt terminal. The example
script inserts one public post and one draft and preserves them when repeated.
For native PostgreSQL, use the psql command in [the API guide](public-rest.md).

Open http://127.0.0.1:8080/. Select Our first public post to open
/en/posts/our-first-public-post. Reload that address and use Back/Forward.
The draft stays hidden. Without sample rows, a migrated database shows an empty
state; without a working database, the page shows an unavailable state.

## Follow a request

| File in application/frontend/src/main/scala/com/anjunar/blog/frontend | Responsibility |
| --- | --- |
| Main.scala | Compose the service and actions, then mount the page. |
| BlogPost.scala | Mirror entity fields and Data/Table wrappers. |
| HttpJson.scala | Fetch same-origin JSON, check status, map it, and forward cancellation. |
| BlogService.scala | Name the public list and detail operations and their response models. |
| BlogActions.scala | Keep the summary preference and perform an explicit page retry. |
| BlogRoutes.scala | Load each route and choose its loading/error boundary. |
| BlogPage.scala | Provide the English runtime and router inside the persistent site shell. |
| PostListPage.scala | Render published rows, optional summaries, total count, and page links. |
| PostPage.scala | Render the complete post as text. |
| LoadingPage.scala / ErrorPage.scala | Show request progress or a useful recovery action. |

The JVM EntitySchema and browser JsonSchema have different jobs. The former
controls server mapping and Criteria attributes. The latter is derived at compile
time from the local frontend classes. The REST schema metadata does not generate
browser models, forms, or permissions; this reader does not consume it.

The browser keeps UUIDs and ISO timestamps as strings and versions as Long.
Content is Option[String] because the list graph omits it; summary and
publishedAt can also be absent. Omitted rows default to an empty sequence, while
size remains the total. Version zero is a value, not a missing-field marker.
These are read models; do not send a partial list model as an update.

The server orders rows. The UI requests 20 at a time and carries offset in its
URL. This removes chapter 9's local sort control: reversing a single page would
not sort the whole result. Chapter 15 develops searching and sorting further.

## Routing and failure behavior

The English i18n runtime makes router links locale-aware: /en is the list and
/en/posts/:slug is a detail. The root / remains an entry point. API URLs still
start with /service. German catalogs and language selection follow in chapter 18.

Route loaders pass context.signal through BlogService to fetch. The router
aborts the old request when navigation changes and uses a render token to reject
stale completion. Page rendering does not start a second copy of the request.
The shared actions survive route changes, so toggling summaries stays effective
after returning from a post.

HTTP 404 renders Post not found at the requested URL. An invalid offset renders
Invalid page before requesting data. Other HTTP, network, or decoding failures
render Posts are unavailable without displaying server response bodies.
Try again intentionally reloads the current document, preserving the URL and
query. Router 1.0.9 does not expose a reload operation; navigating to an equal
state is not a retry. A document reload also resets the summary preference.

FrontendHandler serves index.html for known English page routes on GET/HEAD.
The /index.html alias redirects to / while preserving its query.
It leaves /service requests with RESTEasy and returns 404 for unrelated paths or
unknown assets; POST to page paths returns 405. Missing assets still do not
prevent the API from starting.

A missing post's document currently returns 200 because Undertow only serves
the static shell. Its subsequent API request returns 404 and the UI shows the
missing-post page. Route.error records the route's status for the router; it
cannot change an already sent document response. SSR in chapter 19 will connect
data loading to the document status. Metadata and canonical URLs follow later.

Content is plain text, including strings that resemble HTML. The structured
editor enters in chapter 17; do not use innerHTML to display today's content.

## Verify

```text
npm ci
npx playwright install chromium
sbt --server "application-frontend/testFull"
npm run test:browser
```

Expect 6 Scala.js mapping tests and 11 browser contract tests. The browser checks
start the real backend on port 18080 but intercept data requests for controlled
loading, failure, empty, pagination, cancellation, and history scenarios. They
also check real static/API routing, mobile layout, keyboard controls, and text
escaping. PostgreSQL is not needed for this project.

With a separate migrated test database containing the unmodified SQL examples:

```text
sbt --server "application-backend/testFull"
npm run test:browser:database
```

Expect 42 backend tests and 2 database browser tests. The second browser project
uses no network interception: it follows the PostgreSQL → REST → JsonMapper →
list/detail path, including reload and draft/unknown 404 behavior. Use a dedicated
test database with the sample public post on its first page. These browser tests
do not insert or remove rows. The backend suite removes its own fixtures.

Both browser projects stop their server after testing. Keep port 18080 free.
Screenshots are written to test-results; traces are retained on failure.
