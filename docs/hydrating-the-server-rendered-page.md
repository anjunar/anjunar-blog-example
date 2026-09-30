# Chapter 23: hydrating the server-rendered page

Public pages now retain their server DOM when the browser starts. The first
public route reuses the exact JSON response used during SSR. It makes no second
browser request for that response; later navigation uses the normal public API.

This chapter builds on chapter 22. No schema migration, new dependency or
framework release is required. Only chapter 24 (public metadata and discovery)
remains in the series.

## Run and observe

Use the configured chapter 21 database and the chapter 22 JDK/GraalJS setup.

```text
npm ci
sbt --server frontendAssets "application-backend/run"
```

Restart the backend after rebuilding assets so the cached SSR module and browser
bundle match. Open /en or /de. With JavaScript disabled the page remains readable.
With JavaScript enabled, a cold document load should not produce a browser request
to /service/blog/posts. A subsequent article link, language change or search
submission should fetch fresh data.

For an explicit DOM-identity check, pause /main.js in browser developer tools,
retain the article or list node, then let the module finish. The old node must
still be connected and identical to the node found afterward. The automated
browser checks perform this comparison for the page, list, search input,
article, heading and Markdown content.

## State transfer

Each BlogDocument creates its own InitialPageData.capture(url) and passes it to
BlogService. HttpJson separates the received status/content type/body from mapping.
The recorder saves only public post GET responses and uses the existing mapper to
render them. It also records an unavailable response when the transport fails.

The snapshot contains a format version, the document path/query, the exact API
path/query, HTTP status, content type and the original JSON body. Keeping the raw
body preserves omitted fields, nulls, version zero and response-only translation
fields; serializing the writable frontend model would not preserve this contract.

A DocumentHead entry carries URI-encoded JSON in data-state on the inert
application-state script element (type application/json). It has no executable
script body. HeadSink additionally escapes attribute values. Arbitrary article
text, quotes and closing-script strings therefore remain data.

This is public response data, not an authentication snapshot. Do not add session
tokens, cookies, private API responses or a process-global cache. Server render
contexts remain isolated.

## Completed futures are part of the hydration contract

HydratingCursor can claim the existing route tree when Route.load returns an
already-completed Future. If a loader is still pending, this framework version
adopts the visible range temporarily and replaces it after loading. That would
keep content visible but would not preserve the route's DOM identity.

Replay decodes the saved response immediately. The pure maps in InitialPageData,
BlogService and the two public route loaders use ExecutionContext.parasitic so a
completed response stays completed through validation and component creation.
This does not turn fetch into synchronous network I/O in the browser. Live
requests remain asynchronous.

The snapshot is accepted only for the same document path and query (fragments
are browser-local). The exact API key includes search parameters and locale.
An entry is consumed once; finish drops replay state after hydration. A missing
API entry during replay is a mismatch, not permission to silently fetch different
initial data. Error responses replay the same failure; routes such as invalid
search parameters may have an empty snapshot because they never called the API.

## Bootstrap and cleanup

Main.boot is exported and returns the same promise on repeated calls. ClientMain
still calls it once automatically.

- For a public snapshot, mount BlogPage with HydratingCursor.root(#app, async).
  The document head/body wrappers remain outside this component root.
- Wait for AsyncRenderContext.drain, then call completeHydration. Only afterward
  release the initial response through finish.
- Remove the bootstrap-state element when startup settles. Component listeners
  continue to belong to the mounted tree.
- Account/editorial pages and explicitly disabled SSR use the empty shell and
  DomCursor as before.
- A corrupt/wrong-page snapshot or a structural hydration failure cancels the
  attempt, unmounts its partial component tree and performs one fresh mount.
  A console warning identifies the recovery. It may fetch again; it is not
  successful hydration and does not loop or auto-reload.
- Missing state also mounts fresh. Unexpected failures of that fresh attempt
  remain failures rather than starting an endless retry.

PostSearchForm captures native query/status/sort/limit values in beforeHostBinding
when hydrating. After completeHydration, it restores them through the bound model.
That preserves typing while the bundle loads without changing the initial tree
before it has been claimed. No imperative markup or attribute construction is
introduced. A full recovery rebuild can still discard such pre-start input;
private editorial forms are not server-rendered in this chapter.

## Verification

At this checkpoint, 183 backend tests, 59 Scala.js tests and 93 distinct browser
checks passed (80 existing checks plus 13 SSR/hydration checks).

Use the dedicated database and environment documented in README.md. The browser
SSR suite creates and removes its own random-ID posts/translations. It requires
BLOG_PSQL (or psql on PATH), BLOG_DB_URL, BLOG_DB_USER and BLOG_DB_PASSWORD.

```text
sbt --server "application-frontend/testFull" "application-backend/testFull"
npm run test:browser
npm run test:browser:ssr
```

The six new Scala.js tests cover immediate mapping, selected/omitted fields,
single-use API keys, failed responses, invalid state and safe text encoding.
The SSR/browser suite checks no-JavaScript rendering plus real node identity,
no duplicate initial requests, early typing, repeated boot, fresh values after
navigation, error pages and bounded recovery. Existing browser contracts still
disable SSR explicitly and cover the account/editorial workflows.

Chapter 24 adds per-page metadata, canonical and alternate-language URLs,
sitemap, feed and the remaining public-page routing details.
