---
slug: error-case-instead-of-rejection
branch: claude/errorcase-status-rejection-d8289e
owner: claude
status: in-progress
started: 2026-11-05
---

## Goal

Tell clients less about why a command was refused. Add `spine.core.ErrorCase` and
`Status.error_case`, deprecate `Status.rejection`, and remove the filter-rejection hack
`CommandFilter.reject(CommandEnvelope, RejectionThrowable)` with no replacement in filters.
`Status.error` is not changed here.

## Context

This is the first PR of a series that replaces SpineEventEngine/core-jvm#1705, which grew too
big for one review. `ErrorCase` comes from that PR unchanged.

Before this change, `Status.rejection` reached clients only from
`CommandFilter.reject(…, RejectionThrowable)`. `AckRejectionPublisher` re-posted that rejection
to the `EventBus`, and `CommandAckMonitor` turned it into `CommandRejected`. The Java client
already treated a `REJECTION` ack as an invalid status. `CommandLog` stored handler rejections
in `CommandTimeline.how_handled`, a `spine.core.Status`.

## Decisions

1. Filters do not handle permissions or other business rules. Filtering a message before it is
   posted to a bus is only for validating it (an anti-corruption layer), as `ValidatingFilter`
   does; the next session moves `ValidatingFilter` off `Error`. So a filter gets no
   replacement for `reject(…, RejectionThrowable)`. (Owner, 2026-11-05; this replaced an
   implemented `CommandFilter.deny(command, Error)` with an in-bus marker.)
2. `ErrorCase.REQUEST_DENIED` stays in the enum, unused for now.
3. The Java client passes an `error_case` ack to `ServerErrorHandler` as an equivalent `Error`
   (type `spine.core.ErrorCase`, code = the number, message = the name). The handler's
   signature does not change.
4. `CommandTimeline.how_handled` is a `HandlingStatus` (`ok`, `error`, `rejection`) in its own
   file, so the log keeps the full details without the deprecated field.
5. `CommandErrored` always keeps the full `Error`; `error_case` is the case returned to the
   client. An error-case `Ack` gets an error made of the case (`ErrorCase.toError()`).
6. The bump is `.620`, not `.613`: removing public API makes the PR breaking, and the version
   policy then rounds up to the next multiple of 10. #1705 is also at `.620`; the PR that
   merges second re-bumps.

## Plan

- [x] Task file.
- [x] Bump the version to `2.0.0-SNAPSHOT.620` (breaking: public API removed).
- [x] Protos: `error_case.proto`; `Status.error_case`, deprecated `Status.rejection`;
  `handling_status.proto`, `CommandTimeline.how_handled`; `CommandErrored.error_case`;
  docs of `CommandService.post()`.
- [x] core: `ErrorCase.toError()`; deprecate `Responses.rejectedBecauseOf()`.
- [x] server: `CommandAckMonitor`; `CommandLogProjection`; remove `AckRejectionPublisher`,
  `CommandBus.initObservers()`, the `reject()` helpers of `MessageIdExtensions`;
  `BusFilter` docs.
- [x] client: `CommandRequest` handles `ERROR_CASE`; `ServerErrorHandler` docs.
- [x] server-testlib: deprecate `ResponseStatusSubject.isRejection()`.
- [x] Tests: remove the filter-rejection tests and fixtures; specs for the ack monitor, the
  command log, `ErrorCases`, and the client answered with an error case.
- [x] Migration note: `docs/migration/command-filter-rejection.md`.
- [x] Build: `./gradlew clean build dokkaGenerate`; read the test counts
  (2026-11-05: core 186, client 189, server 2466 + 1 skipped, server-testlib 159, no failures).
- [x] Review: `pre-pr` (PASS; PR SpineEventEngine/core-jvm#1707).

## Follow-ups

- #1706: also remove `Status.rejection`, `Responses.rejectedBecauseOf()`, and
  `ResponseStatusSubject.isRejection()`.
- spine-ts: drop the `rejection` ack branch; read `error_case`.
- `CommandLog` never receives `HandlerFailedUnexpectedly` (routing bug on master).

## Log

- 2026-11-05 — questions answered, plan drafted.
- 2026-11-05 — implemented; the shared filter fixture is `CompanyNameFilter` (testFixtures).
  Also fixed the format arguments of the "Cannot handle ack status" message in
  `CommandRequest`, which lacked the status case.
- 2026-11-05 — owner: client-facing docs must not tell where the server keeps the details
  (`Status.error_case`, `ErrorCase`, `CommandService.post()`, `ServerErrorHandler`).
- 2026-11-05 — review: `CommandLog` records an error made of the error case when
  `CommandErrored` carries only the case; the unused `BusFilters.Denying` is removed.
- 2026-11-05 — Kotlin review: `errorOfCase(number)` converts a number unknown to the client
  (`UNRECOGNIZED`) without throwing; `CommandRequest` and `CommandLogProjection` use it.
  The marker identity has one source in `CommandDenials`; `causedError()` is no longer
  `@VisibleForTesting`; the end-to-end spec is renamed to `DeniedCommandSpec`.
- 2026-11-05 — owner: `BusFilter` docs must not mention `CommandFilter.deny()` (a super-type
  never documents its subtypes); the paragraph is removed.
- 2026-11-05 — owner: `CommandErrored` always stores the full `Error`; `error_case` is the case
  returned to the client (its doc no longer mentions `Status.error`, which goes away). For an
  error-case `Ack` a filter built itself, `CommandAckMonitor` fills `error` with
  `errorOfCase()`; the `CommandLog` fallback is removed.
- 2026-11-05 — owner: System-type docs do not restate that the data stays on the server
  (`CommandErrored.error`, `HandlingStatus.error`).
- 2026-11-05 — owner: the `ErrorCase` value docs are trimmed to what the client learns; they no
  longer name filters, Bounded Contexts, handlers, internal types, or tenant reasons.
- 2026-11-05 — owner: `CommandService.post()` docs drop "the value tells only the kind of the
  problem" and the note on handler rejections; `command_log_events.proto` loses the trailing
  `//` of its one-paragraph docs.
- 2026-11-05 — owner: no permissions in filters (anti-corruption layer only). Removed
  `CommandFilter.deny()`, `CommandDenials`, the reason-hiding observer of `CommandBus`, the
  denial specs, and the `CompanyNameFilter` fixture. The client spec now uses a filter that
  answers with `INVALID_REQUEST`. `REQUEST_DENIED` kept, unused.
- 2026-11-05 — owner: forget clients older than the server (a major version), so one function:
  `errorOfCase()` is removed; `CommandRequest` and `CommandAckMonitor` call `toError()`.
- 2026-11-05 — PR #1707 opened. Pre-PR review fixes: `toError()` also rejects the reserved
  `ERROR_CASE_UNSPECIFIED`; deprecation suppressions say what they cover; the
  `causedError()` test is named after it; `CommandLogProjection` and `ServerErrorHandler`
  docs tightened; `CommandBus` in code font in the System protos.
- 2026-11-05 — Codex review on #1707: the deprecation notes of `Status.rejection`,
  `Responses.rejectedBecauseOf()` and `ResponseStatusSubject.isRejection()` keep to their
  own scope (only that the field is going to be removed).
