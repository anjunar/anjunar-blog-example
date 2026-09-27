# Managing entity relationships

Chapter 17 connects posts to an optional author and a set of reusable tags.
It adds a metadata page, safe ID-only writes and bound relationship controls.
Chapter 18 handles media separately.

## Start and migrate

Start with the chapter 16 database and administrator. The build now resolves
JSON Mapper **1.1.6 from Maven Central**. No sibling checkout or local publication
is required. This release fixes reference resolution and collection preparation
needed by this chapter.

With the server stopped and the existing development database configured:

```text
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain preview"
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain migrate"
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain migrate"
sbt --server frontendAssets "application-backend/run"
```

The upgrade adds nullable blog_account.display_name and blog_post.author_id,
blog_tag, blog_post_tag and their foreign keys. It preserves the old SchemaId
values and existing rows. It does not invent authors for historical posts.
The second migration reports AlreadyApplied.

As in chapter 6, the existing named publication CHECK can make a read-only preview
report INCOMPLETE (exit 3). Migration checks its normalized predicate under the
lock. Review the additive SQL; do not delete/recreate the database to bypass it.
The verified upgrade applied seven statements and preserved checksums of every
pre-existing post/account field.

## Try the interface

1. Sign in at /en/account with the development administrator.
2. Open editorial, then **Manage authors and tags**.
3. Set a public name for an author. Create a tag with a unique lowercase slug.
4. Create a post at /en/editorial/new. Its initial author is the current account.
5. Select one or more tags, save, and reload. Choices and version survive.
6. Open preview and publish. The public detail shows the author name and tags.
7. Edit again and clear a selection. The association disappears; the shared tag
   remains in the catalog.

Authors are active, unlocked administrators in this tutorial. Public registration
still creates readers. This chapter adds no role-management endpoint. Existing
posts keep attribution if an author later becomes unavailable for new selection.
An unassigned or unnamed author displays **Editorial team**; an editor sees
**Unnamed author** in the catalog selector until a public name is provided.

Catalogs return 20 entries by default, at most 100 per page. Load-more follows
the returned next links without dropping existing selections.

## Relationship write contract

POST /service/editorial/posts and PATCH /service/editorial/posts/{id} use
PreparedChange[BlogPost]. PATCH still requires the current nonnegative version.

| Input | Effect |
| --- | --- |
| author omitted on create | Assign the current administrator. |
| author omitted on update | Keep the current author. |
| author: null | Clear the author. |
| author: { id: UUID } | Resolve and authorize that existing Account. |
| tags omitted | Leave the existing collection unchanged (empty for a new post). |
| tags: [] | Remove every association. |
| tags: [{ id: UUID }, ...] | Replace associations with those existing tags. |
| tags: null | HTTP 400; clear with an empty array. |

Duplicates, malformed/unknown IDs, ineligible authors, nested edits and ID-less
children are rejected. A tag object containing id AND name is not a rename.
Rename it through the tag endpoint with its own version.

A post owns the join-table rows. It does not own Account or BlogTag lifetimes.
There is no CascadeType.REMOVE or orphan removal and no tag-deletion endpoint.
The relationship has set semantics: tag order is not a persisted contract.
At most 20 tags may be selected; JSON Mapper performs the field validation.
Hibernate callbacks continue to validate complete entities.

Read responses contain public author id/version/displayName and tag
id/version/slug/name. They never expose email, role or credentials through
the author subgraph. The mapper omits null values and empty collections:
on a detail response, absent author/tags mean unassigned/empty. The frontend
defaults cover this; on writes omission still means preserve.

Chapter 16 list projections remain small. They do not acquire relationship
joins or author/tag fields. Always load the full detail before editing.
Public tag filtering is not implemented in this chapter.

## Metadata operations

| Endpoint | Operation |
| --- | --- |
| GET /service/editorial/authors | Paged active author choices. |
| PATCH /service/editorial/authors/{id} | Change only displayName, with account version. |
| GET /service/editorial/tags | Paged reusable tags and create/update links. |
| POST /service/editorial/tags | Create a tag; returns 201. |
| PATCH /service/editorial/tags/{id} | Change slug/name with tag version. |

Every endpoint requires ADMIN. Writes also require the session CSRF token.
Session links expose the two catalogs. Returned row links expose PATCH; the tag
collection exposes POST. Direct requests still enforce authorization.

