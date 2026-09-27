# Working on the companion project

- Check Git status before making changes and preserve existing work.
- This project is a step-by-step tutorial for building a blog without multitenancy.
- Anjunar Stack is a technical reference, not a local build dependency.
- Implement only the step currently being discussed. Do not copy the entire stack in advance.
- Use libraries from Maven Central; do not introduce ProjectRef or publishLocal dependencies.
- Examples must run against the corresponding project revision.
- Import types and APIs instead of using fully qualified names in code. Import java.lang and use lang.Long, lang.Integer, etc. for Java wrappers. Use import aliases for name collisions.
- Use plain JPA and Bean Validation annotations on fields declared in the class body. An explicit @field target is unnecessary there; use it when an annotation on a constructor parameter needs to target the backing field.
- Write the blog articles, documentation, roadmap, code comments, and example text in English.
- Name article slugs abt-NN-title-in-kebab-case, where NN is the two-digit article number (for example, abt-01-building-a-complete-web-application-with-scala).
- English is the application's primary language. Introduce German as the second language in the internationalization chapter.
- Preserve the contract across the entity, EntitySchema, REST graph, and frontend model.
- Discover entity classes through EntityExtension and EntityRegistry. Entity-containing archives need beans.xml with bean-discovery-mode="all"; do not maintain a manual entity list in Persistence.
- BlogPost.Schema is the complete mapper and Criteria field model. Use reference for persistent singular attributes and preserve SingularProperty types; property is for mapper-only or transient fields. Keep every published @JsonbProperty field in the schema.
- Initialize SchemaProvider schemas only after Hibernate is ready and a CDI request transaction is active. RuntimeContext resolves the request EntityManager. Never store caller-specific permission state in a cached schema.
- Default mapper rules allow reads and deny writes. Public row visibility still belongs in queries/endpoint checks; findPublishedBySlug excludes drafts. Authenticated PostEditRule controls the four editable post fields; lifecycle fields stay read-only.
- Public reads use Data/Table envelopes and MapperMessageBodyWriter. Chapter 16 lists explicitly select BlogPostSummary columns; detail returns the real BlogPost entity. Keep version in both list/detail graphs and the projection. Queries enforce published-row visibility; schema response metadata describes structure, not permissions.
- Keep each UI tree together in compose; use the i18n macro for new translatable UI messages.
- The frontend uses scalajs-ui-core/json/router/forms 1.0.9 from Maven Central. BlogPost mirrors the published entity fields; JsonSchema is derived locally, independently of REST schema metadata. Routes load through BlogService/HttpJson and forward their AbortSignal. Preserve omitted rows, optional fields, and version zero. Keep editorial text separate from i18n UI messages.
- Build browser assets from the repository root with sbt --server frontendAssets. Edit application/frontend sources, never target/frontend. The existing backend serves this directory; API startup must not depend on it existing.
- Browser check: npm ci, npx playwright install chromium, npm run test:browser. The default contracts project starts the real backend on port 18080 and intercepts data requests, so it does not need PostgreSQL. Keep this port free. npm run test:browser:database exercises real data with a dedicated migrated database seeded with database/examples/public-posts.sql. Run application-frontend/testFull for the Scala.js model checks. The backend suite still needs its migrated test database.
- Add appropriate functional checks alongside each feature.
- Article examples should show the complete changed path, with imports and exact file locations. Include full relevant models/components/services and executable examples for the difficult behavior; do not explain essential missing code only in prose.
- Current check: sbt --server "application-backend/testFull". Use a separate local PostgreSQL database, BLOG_DB_PASSWORD, migrated with SchemaMain; see README.md and docs/schema-evolution.md for setup. Preserve SchemaId values and named publication checks; run migrations on the compile classpath so test-only entities are excluded. Model tests remove their own rows; transaction tests create and drop a uniquely named probe table.
- Do not modify the reference repository or content repository without a corresponding request.

