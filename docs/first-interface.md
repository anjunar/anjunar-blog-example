# First Scala.js interface

Chapter 9 renders an English journal page with three local example posts.
It uses Scala.js UI 1.0.9 from Maven Central and the Scala.js 1.22.0 sbt plugin.
The frontend and JVM backend are separate modules in the same build.

## Build and open

From the repository root:

```text
sbt --server frontendAssets "application-backend/run"
```

Open http://127.0.0.1:8080/. Set BLOG_PORT to use another port.
This preview and liveness work without PostgreSQL. The database-backed API still
requires the database setup and migration described in the README.

The asset task runs fastLinkJS and copies its output plus frontend resources to
target/frontend. The existing Undertow server serves those files; there is no
second development server or JavaScript bundler. Node is needed only for browser
tests in this chapter.

After changing Scala, HTML, or CSS, run `sbt --server frontendAssets` in another
terminal and reload. The server uses no-cache for these development assets.
Stop the application with Ctrl+C.

A missing asset directory returns 503 for the page while API routes remain
available. Build the assets and run from the repository root. Unknown paths
return 404; only /service and /service/... are passed to RESTEasy.
There is no catch-all page fallback yet.

## Follow the code

| File | Responsibility |
| --- | --- |
| application/frontend/src/main/scala/com/anjunar/blog/frontend/Main.scala | Mount BlogPage into the HTML host with a DomCursor. |
| application/frontend/src/main/scala/com/anjunar/blog/frontend/BlogPage.scala | Keep the whole UI tree in compose; bind controls, conditions, and the post list. |
| application/frontend/src/main/scala/com/anjunar/blog/frontend/PostPreview.scala | Read-only local previews and their fixed example data. |
| application/frontend/src/main/resources/index.html | English HTML shell, app host, stylesheet, and module script. |
| application/frontend/src/main/resources/style.css | Typography, responsive layout, button states, and visible focus. |
| application/backend/src/main/scala/com/anjunar/blog/FrontendHandler.scala | Route static page requests separately from the existing API. |

Show summaries binds a boolean Property to aria-pressed and to when blocks.
The order button updates another Property. A lifecycle-owned observer sorts
the example data and calls ListProperty.setAll; the DSL foreach updates the
rendered rows. Native buttons provide Enter/Space activation, and the skip link
moves keyboard focus to the main landmark.

UI messages use the i18n macro with an English runtime and source-text fallback.
The post titles and summaries are editorial data, not translation keys.
German catalogs and locale selection enter in chapter 18.

PostPreview is a small, read-only display projection. It is not the API's
deserialization model. Chapter 10 introduces the matching frontend JSON model,
HTTP service, actions, routing, and loading/error states. This chapter has no
HTTP data requests, article navigation, server rendering, or hydration.
JavaScript mounts into an empty app element.

## Verify

Install the pinned browser-test dependency:

```text
npm ci
npx playwright install chromium
npm run test:browser
```

The four browser tests build fresh assets, start the real backend on
127.0.0.1:18080, and stop it after the run. Keep that port free; tests never reuse
an existing server. PostgreSQL is not required for these checks.

They verify semantic content, keyboard controls, list updates, mobile overflow,
skip-link focus, JavaScript errors, static MIME types, API liveness, and 404/405
routing. Screenshots are written to test-results; traces are retained on failure.
This covers the tested flows, not a complete accessibility audit.

With a separate migrated PostgreSQL database configured, also run:

```text
sbt --server "application-backend/testFull"
```

Expect 42 successful backend tests. The additional regression test confirms
that a missing frontend build does not prevent the API from starting.
