# Blog series roadmap

## Agreed scope

We are building a blog with user accounts, an editorial interface, images,
multiple languages, and server-side rendering. One application, one configuration,
and one database, with no tenant columns or tenant context. The separation
between platform, features, and application grows with the requirements.

A single blog post provides the recurring example: store it, serve it, display it,
edit it, check permissions, and render its public page on the server.

The series, documentation, and code examples are written in English. The
application starts in English; German provides a second language when we
introduce internationalization.

## Topic sequence

This is a working plan. We will refine the scope of individual articles as we
write them together.

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
| 11 | User accounts and sign-in | Password hashing, sessions, cookies, the first administrator account, registration, and account recovery. |
| 12 | Permissions and HATEOAS | Endpoint, object, and field permissions, plus allowed actions exposed through $links. |
| 13 | Applying changes safely | PreparedChange, authorization before mutation, references, validation, and error responses. |
| 14 | Building a form from end to end | Model binding, field errors, save state, version conflicts, and delayed responses. |
| 15 | Searching, filtering, and pagination | Criteria through EntitySchema, search models, sorting, pagination, and list projections. |
| 16 | Managing relationships and media | Authors, tags, uploads, ownership, delivery, and orphaned media cleanup. |
| 17 | Integrating a post editor | Structured content, the document format, code blocks, and embedded images. |
| 18 | Translating the interface and content | The i18n macro for UI messages; separate models for editorial translations and fallbacks. |
| 19 | Rendering the same interface on the server | The SSR bundle, GraalJS, request data, HTML, HTTP status, and browser API boundaries. |
| 20 | From server-rendered HTML to an interactive page | The browser bundle, render, boot, hydration, initial state, and lifecycle. |
| 21 | Completing the public pages | Metadata, canonical URLs, hreflang, sitemap, feed, redirects, and 404 responses. |
| 22 | Testing the system as a whole | Business logic, persistence, the REST contract, permissions, and complete browser workflows. |
| 23 | Building a deployable package | JARs, two JavaScript bundles, CSS, fonts, fingerprints, configuration, and a startup script. |
| 24 | Deploying the application to a domain | HTTPS, a reverse proxy, systemd, resource-limited builds, migrations, backups, and rollback. |
| 25 | Understanding production operations | Health checks, logs, metrics, queries, SSR, caching, and invalidation. |

## First milestone

The server starts locally, stores a post in PostgreSQL, and returns it through REST.

The initial project revision lays the groundwork for topics 2 and 3:
a reproducible build, HTTP, REST, and CDI. Chapter 4 adds PostgreSQL access and request transactions. Chapter 5 discovers entity classes through CDI, persists BlogPost, and verifies its validation, uniqueness, and optimistic locking. Public REST reads follow in chapter 8.

## Further topics

- Passkeys
- Comments and moderation
- Markdown import and export
- External integrations and background jobs

## Writing principles

- Each article delivers a concrete, verifiable result.
- Examples come from this runnable project.
- A commit or tag connects each published article to its project revision.
- Excerpts identify the file, where the code belongs, how to run it, and the expected result.
- We explain architectural decisions when they become relevant.
- Tests accompany each feature; article 22 brings the testing strategy together.
