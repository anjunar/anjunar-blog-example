# User accounts and sign-in

Chapter 11 adds an operator-created administrator, password sign-in, server-side
sessions, and an account page. Chapter 12 will add public registration, email
confirmation, and password recovery. Permission policies and editing follow
after that.

## Upgrade the database

Configure a separate local PostgreSQL database as described in the README.
Stop the old application, then run from the repository root:

```text
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain preview"
sbt --server "application-backend/runMain com.anjunar.blog.SchemaMain migrate"
```

The new blog_account table is discovered through CDI. Existing BlogPost schema
IDs and data stay unchanged. A chapter 10 database advances to revision 2; a
fresh database starts at revision 1 with both tables. Repeating migrate reports
AlreadyApplied. As in chapter 6, a preview of existing named CHECK constraints
can report INCOMPLETE (exit 3); the executor verifies them under its migration
lock. This does not authorize destructive replacement of a table.

## Create the first administrator

Bootstrap is an explicit CLI command, never an HTTP endpoint or automatic
startup action. Choose the email and a long, unique password (15–128 characters).
Keep credentials out of command arguments and committed configuration.

In PowerShell, with the database variables already set:

```powershell
$env:BLOG_ADMIN_EMAIL = "admin@example.com"
$adminSecret = Read-Host "Administrator password" -AsSecureString
try {
  $env:BLOG_ADMIN_PASSWORD = [Net.NetworkCredential]::new("", $adminSecret).Password
  sbt --server "application-backend/runMain com.anjunar.blog.BootstrapAdminMain"
} finally {
  Remove-Item Env:BLOG_ADMIN_PASSWORD -ErrorAction SilentlyContinue
  Remove-Item Env:BLOG_ADMIN_EMAIL -ErrorAction SilentlyContinue
  $adminSecret.Dispose()
}
```

In Bash:

```bash
export BLOG_ADMIN_EMAIL=admin@example.com
read -r -s -p "Administrator password: " BLOG_ADMIN_PASSWORD
echo
export BLOG_ADMIN_PASSWORD
sbt --server "application-backend/runMain com.anjunar.blog.BootstrapAdminMain"
unset BLOG_ADMIN_PASSWORD BLOG_ADMIN_EMAIL
```

Expect Administrator created followed by its UUID. The command normalizes email,
takes a PostgreSQL transaction advisory lock, checks for an existing
administrator, and persists one account through Hibernate. Two concurrent
bootstrap commands cannot both pass the check.

A repeated command fails if any administrator already exists, even if the new
email differs. It does not reset passwords or elevate an existing account.
Credentials are passed to this short-lived child process through its environment;
remove them afterward. No default account or password is supplied by the app.

## Open the account page locally

The session cookie is Secure by default. For the tutorial's local HTTP listener,
explicitly opt into development cookies in the terminal that starts sbt.

PowerShell:

```powershell
$env:BLOG_COOKIE_SECURE = "false"
sbt --server frontendAssets "application-backend/run"
```

Bash:

```bash
export BLOG_COOKIE_SECURE=false
sbt --server frontendAssets "application-backend/run"
```

Open http://127.0.0.1:8080/en/account or follow Account in the header. Sign in,
reload the page, then sign out. The account response exposes only id, version,
email, and role. The public blog remains readable without an account.

Use HTTPS and the default BLOG_COOKIE_SECURE=true outside local HTTP development.
The flag controls the cookie; it does not add TLS to Undertow. The later deployment
chapter supplies HTTPS. No forwarded client-address headers are trusted here.

## Follow the implementation

Backend files under application/backend/src/main/scala/com/anjunar/blog:

| File | Responsibility |
| --- | --- |
| Account.scala | Entity, stable schema IDs, safe mapper fields, typed email lookup. |
| PasswordHash.scala | JDK PBKDF2-HMAC-SHA256, salts, bounded hash parsing and verification. |
| BootstrapAdminMain.scala | Explicit first-admin command with a database lock. |
| LoginRequest.scala | A bounded, strict credential command reader, separate from entity writes. |
| LoginLimiter.scala | Per-account/address attempt windows and bounded concurrent hashing. |
| SessionIdentity.scala | Session principal, CSRF, account revocation and absolute expiry. |
| AuthenticationFilter.scala | Resolve the current identity, protect auth writes, set no-store. |
| AuthenticationResource.scala | Session state, current account, login and logout. |
| SecurityConfig.scala | Secure-cookie default, idle and absolute lifetimes. |
| ApplicationMain.scala / SessionServer.scala | Cookie-only session tracking, capacity and shutdown ordering. |
| RequestTransaction.scala | Run session mutations after a successful transaction commit. |

Frontend files under application/frontend/src/main/scala/com/anjunar/blog/frontend:

| File | Responsibility |
| --- | --- |
| Account.scala | Mirrored safe account fields, session envelope and credential models. |
| AccountService.scala | Fetch CSRF and make same-origin login/logout requests. |
| AccountActions.scala | Submission state, error status and clearing password fields. |
| AccountPage.scala | One cohesive form/account tree with i18n messages and native labels. |
| HttpJson.scala | JSON GET/POST with credentials and a CSRF header. |

