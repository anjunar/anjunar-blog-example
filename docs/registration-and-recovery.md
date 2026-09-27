# Chapter 12: registration and account recovery

The application now has four public pages:

| Page | Action |
| --- | --- |
| /en/register | Request an email confirmation link. |
| /en/confirm#token=… | Confirm ownership and choose the initial password. |
| /en/forgot-password | Request a password reset link. |
| /en/reset-password#token=… | Choose a replacement password. |

Registration creates a READER only after confirmation. It never changes an existing
account or promotes a reader to administrator. BootstrapAdminMain remains the
explicit way to create the first administrator. Sign-in still uses Soteria.

## Upgrade the database

Stop the application and set BLOG_DB_URL, BLOG_DB_USER and BLOG_DB_PASSWORD for
your development database. Update dependencies and inspect the migration:

```text
sbt --server "application-backend/update"
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain preview"
```

The only new object is public.blog_account_token. On a chapter 11 database the
preview reports INCOMPLETE (exit 3) for the existing named publication check;
this is the same read-only normalization limitation described in chapter 6.
Run migrate separately after reviewing the preview:

```text
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain migrate"
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain migrate"
```

A chapter 11 database advances from revision 2 to revision 3 with one CREATE TABLE.
The repeated migration reports AlreadyApplied with zero statements. A fresh
database starts at revision 1 with all three tables. Account and BlogPost
mappings and existing data stay intact.

## Capture development email

The optional Compose mail profile runs Mailpit v1.31.3, pinned to a release.
Its SMTP port and inbox UI are bound to localhost; it does not forward mail.

```text
docker compose --profile mail up -d mailpit
```

Keep BLOG_DB_PASSWORD set even when starting only mailpit, because Compose
validates the complete file. Open http://127.0.0.1:8025 to read captured messages.
A native Mailpit binary on the same ports is also supported.

In the terminal that starts the application, configure local mail and HTTP.
PowerShell:

```powershell
$env:BLOG_SMTP_HOST = "127.0.0.1"
$env:BLOG_SMTP_PORT = "1025"
$env:BLOG_SMTP_MODE = "local"
$env:BLOG_MAIL_FROM = "blog@example.test"
$env:BLOG_PUBLIC_ORIGIN = "http://127.0.0.1:8080"
$env:BLOG_COOKIE_SECURE = "false"
sbt --server frontendAssets "application-backend/run"
```

Bash:

```bash
export BLOG_SMTP_HOST=127.0.0.1
export BLOG_SMTP_PORT=1025
export BLOG_SMTP_MODE=local
export BLOG_MAIL_FROM=blog@example.test
export BLOG_PUBLIC_ORIGIN=http://127.0.0.1:8080
export BLOG_COOKIE_SECURE=false
sbt --server frontendAssets "application-backend/run"
```

Without BLOG_SMTP_HOST, requests for registration/reset email return 503;
existing sign-in and public posts continue working. A mail configuration must
be present even when requesting a link for an ineligible address.

Use /en/register, request a link for reader@example.test, open it from Mailpit,
and choose a 15–128 character password. Then sign in at /en/account. Request a
reset, choose a different password, and verify that an already signed-in
browser becomes anonymous on its next request. The old password stops working.

Opening a mail link is read-only. A POST with the session's CSRF token changes
the account. The frontend removes the token fragment from the address bar and
keeps it only for the current page. After reloading that page, reopen the mail
link or request another one.

## SMTP outside development

Set BLOG_SMTP_HOST, BLOG_MAIL_FROM and an HTTPS BLOG_PUBLIC_ORIGIN.
BLOG_SMTP_MODE defaults to starttls and BLOG_SMTP_PORT to 587.
Set BLOG_SMTP_USERNAME and BLOG_SMTP_PASSWORD together if the relay requires
authentication. Required STARTTLS and server-certificate hostname checking
are enabled. No trust-all certificate setting is used. Plain SMTP and HTTP
account links are allowed only for explicitly local hosts.

The origin comes from configuration, never the HTTP Host header. Do not put a
path, query, fragment or credentials in it. The application sends plain-text
messages through Jakarta Mail 2.1.3 and Angus Mail 2.0.4.

