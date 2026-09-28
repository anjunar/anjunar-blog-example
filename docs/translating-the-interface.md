# Translating the interface

Chapter 20 adds German to the existing English interface. UI messages come from
the i18n macro and one catalog; the URL selects the locale. Posts, slugs, names
and tags keep their existing values. Chapter 21 introduces editorial translations.

## Run this checkpoint

Use the chapter 19 database and configuration. This chapter needs no schema
migration. It uses scalajs-ui 1.0.13 from Maven Central for direct macro support
in attributes, selection labels and reactive messages.

```text
npm ci
sbt --server frontendAssets "application-backend/run"
```

Open http://127.0.0.1:8080/en and http://127.0.0.1:8080/de. Follow a post, reload,
then switch language. The shell, forms, errors expressed as application messages,
and editor controls change language. The post itself stays the same.

## The URL and the runtime

[BlogI18n](../application/frontend/src/main/scala/com/anjunar/blog/frontend/BlogI18n.scala)
declares English/German, the German catalog and English default.
[BlogPage](../application/frontend/src/main/scala/com/anjunar/blog/frontend/BlogPage.scala)
creates I18nRuntime.managed from cursor.browserUrl and provides it before mounting
the router and its children. There is no separate locale cookie or local-storage
preference. Unprefixed entry URLs start in English.

The header's language action navigates to router.localizedPath with the current
route, query and fragment. Ordinary browser history records the change.
The persistent header links bind their href through the same public API, with
the observed locale passed explicitly: those links compose before the router
mounts. Routed links inherit the router's locale normally.

FrontendHandler accepts the known /en and /de browser routes, including account,
editorial and error pages. Private pages retain no-store. REST/media remain under
/service; /de/service is not an API alias. Unsupported locale prefixes and unknown
browser paths return 404. A missing post under a known route still loads the shell
before REST supplies its error; server-rendered HTTP statuses come later.

## Messages, placeholders and controls

Use import ui.core.i18n.i18n and whole messages such as i18n"Save post".
The macro supplies RuntimeMessage identity and source metadata. Catalog entries
refer to message.key; no hand-written fingerprints or second translation map
are needed. The English source is the fallback when a German entry is absent.

Keep interpolated names stable with I18n.named. The list count uses count and total;
its German pattern is "{count} von {total} Beiträgen". These are named substitutions,
not an ICU plural/select pattern. Unit tests check that translations preserve
the source placeholder set.

Inside compose, pass the macro directly to DSL APIs that accept TextValue:
text(i18n"Save post"), button(i18n"Save post"), ariaLabel = i18n"Publication status",
a form control's placeholder = i18n"Choose an author", or
SelectOption("DRAFT", i18n"Draft"). The DSL resolves the component's i18n context
and creates the reactive binding. An explicit runtime.text wrapper is unnecessary
there. For changing messages, use state.map(value => i18n"..."); TextValue binds
both the selected message and the locale. Interpolating a mutable value still
requires deriving a new message when that value changes. String-only APIs outside
the DSL, such as ErrorResponse or a ComboBox converter, use resolveNow explicitly.

The catalog includes the public EditorMessages from scalajs-ui for toolbar labels,
dialogs, upload state and fallbacks. The existing editor("content") DSL and plugin
configuration stay intact. These controls inherit the same runtime.

The outer static document's html.lang is updated through a lifecycle-bound browser
observer. Its root is outside BlogPage's component tree; component attributes still
use the DSL. There is no SSR implementation in this chapter.

## Protect unfinished work

A locale navigation replaces the routed component. The header therefore disables
its language buttons while a page owns unfinished input or a pending operation,
and displays an explanation. Save, submit, clear or explicitly discard the form
first. Search filters already submitted in the URL survive the switch.

[LanguageNavigation](../application/frontend/src/main/scala/com/anjunar/blog/frontend/LanguageNavigation.scala)
combines guards registered by forms. Each guard watches the relevant Properties,
belongs to its component and releases its subscriptions when the component is
disposed. It neither stores another locale nor copies form data.

The post form covers changed values, relationships and uploads; account,
recovery and metadata forms cover their own inputs and pending work. Token-bearing
confirmation/reset pages keep their guard until the user leaves via an ordinary
link. AccountLink removes the secret fragment after reading it. Language navigation
reads the current browser query/fragment instead of restoring a stale router hash.

This protects the language buttons. It is not a general unsaved-work guard for
browser Back, reload, manually edited URLs or other navigation.

## Verify

Without PostgreSQL, run the model checks and controlled browser contracts:

```text
sbt --server "application-frontend/testFull" frontendAssets
npx playwright test --project=contracts
```

Expect 46 Scala.js tests and 65 browser contract tests. For only the six new
browser scenarios, use npm run test:browser:i18n. They cover German direct entry,
locale links, query/fragment retention, history, reload, form guards, saved edits,
the editor image dialog, confirmation tokens and error routes. Desktop and mobile
screenshots are recorded in test-results.

For the complete regression run, use the dedicated migrated database, public-post
fixtures, administrator, chapter 12 SMTP settings and psql on PATH or BLOG_PSQL:

```text
sbt --server "application-backend/testFull"
npx playwright test
```

Run these sequentially. Expect 159 backend tests and 77 browser tests across
eleven projects (65 controlled contracts and 12 real workflows). Keep port 18080
free. The real workflows honor the authentication rate limit.

## Deliberate boundaries

Detailed backend problem messages and built-in form-validation diagnostics still
use their existing English strings. Translating those requires a separate message
contract; the catalog does not translate arbitrary server text. Transactional
emails and their links remain English. Dates retain their existing ISO display.

The initial static HTML shell remains English until browser boot sets html.lang.
Server rendering and hydration are later chapters. Database-backed post
translations, content fallbacks and localized slugs belong to chapter 21.