The form uses scalajs-ui-forms 1.0.9 from Maven Central and binds directly to
credential Properties. It prevents duplicate submissions, clears the password
after starting a request, and ignores completed UI work after disposal. Clearing
the field is not a promise to erase immutable JavaScript strings from memory.
Authentication mutations may still finish on the server after navigation; the
next account visit reads the server's current session.

## HTTP contract

| Request | Response |
| --- | --- |
| GET /service/auth/session | CSRF token and, if authenticated, the safe Account entity. Creates an anonymous session when needed. |
| GET /service/auth/me | Data[Account] with the Account.self graph; 401 without a valid identity. |
| POST /service/auth/login | JSON email/password plus X-CSRF-Token; rotated session state on success. |
| POST /service/auth/logout | X-CSRF-Token; invalidate the authenticated session and return fresh anonymous state. |

There is no registration, password-reset, or account-edit endpoint in this
chapter. These are deliberate credential commands, not a general entity update
path. The login reader accepts only email and password and limits its body to
4 KiB. Invalid commands return 400, oversized commands 413. Unknown email, wrong
password, and locked account return the same 401 result. A signed-in caller must
sign out before signing in again (409).

Auth responses use Cache-Control: no-store. Password hashes, lock state, and
authenticationVersion are omitted from the entity graph, EntitySchema and
frontend account model. The schema's default rules do not allow incoming writes.

## Session behavior and limits

The browser stores an opaque, HttpOnly, SameSite=Lax cookie named BLOGSESSION.
It has Path=/, no Domain and no persistent Max-Age. URL session tracking is
disabled. Neither the identity nor the cookie is stored in localStorage or
sessionStorage.

GET session supplies a random synchronizer token tied to the server session.
Login and logout require it in X-CSRF-Token; a token from another session fails.
Requests marked Sec-Fetch-Site: cross-site are also rejected. SameSite supplements
this check. Successful login rotates both session ID and CSRF token.

An authenticated principal contains only account UUID, authenticationVersion,
and sign-in time. On authenticated application REST requests, the filter reloads
the account (health checks are excluded). Deletion, locked=true, an authentication
version change, or eight hours since sign-in invalidates
the session. Current roles come from the database. The JPA optimistic-lock
version and authenticationVersion serve different purposes.

Undertow enforces a 15-minute idle timeout. All sessions live in this process;
restart signs everyone out. The session manager permits at most 2,048 active
sessions. SessionServer ends session listeners before RESTEasy shuts down Weld,
because RESTEasy 7.0.5 otherwise closes CDI first.

Session creation/rotation for login and logout runs only after serialization
and successful database commit. Rejected requests, writer failures and rollback
discard these callbacks. The anonymous GET session is read-only database work
and creates only container session state. This is not a distributed transaction
between the database and browser: a network failure can still hide an otherwise
successful response.

PBKDF2 uses 600,000 iterations, a fresh 16-byte salt, and a 256-bit key. Stored
formats are strictly bounded; unknown accounts still pay for a verification
against a decoy hash. This JDK-based choice is CPU-hard, not Argon2id's memory-hard
design. See [OWASP password storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).

Sign-in permits five attempts per canonical email and thirty per direct client
address in a 60-second window, at most 4,096 limiter keys and four concurrent
hash verifications. Limits include successful attempts. A 429 response carries
Retry-After: 60. This is a single-process limiter; it resets on restart. Behind a
reverse proxy, the current direct-address bucket is shared by clients until
trusted proxy handling is configured. It is not a distributed abuse-prevention
system.

## Verify

With a separate migrated test database configured:

```text
sbt --server "application-backend/testFull"
sbt --server "application-frontend/testFull"
npm run test:browser
```

Expect 61 backend tests, 9 Scala.js mapping tests and 16 Chromium contract tests.
The browser contract project intercepts data requests and needs no database.
The backend tests own and remove their account fixtures; existing rows remain.

The database browser projects make real requests. The existing blog project
needs database/examples/public-posts.sql. The authentication project needs an
administrator created by BootstrapAdminMain in that dedicated test database:

```text
npm run test:browser:database
npm run test:browser:auth
```

Set BLOG_TEST_ADMIN_EMAIL and BLOG_TEST_ADMIN_PASSWORD for the latter, using the
same short-lived environment approach as bootstrap. Expect two blog tests and
one authentication test. These projects never provision accounts themselves or
modify the sample posts. Keep port 18080 free; Playwright starts/stops the
server and explicitly uses development cookies for its local HTTP origin.

Verification includes valid/invalid login, CSRF, session/token rotation, cookie
flags, logout replay, revocation, throttling, bounded input, private responses,
callback execution after commit, browser reload, field binding, double-submit
prevention, error recovery and mobile layout. Screenshots live in test-results.
