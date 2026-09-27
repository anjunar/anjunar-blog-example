# Chapter 15: building a form from end to end

The editorial workspace can now create and edit posts through the chapter 14
write contract. The form binds directly to the frontend BlogPost. Save actions
follow server links, send mapper-generated partial JSON with the current version,
and merge the response without overwriting text entered while saving.

## Start and try it

Use the existing migrated development database and bootstrapped administrator.
No database mapping or dependency version changes in this chapter.

```text
sbt --server frontendAssets
sbt --server "application-backend/run"
```

Configure BLOG_DB_URL, BLOG_DB_USER and BLOG_DB_PASSWORD as before. Set
BLOG_COOKIE_SECURE=false only for local HTTP, then sign in at /en/account.

1. Open editorial and choose **New post**. Supply a title and a lowercase,
   hyphen-separated slug; optionally add a summary and content.
2. Save. The server creates a draft; the form switches to its edit route once
   there are no newer unsaved edits.
3. Change the title, clear the summary and save again. Reload to verify the
   persisted title and empty summary.
4. Open the same post's edit route in two tabs. Save in the first tab, then
   submit an edit from the second. Its old version receives 409 and its text
   stays in the form. **Discard my edits and reload** explicitly replaces it
   with the current server state.
5. Open preview to publish or retract using the existing chapter 13 controls.

New-post routes require a create link; edit routes require an update link.
The API still enforces ADMIN, CSRF, property rules and the current version.
A frontend link is an advertised capability, not authorization.

## How the form is connected

| Part | Responsibility |
| --- | --- |
| BlogPost | Mirrors the entity, defines client constraints, creates mapper payloads and merges acknowledged values. |
| PostEditorPage | One continuous compose tree, form binding, labels/errors, submit checks and lifecycle. |
| PostEditorActions | Frozen submission, busy/conflict/error state, response reconciliation and disposal. |
| EditorialService | Validated same-origin action links, CSRF/session lookup and typed responses. |
| HttpJson | POST/PATCH transport and typed problem details. |

The form uses form(post), input("title"), input("slug") and textAreaInput for
summary/content. It checks both validateBindings and validate before sending.
Client validation helps the user; the JSON mapper remains responsible for
server-side field validation, with Hibernate's existing callbacks guarding
complete entity invariants.

Attributes use AttributeDsl.setAttribute with an imported AttributeDsl object,
so the inherited component setter cannot shadow the DSL function. Each call targets the component
provided by its enclosing DSL block. Reactive aria-invalid observers stay
inside the input/textarea block and are disposed with that control. Labels use
for/id pairs; each control has aria-describedby. The whole UI tree stays in
compose, including attribute and submit-event bindings.

## Text, null and partial writes

Native text controls bind Property[String]. Content and summary now use nullable
String properties, matching the entity's scalar fields, rather than binding an
Option object to a text control. Null still distinguishes missing list content
from an empty draft. Only loaded detail data or an explicitly initialized new
draft may be saved. PublishedAt remains an Option because it is not edited here.

The JSON mapper emits dirty properties. Do not replace it with a hand-built copy
of every entity field. The small transport adjustments are deliberate:

- Remove the empty ID on creation.
- Include version on every existing-post save, even when that property is clean.
- Turn an explicitly cleared summary into JSON null.
- Preserve empty content as an empty string.
- Read status/publishedAt from responses but exclude them from mapper writes.

Successful saves update property defaults. The next payload therefore contains
only changes since the last acknowledged values. This also preserves version zero.

## Responses must not overwrite newer edits

PostEditorActions captures a PostSnapshot and creates the JSON body synchronously,
before the asynchronous session request. That snapshot is local comparison state,
not another REST model. Save is disabled while busy, but the inputs remain editable.

For each field, mergeSaved replaces the current value only if it still equals the
submitted value. It always advances the default to the acknowledged server value,
and refreshes ID, version and links. A later save therefore uses the new version
and still contains any newer text.

Creation follows the same rule: the returned ID is adopted immediately. If typing
continued during creation, the same form stays mounted and its next save uses
update, not create. Once those edits are saved, the route is replaced with the
saved post's edit URL.

Disposal removes observers and suppresses late response handling and navigation.
It does not cancel or undo a write that may already have reached the server.

## Error behavior

| Result | Form behavior |
| --- | --- |
| Local invalid value | Show the control error; send nothing. |
| 400 / slug field conflict | Show applicable server errors; allow correction and another save. |
| Version conflict | Keep the input, disable saving, offer explicit discard/reload. |
| 401 / 403 | Keep the input and stop saves; expose the account link. |
| Network / unexpected failure | Treat the result as unconfirmed; stop blind retries and offer the editorial list. |

Field errors use Form.setErrorResponses. An error from an older request is applied
only if the affected input still equals what was submitted. Cross-field errors
such as publicationConsistent appear in the form-level alert. Null error lists
do not leave the form stuck in its saving state.

No automatic retries, conflict merging, autosave or offline draft storage are
introduced. Navigating away or reloading discards unsaved text; copy any text you
want to retain before explicitly discarding a conflict.

## Verify

```text
sbt --server "application-frontend/testFull" frontendAssets
npx playwright test --project=contracts
npx playwright test --project=forms
```

There are 21 Scala.js tests and 42 browser contracts. The forms project adds a
real create/edit/clear-summary/version-conflict workflow against PostgreSQL.
It needs the dedicated BLOG_TEST_ADMIN_EMAIL/PASSWORD and psql on PATH, or
BLOG_PSQL. It deletes only its own created UUID.

All seven browser projects contain 49 tests: contracts, forms, changes, editorial,
database, authentication and recovery. The database needs the prior sample posts;
recovery needs chapter 12's local SMTP capture environment. Keep port 18080 free.
Run the backend SMTP suite separately from browser recovery.

The chapter also checks ServerIntegrationSpec and PostChangesSpec: 18 backend
tests. The unchanged complete backend suite still contains 112 tests.
