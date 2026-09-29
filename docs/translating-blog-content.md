# Chapter 21: translating blog content

A post keeps its English source and shared identity. A German translation has its
own title, summary, Markdown document, version and publication flag. The slug,
author, tags and cover remain on BlogPost. UI language and editorial language are
separate: either interface language can edit the German translation.

## Run this chapter

Stop the HTTP server and use the dedicated local database described in README.md.
Keep all existing SchemaId values and migration history.

```text
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain preview"
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain migrate"
sbt --server frontendAssets "application-backend/run"
```

The upgrade adds blog_post_translation and blog_post_translation_media, a unique
(post_id, locale) constraint and three foreign keys. It does not move or rewrite
existing English fields. On the chapter 20 test database this is five statements;
repeating migrate reports AlreadyApplied with zero statements. Preview may report
INCOMPLETE for the existing publication check's PostgreSQL normalization, as
explained in [schema evolution](schema-evolution.md).

Sign in as the test administrator, open an editorial post and follow **German
translation**. The English reading view sits beside an empty German form.
Save the draft, reload it, then publish it explicitly. Follow the same slug under
/en/posts/ and /de/posts/ to compare the result. Editing a published translation
updates that published language immediately; retract it first to work privately.

## Selection and the entity contract

| Request | Visible text |
| --- | --- |
| English, published post | English source |
| German, published post and translation | Complete German translation |
| German, missing or draft translation | Complete English source, with a fallback notice |
| Either language, draft post | HTTP 404 |
| Editorial translation editor | The German draft itself, or an empty new draft |

An absent German summary stays absent; it never borrows the English summary.
Both root and translation must be published before translated inline images are
public. All saved references protect uploads from cleanup, including drafts.

The public detail endpoint returns the real BlogPost. Its English fields retain
their existing meaning. Response-only translation, contentLocale and
availableLocales describe the selected language; the nested translation excludes
its internal parent and image associations. The frontend renders the nested
entity when present, otherwise the original fields, and marks actual content
with lang. No localized text is assigned into managed English fields.

Both entities have EntitySchema definitions. JSON Mapper 1.1.6 eagerly resolves
nested schemas. Therefore the response-only BlogPost.schema.translation property
is lazy and PostLocalization registers/uses it after the persistent schemas
exist. Persistent Criteria attributes are initialized eagerly; the deferred
property factory needs no captured EntityManager. This avoids a cycle through
the internal translation.post backlink and is covered across separate CDI
containers in the complete backend suite.

The list remains a constructor projection. LocalizedPostFields supplies identical
CASE expressions to HibernateSearch's filter, title sort and projection, with
separate roots for rows and count. One left join selects only the published
translation; uniqueness prevents duplicate list rows. Search covers selected
title/summary and the shared slug. It does not search Markdown bodies. Page links
retain locale, query and sort.

## Write contract

All translation endpoints require ADMIN. Writes also require the existing CSRF
token. Use the returned links; IDs below are placeholders.

| Method and path under /service | Input / result |
| --- | --- |
| GET /editorial/posts/{postId}/translations/de | Existing German entity, or an unsaved draft with create link |
| POST /editorial/posts/{postId}/translations/de | title, optional summary and content; saves an unpublished translation |
| PATCH /editorial/posts/{postId}/translations/{id} | Current version and changed title/summary/content |
| POST /editorial/posts/{postId}/translations/{id}/publish | Current version; publishes a saved document |
| POST /editorial/posts/{postId}/translations/{id}/retract | Current version; returns it to draft |

Publication command bodies contain only version. They never save pending content.
The parent, locale, publication flag and derived image references are not editable
JSON fields. The PreparedChangeParamConverter reads the translation ID and JSON
body, then PreparedChanges loads/locks the entity and checks the required version.
The endpoint compares the entity's parent UUID with postId before applying the
change. This checks URL consistency, not user ownership; ADMIN/CSRF come from the
security filter. The mapper validates supplied values; PostMedia.synchronize
derives authorized image references from the translated Markdown.

Translation endpoints do not flush or commit. TransactionBoundary flushes a
successful write before MapperMessageBodyWriter serializes the Data envelope.
The envelope holds the same managed entity, so JSON contains the new @Version.
Whole-entity Hibernate validation and SQL errors also surface at that boundary.
The writer buffers JSON and commits before sending a success body; serialization
or commit failures follow the existing rollback/error path.

Creation locks the parent before checking uniqueness, so two requests yield one
saved row and one 409. Row locking plus required versions rejects stale updates
and publication commands.

The form snapshots its partial JSON body before the asynchronous session lookup.
Saved values merge only where the user has not typed something newer; identity,
version, baselines and links always advance. A conflict preserves input and stops
retries until explicit discard/reload. Language navigation is disabled for dirty
forms, saves and pending image uploads. Backend diagnostic messages remain English.

Inside the continuous compose tree, use lang = translation.locale.get on German
input/editor nodes and lang = "en" on the English source. The translation locale
is fixed while mounted; changing the UI locale remounts the page. Public content
uses lang = post.contentLocale.get so English fallback remains marked as English
inside a German interface. These are named AttributeDsl setters; lang does not
translate text or change the i18n runtime.

## Verify

Use a migrated, separate test database. Full backend tests also need the local
SMTP capture configuration from [chapter 12](registration-and-recovery.md).
Do not run backend and browser recovery tests concurrently.

```text
sbt --server "application-backend/testFull" "application-frontend/testFull" frontendAssets
npx playwright test
```

The translation browser workflow uses BLOG_TEST_ADMIN_EMAIL,
BLOG_TEST_ADMIN_PASSWORD and psql (or BLOG_PSQL), like the earlier database
workflows. It creates and cleans its own post and translation. To run it alone:

```text
npm run test:browser:translations
```

The chapter implementation has 80 distinct browser checks verified across full
and focused runs, with desktop and mobile layouts inspected. For the endpoint
and lang-DSL corrections, the complete 171 backend and 53 Scala.js tests passed
again, alongside eight translation/i18n browser contracts and the real translation
database workflow. The parent-URL check also accepts an equivalent uppercase UUID
and verifies that the returned version matches a subsequent read.

All three Scala article excerpts compiled against this revision: the resource
with its update method/response builder, the full localization class, and the
form excerpt inside its declared component state and compose/render context.
English and German use identical code blocks.

The backend checks whole-language fallback, draft isolation, null summaries,
localized search/count/order/paging, source preservation, independent versions,
validation rollback, CSRF, roles, cross-parent URLs, concurrent creation and media
visibility/cleanup. Frontend checks cover partial writes, nested mapping, delayed
responses, command guards and disposal. The browser writes, reloads, publishes,
searches, resolves a conflict and retracts a translation in both UI languages.

## Deliberate boundaries

This chapter supports an English source plus one German translation. Translation
is manual. Source edits do not invalidate or mark a German translation stale;
editors review consistency themselves. Slugs, author/tag labels and cover
descriptions stay shared. Localized canonical/alternate links follow in chapter
24; server rendering and hydration follow in chapters 22 and 23.