CatalogSearch uses the existing HibernateSearch engine, typed schema attributes
and CDI predicate/sort providers. Author availability is a query predicate AND
a check during reference resolution. A previously loaded choice is not authority.
Names sort ascending with UUID as tie-breaker. Paging is bounded, not a snapshot.

## Executable API walkthrough

Sign in on the local tutorial site, then paste
[examples/entity-relationships.js](examples/entity-relationships.js) into the
browser console. It creates one tag and one draft, proves that a nested rename
rejects the whole update, verifies omission, clears both relationships and updates
the still-existing tag. Expected result:

```json
{
  "rejectedStatus": 400,
  "createdVersion": 0,
  "clearedVersion": 2,
  "tagVersion": 1
}
```

The result also contains postId, tagId and preview. The example deliberately
leaves its objects available for inspection. The automated browser test runs the
same file unchanged and cleans only its own rows afterward.

## Implementation map

All paths below are relative to application/.

| Concern | Implementation |
| --- | --- |
| Domain, schema, graphs | backend/.../Account.scala, BlogTag.scala, BlogPost.scala |
| Reference policy | backend/.../ReferenceAccess.scala |
| Typed preparation | backend/.../PreparedChanges.scala, PreparedChangeProviders.scala |
| Metadata queries/REST | backend/.../CatalogSearch.scala, EditorialAuthorsResource.scala, EditorialTagsResource.scala |
| Mirrored values and partial writes | frontend/.../Account.scala, BlogTag.scala, BlogPost.scala |
| Catalog requests/paging/save state | frontend/.../CatalogService.scala |
| Author/tag forms | frontend/.../RelationshipPage.scala |
| Collection binding | frontend/.../TagSelection.scala |
| Post editor | frontend/.../PostEditorPage.scala, PostEditorActions.scala |
| Routes/overlays | frontend/.../BlogRoutes.scala, BlogPage.scala; backend/.../FrontendHandler.scala |

The frontend source prefix is src/main/scala/com/anjunar/blog/frontend/;
the backend prefix is src/main/scala/com/anjunar/blog/.

TagSelection is a real collection form control. ComboBox 1.0.9 exposes a singular
Control[T] value even in multi-select mode. The wrapper binds its selection
ListProperty to the parent's tags field and keeps @Size and server field errors
on that collection. It registers/unregisters with FormContext and disposes every
subscription. A Viewport owns the combo-box overlay.

The post form allows edits during saves: snapshot author IDs/tag ID sets, preserve
newer selections, advance defaults/version, and retain returned metadata when
unchanged. The smaller metadata forms disable their controls while saving.
A successful catalog save replaces the row with the response (including version);
a stale/unconfirmed write requires reload.

## Verify

Use an isolated database migrated to this mapping, the prior sample public posts,
a bootstrapped administrator, and the chapter 12 local SMTP capture settings.
Set BLOG_TEST_ADMIN_EMAIL, BLOG_TEST_ADMIN_PASSWORD and psql on PATH or BLOG_PSQL.
Keep port 18080 free. Run backend and browser tests sequentially.

```text
sbt --server "application-backend/testFull" "application-frontend/testFull" frontendAssets
npx playwright test
```

There are 136 backend checks, 33 Scala.js checks and 62 browser checks
(52 controlled contracts and ten real workflows). The full run may wait one minute
before a relationship test signs in: it honors the real account login limit and
Retry-After response instead of disabling that protection. For only the new real workflows:

```text
npm run test:browser:relationships
```

RelationshipSpec covers actual HTTP/transactions, public graphs, default/swap/clear/
omit behavior, shared tags, nested-input rejection, unknown/ineligible references,
collection validation, complete rollback, metadata versions, CSRF and pagination.
MapperReferenceSpec catches the framework regressions directly against the published
dependency. RelationshipModelSpec covers ID-only writes, identity-based merging,
late field errors and a mounted collection form control. Browser contracts
add delayed replies and load-more; real workflows exercise the article example,
tag creation, post publication, reload and unlinking.

Media, tag deletion, reverse navigation, author/tag search filters and bulk editing
remain outside this chapter. Public author names are current account metadata,
not immutable historical byline snapshots.
