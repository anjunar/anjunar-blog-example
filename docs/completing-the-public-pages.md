# Chapter 24: completing the public pages

This is the final chapter of the 24-part tutorial. It completes document metadata,
public URLs and discovery. There are no new dependencies or framework releases.

## Upgrade and run

Stop the backend, configure the existing database environment, then run these
commands separately:

```text
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain preview"
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain migrate"
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain migrate"
sbt --server frontendAssets "application-backend/run"
```

The chapter 23 upgrade adds two nullable timestamp columns: blog_post.updated_at
and blog_post_translation.updated_at. The verified migration applied two SQL
statements; repeating it reported AlreadyApplied with zero statements. Existing
rows remain null, and @PrePersist/@PreUpdate records future row changes.

The read-only preview may exit 3 / INCOMPLETE because the existing named
publication CHECK needs PostgreSQL predicate normalization on a probe table.
Read the reported steps and checks: INCOMPLETE is not READY. The migration performs
that verification under its locks and must complete successfully before startup.
Do not bypass a drift error or use --adopt-existing for this upgrade.

Set BLOG_PUBLIC_ORIGIN to the installation's absolute HTTPS origin, without
credentials, path, query or fragment. Local development defaults to
http://127.0.0.1:<BLOG_PORT>; HTTP is accepted only on loopback hosts.
This is the same setting already used by account mail. PublicSite validates and
normalizes it. The backend passes it to the SSR export and injects it into the
browser shell, so canonical URLs stay consistent after navigation. Forwarded
host/protocol headers do not choose these addresses.

## Page metadata

PageHead uses DocumentHead and the library's BrowserHeadSink. SSR uses the registry
already provided by BlogDocument. The browser hydrates only #app, so PageHead
synchronizes the head registry at registration and disposal outside that cursor.
It does not build another DOM template or fragment the UI's compose tree.

The default is the journal title and noindex, follow. Loaded public routes register
their title, description, Open Graph values, canonical, language links and feed
link. Handles belong to components; disposal removes stale article entries when
the next route mounts. Static shell titles have the same data-ui-head key as SSR.

The article metadata uses the API's contentLocale, availableLocales and selected
translation. A missing German summary uses the German title, not the English
summary. Cover images use the existing public media path and access policy.

| Page | Policy |
| --- | --- |
| English source | English canonical; German alternate only if published. |
| Published German article | German canonical with reciprocal en/de links. |
| English fallback under /de | English canonical; no German discovery entry. |
| Default list | Localized root canonical and en/de/x-default links. |
| Ordinary pagination | Self-canonical with offset; no root-page canonical. |
| Query, custom sort/page size, empty later page | Normalized self-canonical and noindex, follow. |
| Error or private shell | noindex; no article canonical or social metadata. |

Unknown query parameters and explicit default list values do not change the
canonical. Hreflang is implemented once in the HTML head; the sitemap contains
canonical URLs without another language-link implementation. English is x-default.

## Public discovery

FrontendHandler maps four fixed public paths into normal servlet/CDI/JAX-RS
processing: /sitemap.xml, /robots.txt, /en/feed.xml and /de/feed.xml.
The corresponding resources are also reachable under /service/discovery.
They are @PermitAll, run in the normal read transaction and select only published
content. Authenticated administrators do not receive drafts through these views.

PublishedPages selects a compact Criteria projection using names/attributes from
the existing entity schemas. The source must be PUBLISHED. The optional German
join requires a published translation; a draft parent hides all its translations.
The published JSON Mapper version types collection properties by their collection
value, so this join uses joinSet with schema.translations.name, as the existing
localized list query does. Scalar paths use the schema's typed attributes.

- Sitemap: /en and /de, then each published canonical article variant. It supports
  up to 5,000 source posts (at most 10,002 URLs). An extra row detects overflow
  and returns 503 rather than publishing a silently truncated map.
- English feed: latest 20 published source posts.
- German feed: latest 20 published translations, ordered by the parent publication
  timestamp. No English fallback entries.
- Both feeds break publication-time ties by source UUID. Their entry IDs use the
  source or translation UUID, retaining identity across slug changes.
- Robots advertises the sitemap. Noindex is not access control and robots.txt does
  not replace authentication or endpoint rules.

updated_at records changes to each row, not a complete history of independently
managed images, tags or author profiles. The translated page takes the latest known
source/translation change. Unknown sitemap lastmod is omitted. Legacy Atom updated
falls back to publication time until a recorded update exists. An empty feed uses
the epoch as a deterministic timestamp. Feeds are ordered by publication time,
not by last edit; they are a latest-publication view, not an edit audit stream.

DiscoveryXml writes XML through the JDK writer, escapes text/attributes and replaces
XML-disallowed control characters. Atom summaries have type=text and do not contain
executable HTML. Responses carry their XML media type, UTF-8, nosniff and no-store.
They are materialized byte arrays, so TransactionBoundary finishes before transfer.
Retraction affects the next request; a feed reader may keep previously downloaded
entries. No sitemap index, feed archive, ETags or scheduled publishing is introduced.

## HTTP routing

GET and HEAD / and /index.html redirect directly to /en with 308.
Unlocalized /posts/<slug> redirects to /en/posts/<slug>. Supported public trailing
slashes are normalized; /feed.xml redirects to /en/feed.xml. Queries survive the
redirect. Destinations are fixed local paths; no request parameter chooses a host.
The application does not store slug history, so an old slug after an editorial
rename remains 404 rather than gaining an invented redirect.

Unknown localized routes render the existing error component with HTTP 404.
Missing/draft articles remain 404; invalid searches stay 400; unavailable renderers
remain 503. HEAD has the same status/header behavior with no body. Account/editorial
shells and error responses carry X-Robots-Tag: noindex. Other unknown paths and
unrecognized assets are 404, never a successful SPA shell.

## Verification

The dedicated test database must be migrated. Use the SMTP/test administrator
environment from README.md for the existing full backend and browser suites.
Do not run the SMTP/browser workflows concurrently with the backend suite.

```text
sbt --server "application-frontend/testFull" "application-backend/testFull" frontendAssets
npx playwright test --config=playwright.ssr.config.mjs
npx playwright test
```

Local validation: 189 backend tests, 62 Scala.js tests and 99 distinct browser checks
(80 existing workflows plus 19 SSR/hydration/public-page checks).

Checks cover XML parsing/escaping, stable IDs, update persistence/rollback, canonical
and fallback selection, reciprocal language links, noindex, redirects and HEAD.
The browser verifies metadata before JavaScript, after hydration and after route
changes. Real-data checks include an unpublished translation, a published
translation beneath a draft parent, and retraction. Chapter 23's DOM-identity,
early-input and no-duplicate-request checks remain green.

## Series boundary

All 24 chapters are implemented. Packaging, deployment, production operations and
additional feature chapters remain outside this agreed series.

Format references:
[Canonical URLs](https://developers.google.com/search/docs/crawling-indexing/consolidate-duplicate-urls),
[language variants](https://developers.google.com/search/docs/specialty/international/localized-versions),
[sitemap protocol](https://www.sitemaps.org/protocol.html),
[Atom](https://www.rfc-editor.org/rfc/rfc4287).
