# Chapter 16: searching, filtering and pagination

Readers can search published posts by title, slug and summary. The editorial
list adds a publication-status filter. Both lists offer four fixed sort orders
and a page-size selector. Query values live in the browser URL, so links,
reloads and back/forward restore the same search.

## Run and try it

Use the existing development database and administrator. This chapter changes
neither database mappings nor dependency versions.

```text
sbt --server frontendAssets
sbt --server "application-backend/run"
```

1. Open /en, enter a phrase in Search posts, choose a sort and page size,
   and press Search or Enter.
2. Follow Next page. The query, sort and limit stay in the URL. Go back and
   reload; the controls still describe the displayed result.
3. Submit another search while on a later page. It starts at offset zero.
4. Sign in at /en/account and open editorial. Choose Draft to restrict both
   the rows and total to drafts.
5. Use Reset search to restore that list's defaults.

Direct examples:

```text
/en?q=scala&sort=title&limit=10
/en/editorial?q=release&status=DRAFT&sort=title&limit=10
/service/blog/posts?q=scala&sort=oldest&offset=0&limit=10
/service/editorial/posts?q=release&status=DRAFT&sort=title&offset=0&limit=10
```

## Request contract

| Parameter | Meaning |
| --- | --- |
| q | Trimmed literal substring, at most 100 characters without control characters; empty means no text predicate. |
| status | Editorial: omitted, DRAFT or PUBLISHED. Public: omitted or PUBLISHED; DRAFT is rejected. |
| sort | newest, oldest, title or title-desc. Public defaults to newest; editorial defaults to title. |
| offset | Nonnegative Int, default zero. |
| limit | Integer from 1 through 100, default 20. |

The UI normally offers 10, 20, 50 and 100 rows. A valid custom limit supplied
in a URL remains selectable, including the one-row walkthrough used by tests.
Invalid routes fail before fetching data; direct invalid API requests receive
HTTP 400. The backend remains authoritative.

The public endpoint always adds PUBLISHED, even for a signed-in administrator.
Only the ADMIN-protected editorial endpoint can search drafts. Search input
never selects a permission boundary.

## Implementation map

| File | Responsibility |
| --- | --- |
| backend/PostSearchParams | Validate query parameters and choose the public/editorial scope before constructing BlogPostSearch. |
| backend/hibernate/search | Reusable HibernateSearch engine, annotation reader, provider contracts and bounded paging. |
| backend/BlogPostSearch | Annotated immutable search, CDI predicate/sort providers and encoded page URLs. |
| backend/BlogPostSummary | Explicit read-only list projection and selection callback with seven fields and no content. |
| BlogPostsResource / EditorialPostsResource | Choose visibility scope, execute the search and return Data/Table metadata and links. |
| PostLinks | Build filter-preserving pagination links and row links from summary data. |
| frontend/PostSearch | Parse route state, encode URLs and preserve defaults. |
| PostSearchForm | One reusable bound search form with its entire UI in compose. |
| BlogRoutes / services / list pages | Load from the route, keep AbortSignal, render results and page links. |

BlogPost.schema supplies every Criteria attribute through reference. There is
no string-based sort path or second JPA metamodel. The row and count queries
rebuild the same predicates against their own roots; Criteria nodes are not
shared between queries.

Text uses a named parameter with lower-case values. Percent, underscore and
the chosen escape character (!) are escaped before constructing the LIKE
pattern. The user searches for literal text, not a SQL pattern. Page URLs insert
search text as a URI template value, so literal %20 and braces stay data rather
than being treated as existing URL encoding or a new placeholder. Case conversion
uses Locale.ROOT on the JVM; database case and collation rules still apply.

Every sort ends with ascending UUID. Equal titles or timestamps therefore
have a stable relative order. Date sorts place undated drafts after dated
posts in either direction.

## Use the stack's search infrastructure

The backend ports the reusable HibernateSearch architecture from Anjunar Stack
into com.anjunar.blog.hibernate.search. This is the application's Criteria
helper, not the separate Hibernate Search full-text product. The stack remains
a reference, with no local build dependency and no tenant context.

PostSearchParams validates the HTTP values and returns BlogPostSearch, an
AbstractSearch. Its JsonbProperty fields carry RestPredicate and RestSort
annotations. SearchBeanReader reads those annotations through the existing
AnnotationIntrospector and resolves ApplicationScoped providers through CDI.
Predicate providers receive a field value; the sort provider receives the
entire search object. Providers use BlogPost.schema for typed Criteria paths.

