# Uploading and serving media

Chapter 18 adds one cover image to a post. Uploading creates a private Media entity;
saving the post attaches its ID. JPEG/PNG bytes live in PostgreSQL alongside their
metadata. This chapter uses the existing entity mapper, permissions and form
workflow; chapter 19 will introduce structured content and embedded images.

## Upgrade and run

Stop the development server and configure the chapter 17 database:

```text
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain preview"
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain migrate"
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain migrate"
sbt --server frontendAssets "application-backend/run"
```

The additive migration creates blog_media, nullable blog_post.cover_image_id and
cover_alt, and the cover foreign key. Existing posts need no image or description.
Stable SchemaIds identify all new mappings. CDI discovers Media automatically.
The tested upgrade applied four statements; its repeat reported AlreadyApplied.
As before, the existing publication CHECK may make preview report INCOMPLETE
(exit 3); migration verifies its normalized predicate while holding the lock.
Inspect the additive plan rather than recreating the database.

Sign in at /en/account, open a new or existing post, choose a JPEG/PNG and enter
**Image description**. Wait for the preview, save and reload. Publish from the
editorial preview and open the public post in a separate signed-out browser.
Retract it: subsequent signed-out image requests now return 404.
**Remove cover image** clears the form selection; save to remove the persisted link.

## HTTP contract

| Endpoint | Input and result |
| --- | --- |
| POST /service/editorial/media | ADMIN plus X-CSRF-Token; raw JPEG/PNG body with its Content-Type. Optional X-File-Name is a percent-encoded display name. Returns 201, Location and Data[Media]. |
| GET /service/media/{id} | Authorized pixels with Content-Type, Content-Length, inline disposition, nosniff and no-store. Hidden/missing images return 404. HEAD returns matching headers without bytes. |
| PATCH /service/editorial/posts/{id} | Existing PreparedChange contract: required version, optional coverImage ID reference and coverAlt. |

An attachment change has this shape:

```json
{
  "version": 0,
  "coverImage": { "id": "896d0270-b13b-4735-bac6-3606a0407cc0" },
  "coverAlt": "A blue notebook beside a laptop"
}
```

Use an actual ID returned by the upload. Omission preserves the existing cover;
null clears it. Nested name/data edits, unknown IDs and unavailable private images
are rejected. coverAlt allows at most 300 characters and must be nonblank when
an image is attached. JSON Mapper validates supplied fields; Hibernate's existing
entity callbacks enforce that cross-field invariant before commit.

Post and media detail graphs expose id/version/name/contentType/width/height/byteSize.
They exclude ownership and binary storage. Public list projections are unchanged.
There is no cascading media deletion.

## Image and access boundaries

ImageContent reads at most 5 MiB plus one byte, inspects the actual image format,
checks dimensions before decoding, then writes pixels into a fresh JPEG or PNG.
The declared MIME must match. Maximum dimensions are 8000 pixels per side and
12 million pixels in total; normalized output is capped at 8 MiB. Only two
decoders run concurrently; saturation returns 429 with Retry-After.

Undertow's listener has an 8 MiB transport limit, configured through its service
provider before HTTP/1.1 or HTTP/2 captures it. The image reader enforces the
smaller application limit; JSON/auth readers retain their own bounds. Very large
bodies rejected by the transport can end the connection before a problem response.
The 5 MiB boundary and chunked-body rejection are exercised through real HTTP.

Re-encoding discards source metadata, extra frames and trailing content. No SVG,
GIF, video, thumbnail generation, resizing or EXIF orientation correction is
provided here. Export rotated photographs with the intended pixel orientation
before uploading. PostgreSQL bytea stores the normalized bytes; no filesystem
path or original upload is retained.

An unreferenced upload is readable and attachable only by its administrator
owner. Once any post references it, other administrators can use it too, matching
the tutorial's shared editorial permissions. Readers and anonymous visitors see
pixels only while at least one PUBLISHED post references the image. An image
shared by two published posts therefore remains public when only one retracts.

Every image response uses no-store. Permission changes govern subsequent
requests; they cannot erase a copy already downloaded. The controller materializes
the bounded byte array, and TransactionBoundary finishes its read transaction
before the binary writer runs. JSON still serializes inside its transaction.

The browser sends File directly through fetch, with the session's uploadImage
link and CSRF token. Its native file control uses the component DSL and lifecycle.
Only media metadata is mapped into BlogPost; saving emits an ID-only reference.
Pending uploads block Save. Removal or navigation aborts and ignores late upload
responses. A delayed post acknowledgement preserves a newer cover/description.

## Collect abandoned uploads

Upload and post save are separate transactions. A closed tab or a removed cover
may leave an unused image. Eligible images have no post references and were
**created more than 24 hours ago**. This is upload age, not time since unlinking.
Every reference protects the image, including drafts.

A successful upload checks at most 20 old orphans belonging to its caller.
An operator can process one batch of at most 100 old orphans across owners:

```text
sbt --server "application-backend/runMain com.anjunar.blog.MediaCleanupMain"
```

Use the intended database configuration. Repeat the command to drain additional
batches; there is no scheduler in this chapter. A failed upload transaction also
rolls back cleanup in that transaction.

Both reference loading and cleanup lock the Media row. Cleanup rechecks references
after acquiring the lock, so it cannot delete an image attached while it waited.
The foreign key provides a final database constraint. Cleanup currently loads the
bounded bytes with each selected entity; batches are deliberately small.

## Verification

```text
sbt --server "application-backend/testFull" "application-frontend/testFull" frontendAssets
npx playwright test
```

Use the isolated migrated PostgreSQL database, chapter 12 SMTP test settings,
public-post fixtures, the bootstrapped test administrator and psql via PATH or
BLOG_PSQL. Backend and browser runs are sequential; browser suites share the
real sign-in rate limit. For this chapter alone, run npm run test:browser:media.

The chapter has 148 backend tests, 38 Scala.js tests and 67 browser checks
(56 controlled contracts and 11 real workflows). Coverage includes binary delivery
after transaction completion, serialization rollback, private/public transitions,
invalid and oversized uploads, version conflicts, delayed replies, and cleanup
racing with attachment. Browser tests remove only the rows they create.