- Chapter 11 adds Account; CDI discovers it alongside BlogPost. Keep all existing SchemaId values stable. Account.self and the frontend expose only id, version, email and role; never serialize passwordHash or authenticationVersion.
- BootstrapAdminMain is an explicit operator command guarded by a database advisory lock. Never add automatic first-user administrator promotion or reset existing credentials on startup.
- Auth writes require CSRF and JSON credentials are a separate bounded command, not a general entity update. AuthenticationFilter enters Undertow/Elytron/Soteria after TransactionBoundary begins; AuthorizationFilter enforces endpoint policies and CSRF on auth/recovery/editorial writes. Do not replace the container identity with a hand-written JAX-RS SecurityContext.
- Session changes for login/logout belong in RequestTransaction.afterCommit. Preserve rollback/writer-failure coverage. SessionServer closes sessions while Weld is still active.
- Secure cookies default to true; set BLOG_COOKIE_SECURE=false only for local HTTP. Keep HttpOnly, SameSite, cookie-only tracking, expiry and database revocation checks.
- Verify 125 backend tests, 26 frontend model/form/search/problem tests, 48 browser contract tests. test:browser:auth needs a dedicated bootstrapped test administrator via BLOG_TEST_ADMIN_EMAIL/PASSWORD. No password or cookie should be committed or printed.
- Soteria's IdentityStoreHandler and HttpAuthenticationMechanismHandler come from the library, with the official Weld bean decorator. PasswordIdentityStore verifies credentials; SoteriaAuthenticationMechanism calls notifyContainerAboutLogin. Keep the verified initializer, BeanManager JNDI lookup, CallerDetailsResolver service registration and Elytron configuration together.
- Persist authentication in afterCommit, without AutoApplySession/registerSession. request.logout must reach Soteria cleanSubject; current database roles and session revocation are verified through all three security APIs.

- Chapter 12 verifies email ownership before setting the initial password or creating a READER. Never accept a role in public registration or overwrite an existing account.
- AccountToken is internal state with stable SchemaId values, digest-only token storage, purpose, expiry and credential-version binding. It is not a public entity contract.
- Serialize token issuance/consumption by canonical email with the transaction advisory lock. Password reset locks/refreshes Account, updates its hash and authenticationVersion, and consumes the token in the same transaction.
- AccountRecoveryResource requires the existing CSRF filter and strict bounded commands. GET must never consume a link. Preserve generic request responses, throttling, expiry, replay and rollback tests.
- AccountMail enqueues immutable values only after commit. Its bounded queue is in memory, not a durable outbox; never claim guaranteed delivery or log mail tokens. Production SMTP requires STARTTLS and an HTTPS configured origin.
- The complete backend suite needs a free local SMTP capture port via BLOG_SMTP_HOST=127.0.0.1, BLOG_SMTP_PORT, BLOG_SMTP_MODE=local, BLOG_MAIL_FROM and BLOG_PUBLIC_ORIGIN. See docs/registration-and-recovery.md.
- Browser recovery tests capture SMTP locally and leave one uniquely named READER in the dedicated test database. Do not run them concurrently with the backend SMTP suite.

- Chapter 13 requires explicit endpoint policies: a method overrides its class; unannotated resources are denied. AuthorizationFilter enforces them after Soteria resolves the caller.
- PostAccess is shared by publication commands and PostLinks. Recheck role, CSRF and current entity state on every POST; a response link is not authorization.
- Publication commands lock the row and call the domain transition. Preserve conflict, rollback and version tests; general entity updates use PreparedChange in chapter 14.
- PostEditRule resolves the current CDI caller; lifecycle fields use read-only rules. Cache rule classes in EntitySchema, never permission results.
- Build links per Data/Table/SessionState response; keep them out of JPA state. Schema.forGraph still describes structure, not writable-field capabilities. Responses with caller-specific links use no-store.
- The editorial frontend follows supplied command URLs/methods, validates same-origin /service/ destinations, and replaces values/version/links on success. Clear stale actions on failure and ignore replies after disposal.
- test:browser:editorial needs the dedicated test admin plus psql on PATH or BLOG_PSQL. It creates/deletes its own UUID draft in the test database. All browser projects now contain 56 checks.