Both resources inject HibernateSearch. searchContext captures only the
immutable search; entities and count create separate Criteria roots, call
the same providers, and bind their parameter values. BlogPostSummary.select
chooses the seven result columns independently of the filter providers.
No Criteria nodes or caller-specific state are cached in application-scoped
beans. The injected EntityManager resolves to the existing request context.

The port keeps the stack's provider/context/projection structure. Its
AbstractSearch exposes index (the row offset) and limit; PostSearchParams
retains the chapter's HTTP offset contract and strict 400 errors.
RestSort requires an explicit provider for our four whitelisted orders.
The stack's generic field-path sorting and query-cache hints are not needed
here; this chapter configures no query cache. QuerySurface validates internal
callers' bounds as well.

A missing CDI provider or unreadable annotated property fails the operation.
Silently ignoring it could drop a visibility predicate. HibernateSearchSpec
verifies a separate CDI provider with entity and scalar projections, matching
counts, missing-provider failures and internal paging limits.

## A projection is different from an entity graph

The prior list graph hid content in JSON but did not by itself prove that the
body was excluded from the SQL selection. The new query explicitly constructs
BlogPostSummary from id, version, slug, title, summary, status and publishedAt.
It does not load managed BlogPost instances for a list.

Data and Table keep their existing envelopes. Schema metadata still describes
the selected entity fields; the DTO supplies the intentionally limited read
shape. Its type metadata identifies BlogPostSummary. The frontend maps the
published fields into the existing BlogPost mirror, leaving content absent.
Opening preview or edit loads the real entity detail before writing.

Projection fields are explicitly public metadata, with row visibility enforced
by the endpoint and predicates. A projection does not automatically execute
the entity's property rules. If a future list includes restricted fields,
its authorization must be designed along with that projection.

Summary links expose reading/preview and permitted editing. Publication
commands remain on detail responses because canPublish depends on content.
Do not load the large body just to offer a publication button on a list.

## URL and form behavior

The immutable search describes the loaded results. PostSearchFields holds the
user's pending filter choices. Typing does not silently change the displayed
results; submitting builds a new search at offset zero and navigates to its URL.

Public page links are derived from the current search. Editorial page links
follow the server-provided same-origin URLs. Both preserve filters, sort and
limit. An empty page still shows the filtered total and offers the first
matching page. Reset deliberately removes all search parameters.

The reusable form has a real job on both list pages: binding, submission,
accessible controls and search feedback. Its complete tree remains in compose.
All new UI messages use the i18n macro. Disposed routes still abort loading;
a delayed old result cannot replace a later navigation.

## Boundaries

- This is substring search over title, slug and summary, not body/full-text search.
- Lower-case LIKE with a leading wildcard can scan many rows. Measure before
  adding an appropriate index or a different search strategy for a larger blog.
- Stable ordering is not a snapshot across requests. Concurrent inserts, edits
  or retractions can move rows between offset pages.
- Rows and count are separate statements under the existing transaction
  isolation. A concurrent change can affect them differently.
- Offset pagination is bounded by Int and the page size; very deep offsets
  can still be expensive. Cursor pagination is not part of this chapter.
- Tags, authors and relationship filters follow in chapter 17.

## Verify

Use an isolated, migrated database with the previous sample posts, administrator
and chapter 12's SMTP capture environment. Run backend and browser suites
sequentially; keep port 18080 and the SMTP capture port free.

```text
sbt --server "application-backend/testFull" "application-frontend/testFull" frontendAssets
npx playwright test --project=contracts
npx playwright test --project=search --project=forms --project=changes --project=editorial --project=database --project=authentication --project=recovery
```

Expected totals: 125 backend tests, 26 Scala.js tests and 56 browser tests
(48 controlled contracts and eight real workflows).

The search project needs BLOG_TEST_ADMIN_EMAIL/PASSWORD and psql on PATH, or
BLOG_PSQL. It inserts four UUID-owned posts and deletes only those rows in
finally. It tests real query matching, pagination, the limited list shape,
editorial filtering, history/reload and public visibility while authenticated.

PostSearchRestSpec checks literal punctuation, title/slug/summary matching,
count consistency, visibility, null-date ordering, equal-key ties, encoded
links, invalid input and empty pages. PostSearchSpec checks route parsing,
encoding and offset reset. Browser contracts cover the controls, history,
mobile layout and delayed request disposal.
