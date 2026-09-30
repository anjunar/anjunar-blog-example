# Chapter 22: rendering pages on the server

Public list and article requests now return complete HTML from the same Scala.js
routes, models and components used by the browser. English/German selection,
published translations, fallback, search and paging still come from the public
REST contract. No schema migration is added in this chapter.

## Run and inspect

Use the JDK 25, Node and migrated PostgreSQL setup from README.md. GraalJS and the
Polyglot API (25.3.4.1) resolve from Maven Central; a separate GraalVM installation
or a sibling framework build is not required.

```text
npm ci
sbt --server frontendAssets "application-backend/run"
```

Open /en or /de with JavaScript disabled. Published links and pagination work as
ordinary links. Article text and formatted Markdown must already be present.
Inspect the document response itself, not only the browser's Elements panel:

```text
curl -i "http://127.0.0.1:8080/en?limit=1"
curl -i "http://127.0.0.1:8080/de/posts/our-first-public-post"
curl -I "http://127.0.0.1:8080/en/posts/no-such-public-post"
curl -i "http://127.0.0.1:8080/de?limit=0"
```

The sample slug requires the optional public-posts.sql data. The last two
responses are 404 and 400. /de/unavailable is the explicit 503 error route.
Private account/editorial URLs still serve their client shell and require
JavaScript. Language buttons, search submission and interactive controls also
require JavaScript; direct localized/filter URLs and reading links do not.

Rebuild assets and **restart the backend** after frontend changes: the renderer
retains the loaded module source for its lifetime. Both bundles must come from
the same revision. The SSR file is not in the HTTP asset allowlist.

## Request and component lifecycle

1. FrontendHandler recognizes a public page and dispatches off Undertow's I/O
   thread. It passes the local path and query, without cookies or arbitrary origin.
2. SsrRenderer submits to one worker with at most eight queued renders. The worker
   creates a fresh JavaScript Context with no general Java host access and
   evaluates target/frontend/ssr/main.js. A shared Engine/Source can reuse code;
   module globals and component state remain per request.
3. The exported Main.render mounts BlogDocument into Runtime's SsrCursor and
   waits for renderToStringAsync. That includes asynchronous router loaders.
   BlogPage gets the explicit request URL; browser-only account/history listeners
   stay behind cursor.isBrowser.
4. BlogService calls the existing HttpJson. A small Headers/fetch/timer host
   adapter supports the APIs it needs. The fetch bridge permits only GET on
   /service/blog/posts and its slug detail routes at the fixed loopback origin.
   It sends no cookies or authorization and follows no redirects. REST owns
   transactions, authorization, localization and serialization as before.
5. RouterConfig.renderErrorsOnServer enables the existing error routes.
   BlogPage exposes Router.responseStatus; the handler sends HTML with that
   status, UTF-8 and no-store. HEAD calculates the same status without a body.
6. Runtime disposes the component tree after serialization and the JVM closes
   the guest Context. Shutdown closes the renderer before the HTTP server.

The HTTP bridge is intentionally synchronous on the dedicated renderer thread.
The JavaScript surface still returns promises, so the existing service/router
contract stays asynchronous. This is a small tutorial runtime, not a general
browser emulator or a throughput-optimized SSR pool.

A render has a 15-second caller deadline including queueing. Cancellation
interrupts the worker and closes its Context, including a CPU-bound guest.
Individual API requests have a three-second response-header timeout and a 2-MiB
body limit; body reading remains inside the overall render deadline.
A full queue, missing bundle, rendering exception or deadline produces a safe
503; the API remains available. The small unit fixture verifies recovery after
cancellation. Do not expose the internal exception in HTML.

## Two entry points, one interface

application-frontend has no automatic main initializer and exports render.
application-client depends on it and calls Main.boot from its ClientMain.
frontendAssets writes the browser module to target/frontend/main.js and the
server module to target/frontend/ssr/main.js.

BlogDocument composes html/head/body and mounts BlogPage inside #app.
DocumentHead supplies charset, viewport, styles and the module script. The two
stylesheets have different registry keys so neither replaces the other.
Per-article metadata, canonical/hreflang and discovery documents belong to
chapter 24.

For now boot clears #app and mounts BlogPage with DomCursor, then fetches the
current route again. This is **remounting, not hydration**; there can be a loading
transition and duplicate data request. Chapter 23 will preserve/adopt the server
DOM and reuse initial state. Do not claim that chapter 22 already avoids either.

## Verification

At this checkpoint, 183 backend tests, 53 Scala.js tests, all 80 existing browser
checks and five additional SSR browser checks pass.

Keep the dedicated test database configured as described in README.md. The SSR
browser suite creates random-ID posts/translations and removes only its own rows.
It needs BLOG_PSQL (or psql on PATH), BLOG_DB_URL, BLOG_DB_USER and BLOG_DB_PASSWORD.

```text
sbt --server "application-backend/testFull" "application-frontend/testFull"
npm run test:browser
npm run test:browser:ssr
npm run test:browser:translations
```

The default browser configuration explicitly sets BLOG_SSR_ENABLED=false so its
browser-intercepted contract fixtures still control all data. The separate SSR
configuration sets it true and uses the real API/database, predominantly with
JavaScript disabled. It verifies actual document status, escaped titles,
Markdown, localized list/detail/fallback, paging, HEAD, private shells, asset
boundaries and client remount/navigation. The backend tests cover isolated
contexts, timers, restricted fetch, CPU timeout/recovery and missing/failed bundles.

BLOG_SSR_ENABLED=false is a development/test escape hatch; the application
defaults to SSR enabled. Never confuse a passing shell-only contract test with
proof that public server rendering works.