- Chapter 14 supports PreparedChange[BlogPost] for POST creation and PATCH editing. Check change.getEntity() before applying once. The JSON mapper validates supplied values through its injected Validator; do not add a second controller validation pass. Keep Hibernate's existing CALLBACK validation for complete entities before insert/update. Propagate failures to roll back; preparation is not an independent transaction.
- PATCH requires a nonnegative integer version. Lock the current row before comparing; never assign the request's version. Preserve missing versus null, server-generated identity, property rules and publication invariants.
- RequestJson accepts at most 1 MiB of strict UTF-8 JSON, rejects duplicate keys and limits nesting. PreparedChanges checks known fields, metadata and scalar shapes. Reference loading is deliberately rejected until authorized relationships arrive in chapter 17.
- Use the typed EntitySchema for slug checks, with query flush mode COMMIT to avoid premature writes; the database unique constraint still handles races. Preserve both adopted and explicitly named slug-constraint error handling.
- Return application/problem+json with safe details and field paths. Unexpected errors expose a correlation ID, never driver text. Keep HTTP status authoritative when parsing ProblemDetails in the browser.
- The mapper omits empty strings; editorial draft detail normalizes missing content to an empty value. Published detail still requires content.
- test:browser:changes executes docs/examples/post-changes.js unchanged against real HTTP and PostgreSQL, then deletes its own draft. Keep the article example executable and tested.

- Chapter 15 binds form(post) directly to BlogPost; text fields use nullable Property[String], preserving missing content versus an empty draft. Do not bind Option[String] to a String control. Keep scalar names, constraints and partial-write semantics aligned with the entity.
- BlogPost.writeBody uses JsonMapper dirty-field serialization, always adds the current version for updates, removes a new empty ID and maps an explicitly cleared summary to null. Lifecycle fields are read-only in the frontend mapper.
- Freeze payload and submitted values before any asynchronous session lookup. Merge each acknowledged field only if it has not changed since submission; always advance its default and the saved ID/version/links. Never let a delayed response erase newer typing or repeat creation.
- Server field errors use Form.setErrorResponses and only apply to unchanged submitted values. Cross-field errors remain visible at form level; stale versions, revoked access and unconfirmed writes stop blind retries while keeping the input.
- Set attributes through the DSL, never through element.setAttribute or another component receiver. Import ui.core.dsl.AttributeDsl and use AttributeDsl.setAttribute to disambiguate it from the inherited component method. Keep reactive attribute observers inside the target element's DSL block and dispose them with that element. Keep labels, aria-describedby, aria-invalid, native form submission and the entire DSL tree in compose.
- test:browser:forms creates/edits a real post, clears its summary and resolves a stale version through the UI, then removes its own UUID. It uses the same dedicated admin/database/psql setup as test:browser:changes.

- Chapter 16 keeps backend search in immutable BlogPostSearch values extending AbstractSearch; the frontend uses its own PostSearch route model. Public search always restricts rows to PUBLISHED, including for administrators; only the ADMIN editorial endpoint accepts DRAFT.
- Reuse hibernate/search/HibernateSearch with @RestPredicate/@RestSort and CDI providers. SearchBeanReader must fail for missing providers or unreadable fields; never silently omit a visibility predicate. Do not replace the generic engine with a post-specific query executor.
- Build row/count predicates separately against their own Criteria roots using BlogPost.schema attributes. Bind escaped literal LIKE text; whitelist sorts and finish every order with UUID. Date sorts put null dates last.
- BlogPostSummary is an intentional read-only constructor projection. Its selected metadata is safe for the endpoint's scope; DTO projections do not inherit entity property rules. Never pass a list summary into editing without loading detail. Publication links stay on detail because they require content.
- Page links preserve q/status/sort/limit and encode raw text as a URI template value, including literal percent escapes and braces. Keep filtered totals on empty pages and avoid Int overflow in page arithmetic.
- PostSearchForm is a shared component with its complete form tree in compose. Its pending fields are route state; submitting resets offset, while paging/history/reload retain the loaded filters.
- test:browser:search owns and cleans four UUID posts in the dedicated database. All eight browser projects contain 56 tests. Substring search and offset pagination do not promise indexed full-text performance or a stable snapshot under concurrent writes.
