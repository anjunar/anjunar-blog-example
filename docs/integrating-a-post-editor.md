# Integrating a post editor

Chapter 19 binds scalajs-ui's Markdown-valued Editor to the existing BlogPost
form. New posts use Markdown; old posts keep their literal plain-text content
until an administrator explicitly selects **Enable rich text**.

## Upgrade and run

Use the chapter 18 database and administrator. The build resolves scalajs-ui
core/json/router/forms/editor **1.0.12** and CommonMark **0.30.0** from Maven Central.
The Scala.js linker targets ES2021, including the regular-expression features
required by the editor's Markdown codec. No sibling checkout or locally
published artifact is required. The pinned npm
Material Icons package supplies a local font for the editor toolbar; its OFL
notice is copied alongside it as /material-icons-LICENSE.txt.

```text
npm ci
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain preview"
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain migrate"
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain migrate"
sbt --server frontendAssets "application-backend/run"
```

The additive migration adds nullable blog_post.content_format and the
blog_post_media join table with two foreign keys: four statements on the
chapter 18 database. A repeat reports AlreadyApplied with no statements.
Existing content is not rewritten. NULL means legacy plain text; new entity
instances default to PLAIN_TEXT. The new-post form explicitly submits MARKDOWN.
As in earlier chapters, preview can report INCOMPLETE (exit 3) for the existing
publication CHECK; migrate verifies its normalized predicate under the lock.

Open /en/editorial/new. Write text, select it and choose **Bold**. Use
**Edit Markdown** to enter a heading or fenced code block, then **Visual editor**
to see the result. Upload a JPEG/PNG with the toolbar's **Upload image** action,
select the image and use **Edit image** to supply alternative text. Save and
reload, open the preview, publish and view the public URL signed out. Retract
the post and verify that subsequent signed-out image reads return 404.

Existing posts initially show the chapter 15 textarea. Conversion escapes
Markdown punctuation and preserves line breaks before switching the format.
There is no automatic conversion of historical posts on migration or read.

## One content contract

BlogPost.content remains a bounded String. contentFormat accepts PLAIN_TEXT
or MARKDOWN and travels through EntitySchema, the detail graph, frontend
Property, partial-write serialization and save merging. Public lists remain
compact projections and do not load documents or image collections.

Editor.editor("content") is inside form(post), so it uses the same binding,
validation and field-error path as the previous text control. The UI tree stays
in PostEditorPage.compose. PostMarkdown adapts the existing MediaService to
the editor's MediaUploader and restricts image URLs to /service/media/{UUID}.
Save and source-mode switching wait for pending editor uploads. Leaving the
component disposes its editor session and uploads.

The existing save actions freeze the submitted document, keep typing responsive
and retain newer text when a late response arrives. Format changes participate
in the same comparison. There is no separate editor DTO or manual content copy
in the HTTP service.

PostContent serves both editorial preview and public detail using the editor's
read-only mode and the same image policy. It falls back to literal text for old
posts. Read-only mode exposes no toolbar or writable document surface.

## Documents and media references

The server parses Markdown using CommonMark. It rejects raw HTML, unsafe link
schemes, external image destinations and images without meaningful alternative
text. Source length is limited to 100,000 characters, the parsed tree to 10,000
nodes and depth 32, and images to 20 distinct UUIDs. Empty structural markup
cannot make a post publishable. Plain-text content keeps its old semantics.

PostMedia derives inlineMedia from actual image nodes after applyChanges().
Fenced/indented code, inline code and escaped image syntax do not create media
references. The client cannot set inlineMedia. Resolution locks media rows in
UUID order and checks the same ownership/reference policy as cover images.
Missing or unavailable images reject the entire write with a content field error.

The relation is stored in blog_post_media without cascading media deletion.
MediaLifecycle considers both cover and inline references for public delivery
and cleanup, including draft references. Joins use the schema's exposed
collectionAttribute, the actual JPA attribute required by Hibernate's join
implementation. No independent metamodel or duplicated attribute name is needed.

The mapper still validates submitted field constraints; Hibernate callbacks
validate the complete document and publication state. PostMedia's parse also
provides the exact reference set and a useful document error before persistence.
All document text, format and references commit or roll back together.

The browser renderer and CommonMark are separate parsers. This checkpoint
supports the ordinary text/headings/lists/links/code/images produced by the
configured editor. It does not promise arbitrary Markdown extensions, raw HTML
or a lossless round trip for every external Markdown dialect. Code fences retain
their text; syntax highlighting is not added here.

## Verification

Configure the dedicated migrated PostgreSQL/admin/SMTP environment from the
earlier chapters. Never point integration tests at production.

```text
sbt --server "application-backend/testFull"
sbt --server "application-frontend/testFull" frontendAssets
npx playwright install chromium
npx playwright test
```

The backend suite covers parsed references, private and missing images, rollback,
publication/retraction, cleanup and image-looking code samples. The frontend
suite covers format transport and late-save preservation. Browser tests exercise
explicit legacy conversion, document errors, delayed saves, code rendering,
real image upload, publication and retraction, plus the previous chapters.

For just the real editor workflow, use npm run test:browser:documents with the
test administrator and psql on PATH or BLOG_PSQL. It cleans only its own posts,
join rows and uploads. The contract tests also capture a narrow editor layout;
the real workflow captures the editor, image dialog and public document.
