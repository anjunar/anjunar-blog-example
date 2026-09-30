# Blog series roadmap

## Agreed scope

We are building a blog with user accounts, an editorial interface, images,
multiple languages, and server-side rendering. One application, one configuration,
and one database, with no tenant columns or tenant context. The separation
between platform, features, and application grows with the requirements.

A single blog post provides the recurring example: store it, serve it, display it,
edit it, check permissions, and render its public page on the server.

Each new article is written in English and German. Documentation and code
examples remain English. The application starts in English and adds German
with internationalization.

The series ends with chapter 24, completing the public pages. Chapters 21–24
cover translated posts, server-side rendering, hydration and public-page metadata
and discovery. Packaging, deployment, production operations and additional feature
chapters are outside this series. Tests remain part of each implementation chapter.

## Topic sequence

This is a working plan. We refine the scope of individual articles as we
write them together. Chapter 11 was split into authentication and a separate
chapter 12 for registration/recovery. Chapter 17 is also split: relationships come
first, followed by media in chapter 18. Chapter 20 is split as well: UI messages,
locale routing and language switching come first; editorial translations follow
in chapter 21. Later topics move forward accordingly.

| No. | Topic | Content |
| --- | --- | --- |
| 1 | What we are building and how it fits together | The finished application, its libraries, and a request's journey through the browser, HTTP, REST, and database. |
| 2 | From an empty directory to a runnable project | Tools, sbt, modules, published dependencies, and the first startup. |
| 3 | Starting an HTTP server with Jakarta components | Connecting Undertow, RESTEasy, and Weld; an endpoint, CDI, and shutdown. |
| 4 | Understanding database access and transactions | PostgreSQL, Hibernate, Agroal, Narayana, and EntityManager; commit, rollback, and serialization. |
| 5 | Our first domain model | BlogPost, slugs, publication status, IDs, versions, Bean Validation, business rules, and automatic entity discovery through CDI. |
| 6 | Evolving the data model | Hibernate DDL Manager, SchemaId, schema changes, and preserving existing data. |
| 7 | Describing an entity with EntitySchema | Fields, rules, property, reference, set, list, and Criteria attributes. |
| 8 | Serving posts through REST | Lists and details, the JSON mapper, entity graphs, Data, and Table. |
| 9 | Building the first interface with Scala.js | A cohesive DSL tree in compose, properties, lists, styles, and accessible HTML. |
| 10 | Connecting the frontend and backend | Mirrored models, JSON mapping, HTTP services, actions, routing, and loading and error states. |
| 11 | User accounts and sign-in | Jakarta Security with Soteria, Undertow integration, IdentityStore, password hashing, the first administrator, sessions, CSRF, revocation, and the account page. |
| 12 | Registration and account recovery | Public registration, email confirmation, single-use expiring tokens, mail delivery, and password reset. |
| 13 | Permissions and HATEOAS | Endpoint, object, and field permissions, plus allowed actions exposed through $links. |
| 14 | Applying changes safely | PreparedChange, authorization before mutation, required versions, partial updates, validation, error responses, and the boundary for future references. |
| 15 | Building a form from end to end | Model binding, field errors, save state, version conflicts, and delayed responses. |
| 16 | Searching, filtering, and pagination | Criteria through EntitySchema, search models, sorting, pagination, and list projections. |
| 17 | Managing entity relationships | Authors, tags, entity graphs, safe reference loading, relationship permissions, validation, and mirrored form models. |
| 18 | Uploading and serving media | Uploads, ownership, image delivery, post images, and orphaned media cleanup. |
| 19 | Integrating a post editor | Structured content, the document format, code blocks, and embedded images. |
| 20 | Translating the interface | The i18n macro, English/German catalog, URL locales, language switching and protecting unfinished forms. |
| 21 | Translating blog content | Editorial translations, their entity/schema/frontend contract, localized content selection and fallbacks. |
| 22 | Rendering the same interface on the server | The SSR bundle, GraalJS, request data, HTML, HTTP status, and browser API boundaries. |
| 23 | From server-rendered HTML to an interactive page | The browser bundle, render, boot, hydration, initial state, and lifecycle. |
| 24 | Completing the public pages | Metadata, canonical URLs, hreflang, sitemap, feed, redirects, and 404 responses. |

## First milestone

The server starts locally, stores a post in PostgreSQL, and returns it through REST.