## Transaction and token rules

- Tokens contain 32 random bytes encoded as URL-safe Base64. The database keeps
  only their SHA-256 digest, canonical email, purpose, expiry and credential version.
- Each email/purpose has at most one token. A replacement revokes the previous
  link; both purposes expire after 30 minutes and work once.
- Registration stores no password or account before confirmation.
- Reset checks that the account is still unlocked and its authenticationVersion
  matches the value at issuance. It updates the hash and increments that version
  atomically with token deletion, invalidating all older sessions.
- A transaction-scoped PostgreSQL advisory lock serializes operations for one
  email, including simultaneous confirmation. A reset also locks and refreshes
  the Account row. SQL parameters are bound, never concatenated from input.
- AuthJson limits commands to 4 KiB and permits only the expected fields.
  The same CSRF, no-store and Soteria request setup protects all four endpoints.
- Email requests return the same 202 body for eligible and ineligible addresses.
  Mail work runs outside the response path. This reduces timing differences but
  is not a constant-time guarantee. Address and IP limits are process-local.

AccountToken is internal persistence state, discovered by CDI. It has no REST
entity graph, EntitySchema, frontend entity mirror or general update endpoint.
The request/response commands represent these particular operations.

## Delivery limits and housekeeping

Mail submission happens only after a successful database commit. One worker
and a 64-item in-memory queue keep SMTP out of the request transaction.
Only immutable message values cross into that worker; it never receives a
request-scoped bean, EntityManager, password or session.

A 202 response acknowledges the request, not delivery. Queue saturation,
SMTP failure or a process crash can lose an email. Errors log a generic message
without recipients, credentials or tokens. There is no automatic retry or
durable outbox in this chapter. Requesting a new link replaces the old one.
A later reliable delivery design needs a durable queue/outbox and an explicit
policy for protecting the message's bearer token at rest.

Issuing another link removes expired tokens for that email. Operators can also
periodically delete expired rows in their configured database:

```sql
DELETE FROM public.blog_account_token WHERE expires_at <= CURRENT_TIMESTAMP;
```

Expiry enforcement never depends on that housekeeping. Treat the local inbox,
browser traces and development data as private: they contain usable links.

## Verify

Use a dedicated migrated database. The backend recovery suite starts its own
SMTP capture server, so use a free port separate from a running Mailpit:

```powershell
$env:BLOG_SMTP_HOST = "127.0.0.1"
$env:BLOG_SMTP_PORT = "27125"
$env:BLOG_SMTP_MODE = "local"
$env:BLOG_MAIL_FROM = "blog@example.test"
$env:BLOG_PUBLIC_ORIGIN = "http://127.0.0.1:18080"
sbt --server "application-backend/testFull" "application-frontend/testFull" frontendAssets
npx playwright test --project=contracts
npx playwright test --project=recovery
```

Bash users set the same variables with export. Keep ports 18080 and the selected
SMTP port free. Do not run the backend suite and browser recovery project
concurrently: both own that SMTP port.

Expected results: 79 backend tests, 9 Scala.js tests, 20 browser contract tests,
and one real browser registration/recovery workflow. The browser workflow
leaves one uniquely named READER in the dedicated database; backend tests remove
their own accounts/tokens. No real email address or external SMTP service is used.

The two existing database browser tests and administrator sign-in test still
apply. See the earlier guides for sample posts and BLOG_TEST_ADMIN_EMAIL/PASSWORD.
Run all configured browser projects together only when those fixtures exist.

The new checks cover captured SMTP messages, digest-only storage, generic
responses, expiry, replacement, purpose separation, locked accounts, changed
credential versions, CSRF, limits, command validation, simultaneous consumption,
session revocation and serialization/commit failure. The browser contracts also
check token removal, no mutation on GET, resend, error states and mobile layout.

References: [OWASP password recovery](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html),
[Angus SMTP properties](https://eclipse-ee4j.github.io/angus-mail/docs/api/org.eclipse.angus.mail/org/eclipse/angus/mail/smtp/package-summary.html),
[Mailpit Docker configuration](https://mailpit.axllent.org/docs/install/docker/).