The initial project revision lays the groundwork for topics 2 and 3:
a reproducible build, HTTP, REST, and CDI. Chapter 4 adds PostgreSQL access and request transactions. Chapter 5 discovers entity classes through CDI, persists BlogPost, and verifies its validation, uniqueness, and optimistic locking. Chapter 6 adopts the existing schema with stable IDs and adds an optional summary through Hibernate DDL Manager. Chapter 7 defines the complete EntitySchema, queries published posts with typed Criteria attributes, and verifies the mapper contract. Chapter 8 serves public list and detail responses with entity graphs, Data/Table envelopes, and HTTP contract tests. This completes the first milestone. Chapter 9 adds the first Scala.js interface with local post previews, reactive controls, accessible HTML, asset delivery, and browser tests. Chapter 10 connects it to REST with mirrored JSON models, a same-origin HTTP service, actions, list/detail routes, loading and error boundaries, basic page links, and tests using both controlled responses and real PostgreSQL data. Chapter 11 adds the first administrator, password authentication through Jakarta Security/Soteria and Elytron's Undertow integration, server-side sessions, CSRF protection, session revocation, and the account page. Chapter 12 adds email-first reader registration, confirmation, expiring single-use tokens, SMTP delivery, password reset, and revocation of older sessions. The local mailbox and automated SMTP/browser checks make the full workflow reproducible.

Chapter 13 adds the administrator's editorial list and preview, publication and
retraction commands, explicit endpoint policies, object/state checks and CDI
field rules. Per-response links drive available actions, and real HTTP/browser
tests verify conflicts, revoked access and rollback.

Chapter 14 creates and edits posts through PreparedChange. It requires the current
version, preserves partial/null semantics and uses the mapper's field validation.
Hibernate's existing callbacks protect complete entities; safe problem details
carry validation errors back to the client. Concurrent requests and failed
writes are covered through real HTTP and PostgreSQL; the browser runs the exact
article example. Reference loading stays closed until relationships in chapter 17.
Chapter 15 binds this write contract directly to BlogPost in an accessible create/edit
form. It displays local and server errors, follows create/update links, preserves
newer typing across delayed saves, and keeps conflicts until explicit discard/reload.
The real browser workflow creates and edits a post against PostgreSQL.
Chapter 16 searches title, slug and summary with typed Criteria predicates shared
by rows and count. Lists select compact read-only projections; public visibility,
editorial status filters, stable sorting and literal punctuation are tested.
Bound search controls keep filters in the URL across paging, reload and history.
Chapter 17 adds authors, tags and authorized entity references through the mapper,
REST graphs and bound forms. Chapter 18 adds a post cover through bounded JPEG/PNG
uploads, metadata references, authorized image delivery and cleanup of unused
uploads. Its browser workflow covers saving, publication, retraction and unlinking.
Chapter 19 adds a Markdown-valued editor, explicit legacy-text conversion, code
blocks and embedded images. The server derives image relations from parsed
content; preview and public detail share the read-only document component.
Chapter 20 initializes the interface locale from /en or /de and provides a shared
macro-based catalog for application and editor messages. Language switches retain
routes and filters, while page-owned guards protect unfinished input. Post content
remains unchanged by the UI catalog.
Chapter 21 adds a separately versioned and published German translation beside
its English source. Locale-aware detail, search, sort and count select a complete
published language or fall back to English. Draft media remains private and
protected from cleanup; the editor preserves newer input and handles conflicts.

Chapter 22 now renders the shared public route tree with GraalJS. The response
contains localized content and the route status before JavaScript runs. Separate
server/browser entry points preserve the same components; boot still remounts,
with hydration reserved for chapter 23.

Chapter 23 now hydrates the public server tree and replays its initial public
response once. DOM identity, no duplicate initial fetch, early search input,
error routes, fresh navigation and controlled recovery are verified. Chapter 24
is the final remaining installment.

## Writing principles

- Each new article is written in English and German together.
- Each article delivers a concrete, verifiable result.
- Examples come from this runnable project.
- A commit or tag connects each published article to its project revision.
- Select examples that explain the chapter's central ideas. Give excerpts enough context and the necessary imports; link the complete implementation at the checkpoint. Full files belong in the article only when they help the explanation. Code volume is not a quality target.
- We explain architectural decisions when they become relevant.
- Tests accompany each feature, including the remaining chapters 21–24.
