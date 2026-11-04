---
slug: status-error-case
branch: claude/error-security-hardening-060304
owner: claude
status: in-progress
started: 2026-11-04
related-memories:
  - feedback_proto_one_message_per_file
  - feedback_new_code_in_kotlin
  - system-events-synchronous-in-tests
---

# Tell clients less: `spine.core.ErrorCase` and System diagnostic events

## Goal

A client learns only what kind of problem stopped its request, never why. `spine.core.Status`
carries a `spine.core.ErrorCase` instead of a `spine.base.Error`. The server keeps every detail
in diagnostic events of the System context, and a logger on the System event bus writes them to
the server log. A developer finds the details by the request ID the client already holds; a
client probing the server gets one opaque value per attempt.

## Context

Today the server answers with the whole `spine.base.Error`: exception class names, messages
with type names, every constraint violation with the value that failed, and, for a command of
an internal type, the Java stack trace (`CommandService.handleInternal` uses
`Errors.fromThrowable`). Queries add the full `Error` in the `spine-error-bin` trailer. This
helps automated attacks that try argument after argument until a request is accepted.

The proposal for rejecting unregistered tenants (`reject-unregistered-tenants.md`, merged in
SpineEventEngine/core-jvm#1704) is implemented after this task. Its sections 3–6 report tenant
errors with `TenantError` details and `ErrorInfo`; they are rewritten against `ErrorCase` when
that work resumes (see "Follow-ups").

### What clients are told today

| Request and case | Where | Sent to the client |
|---|---|---|
| Command of an internal type | `CommandService.handleInternal` | `Error` with the stack trace |
| Command type no context serves | `CommandService.serveNoContext` | `UNSUPPORTED_COMMAND`, type name |
| No tenant / tenant in a single-tenant context | `CommandValidator` | `TENANT_UNKNOWN` / `TENANT_INAPPLICABLE` |
| Command fails validation | `CommandValidator` | `INVALID_COMMAND` + violations with values |
| No handler in the context | `DeadMessageFilter` | `UNSUPPORTED_COMMAND` |
| An application filter refuses | `BusFilter.reject(envelope, Error)` | The application's `Error` |
| An application filter rejects | `CommandFilter.reject(envelope, RejectionThrowable)` | `Status.rejection` |
| Query invalid or target unsupported | `QueryValidator` | `INVALID_ARGUMENT` + `spine-error-bin` |
| Query: internal type, no context, failure | `QueryService` | `UNKNOWN` |
| Topic or subscription invalid, unknown | `TopicValidator`, `SubscriptionValidator` | `UNKNOWN` |

## Decisions

- Q1 (2026-11-04): `ErrorCase` values do not name the kind of request. The client knows what it
  sent; per-kind names would also clash with `CommandValidationError` and
  `EventValidationError` values, which are siblings in the `spine.core` package (checked with
  protoc 4.36.2). The diagnostic events follow the same scheme.
- Q2 (2026-11-04): clients get no validation details. `INVALID_REQUEST` only; the violations go
  to `RequestValidationFailed`. No list of field paths in `Status`, no verbose mode for tests.
- Q3 (2026-11-04): `Status.rejection` stays, not deprecated. `CommandFilter.reject(envelope,
  RejectionThrowable)` keeps filling it: a command rejected by a filter is not `ok`. Only
  `Status.error` is deprecated.
- Q4 (2026-11-04): queries and subscriptions fail the RPC with a fixed gRPC code. The
  `ErrorCase` travels in the standard `grpc-status-details-bin` trailer as a packed
  `spine.core.Status`, with no description text. `spine-error-bin` is no longer sent.
- Q5 (2026-11-04): for a request no Bounded Context serves (internal type, or no context), the
  service builds `UnsupportedRequestReceived` and hands it to `DiagnosticLog` directly. It is
  not posted to any bus, and nothing is stored.
- Q6 (2026-11-04): `CommandErrored` stays the fact of the command lifecycle. It gets
  `error_case`; its `error` field is deprecated. The diagnostic event carries the details under
  the same command ID.
- Q7 (2026-11-04): `error_case` is a member of the `status` oneof. The server never sets both
  `error` and `error_case`. Clients built before this change see `STATUS_NOT_SET` (checked with
  protoc: the old schema decodes only an unknown field `4`); acceptable while 2.0 is not
  released (core-jvm `2.0.0-SNAPSHOT.612`, spine-ts `2.0.0-snapshot.26`).
- Q8 (2026-11-04): the logging design in "Design → `DiagnosticLog`", with no `SystemSettings`
  switch. Operators control the logger through the logging backend.
- Q9 (2026-11-04): filters keep returning acks made with `reject(envelope, Error)`. The
  deprecated `Status.error` stays the carrier between a filter and its bus: the filter chain
  converts each such ack at once, and nothing outside the bus sees `error`. Replacing this
  carrier is part of the follow-up issue.

### To confirm (filled in while writing this plan)

- A1. An unexpected exception while serving a request answers with `INTERNAL_ERROR`, and the
  service hands `RequestProcessingFailed` to `DiagnosticLog` directly, as in Q5. Posting
  through a System context that may be failing too is less reliable.
- A2. `ServerErrorHandler` changes in place from `BiConsumer<Message, Error>` to
  `BiConsumer<Message, ErrorCase>`; no second handler type.
- A3. The Java client handles a rejection `Ack`: it cancels the event subscriptions of the
  command and passes the rejection to the consumer of its type. `AckRejectionPublisher` also
  posts the rejection to the `EventBus`, so the same event may arrive through the subscription
  in any order; the client delivers an event ID at most once per command.
- A4. Diagnostic events have no `error_case` field; the event type tells the case (one event
  per value).
- A5. One PR for core-jvm, with the steps below as separate commits.

## Design

### `ErrorCase` and `Status`

```proto
// spine/core/error_case.proto
//
// The kind of problem that stopped a request.
//
// The value tells the client what kind of problem it is, not why it happened. The server
// keeps the details in a diagnostic event of the System context, which refers to the request
// by its ID.
//
enum ErrorCase {
    ERROR_CASE_UNSPECIFIED = 0;

    // The request, its ID, or its context fails validation.
    INVALID_REQUEST = 1;

    // The type of the request is not served: no Bounded Context or handler serves it,
    // or the type is internal.
    UNSUPPORTED_REQUEST = 2;

    // The tenant is missing, not applicable to the Bounded Context, or not registered.
    TENANT_ERROR = 3;

    // An application filter refused the request.
    REQUEST_DENIED = 4;

    // The subscription to activate or cancel is not known to the server.
    UNKNOWN_SUBSCRIPTION = 5;

    // An unexpected failure on the server side.
    INTERNAL_ERROR = 6;
}
```

```proto
// spine/core/response.proto
message Status {
    oneof status {
        option (choice).required = true;
        google.protobuf.Empty ok = 1;

        // Deprecated: the server sets `error_case` instead.
        base.Error error = 2 [deprecated = true];

        core.Event rejection = 3;
        ErrorCase error_case = 4;
    }
}
```

protoc generates `StatusCase.ERROR_CASE` for the new member, next to `getErrorCase()`.
The proposed names do not clash in `spine.core` (checked against the `.proto` files of
core-jvm and base-libraries).

### From a server-side error to an `ErrorCase`

| Source | `Error.type` / `code` | `ErrorCase` | Diagnostic event | Posted |
|---|---|---|---|---|
| `CommandValidator` | `CommandValidationError.TENANT_UNKNOWN` | `TENANT_ERROR` | `TenantCheckFailed` (`TENANT_NOT_SPECIFIED`) | bus |
| `CommandValidator` | `CommandValidationError.TENANT_INAPPLICABLE` | `TENANT_ERROR` | `TenantCheckFailed` (`TENANT_INAPPLICABLE`) | bus |
| `CommandValidator`, `EventValidator` | `INVALID_COMMAND`, `INVALID_EVENT` | `INVALID_REQUEST` | `RequestValidationFailed` | bus |
| `DeadMessageFilter` | `UNSUPPORTED_COMMAND`, `UNSUPPORTED_EVENT` | `UNSUPPORTED_REQUEST` | `UnsupportedRequestReceived` (`NO_RECEIVER`) | bus |
| An application filter | any other | `REQUEST_DENIED` | `RequestDenied` | bus |
| `CommandFilter.reject(…, RejectionThrowable)` | — | — (`rejection`, Q3) | — (`CommandRejected`, as today) | — |
| `ServiceDelegate`: internal type | — | `UNSUPPORTED_REQUEST` | `UnsupportedRequestReceived` (`INTERNAL_TYPE`) | log only |
| `ServiceDelegate`: no context | — | `UNSUPPORTED_REQUEST` | `UnsupportedRequestReceived` (`NO_CONTEXT`) | log only |
| `QueryValidator`, `TopicValidator`, `SubscriptionValidator` | `INVALID_*` | `INVALID_REQUEST` | `RequestValidationFailed` | context's System |
| The same | `UNSUPPORTED_*_TARGET` | `UNSUPPORTED_REQUEST` | `UnsupportedRequestReceived` (`NO_RECEIVER`) | context's System |
| `SubscriptionValidator` | `UNKNOWN_SUBSCRIPTION` | `UNKNOWN_SUBSCRIPTION` | `UnknownSubscriptionRequested` | context's System |
| Any service: unexpected exception | — | `INTERNAL_ERROR` | `RequestProcessingFailed` | log only (A1) |

The mapping lives in one internal Kotlin object. It reads the framework's own enums by
`Error.type` and `code`; an unknown type means an application filter.

### Diagnostic events

New files in `server/src/main/proto/spine/system/server/`, one event per file, each with
`option (is).java_type = "DiagnosticEvent"`. Every event carries:

- `core.MessageId request` — the request's ID and the type URL of its message (a command,
  event, query target, or topic target);
- `core.ActorContext actor_context` — the actor and the tenant, as sent.

There is no field for the Bounded Context (dropped while implementing): an event posted to
a context's System belongs to that context, and `DiagnosticLog` writes the name of the context
it is registered in, or of the service that writes an event directly.

| Event | Its own fields |
|---|---|
| `RequestValidationFailed` | `repeated validation.ConstraintViolation violation` |
| `UnsupportedRequestReceived` | `Reason reason` (`NO_CONTEXT`, `NO_RECEIVER`, `INTERNAL_TYPE`) |
| `TenantCheckFailed` | `Reason reason` (`TENANT_NOT_SPECIFIED`, `TENANT_INAPPLICABLE`, `TENANT_NOT_REGISTERED`) |
| `RequestDenied` | `base.Error error`, `string filter` (the class name) |
| `UnknownSubscriptionRequested` | — |
| `RequestProcessingFailed` | `base.Error error` (with the stack trace) |

`Reason` enums are nested, so their values do not clash in `spine.system.server`.
`TENANT_NOT_REGISTERED` is used only once the tenants work lands.

`CommandErrored` gets `core.ErrorCase error_case = 3`; `error` is deprecated.
`CommandAckMonitor` fills `error_case` from the ack; `CommandLogProjection` stores
`how_handled = Status { error_case }`.

### Reads: gRPC code and trailer

| `ErrorCase` | gRPC code |
|---|---|
| `INVALID_REQUEST`, `UNSUPPORTED_REQUEST`, `TENANT_ERROR` | `INVALID_ARGUMENT` |
| `UNKNOWN_SUBSCRIPTION` | `NOT_FOUND` |
| `INTERNAL_ERROR` | `INTERNAL` |

The code never tells more than the `ErrorCase`. The RPC fails with
`StatusProto.toStatusRuntimeException(google.rpc.Status { code, details: [Any(Status)] })`
and no message. `QueryResponse.response` and `SubscriptionUpdate.response` stay `ok`.

### `DiagnosticLog`

- `io.spine.system.server.DiagnosticLog` (Kotlin): an event subscriber that
  `SystemContext.newInstance()` registers, next to `registerTracing()`. It subscribes to every
  `DiagnosticEvent`: the six above, and `ConstraintViolated`, `CannotDispatchDuplicateCommand`,
  `CannotDispatchDuplicateEvent`, `HandlerFailedUnexpectedly`, `RoutingFailed`.
- An internal entry point logs an event given directly, for `ServiceDelegate` (Q5, A1).
- The logger is named after the class. No switch in `SystemSettings` (Q8).
- `WARNING` for the request events and the duplicate-dispatch events; `ERROR` for
  `RequestProcessingFailed`, `HandlerFailedUnexpectedly`, `RoutingFailed`, `ConstraintViolated`.
- Each event type is logged from its own log site with `atMostEvery(1, SECONDS)`.
- One statement per event: a short message and the event as JSON. Never a request payload.
  `ConstraintViolation.field_value` and the template placeholder holding the value are
  removed before logging. `Error.message` of validation errors is never logged: the
  validation library writes the violations, with their values, into it.
- The copy in `server-testlib` (`blackbox.probe.DiagnosticLog`) is removed from
  `BlackboxProbe`. `FailedHandlerGuard` and `UnsupportedCommandGuard` stay.

## Plan

- [x] **Version.** Bump `version.gradle.kts` above master (`bump-version`; master is at
  `2.0.0-SNAPSHOT.612`; re-check before the bump).
- [x] **Protos.**
  - `core`: `spine/core/error_case.proto`; `error_case` in `Status`, `error` deprecated;
    the comments of `Response`, `Status`, `Ack`, and `CommandService.Post` describe the new
    statuses.
  - `server`: the six diagnostic events; `CommandErrored.error_case`, `error` deprecated.
- [x] **`core` API.** `Responses.errorCase(ErrorCase)`; `Responses.errorWith(Error)`
  deprecated; `ResponseMixin.errorCase()`, `isError()` true for `error_case`, `error()`
  deprecated.
- [x] **Buses.**
  - `Bus` keeps the System write side given to `BusBuilder`, or a no-op one. Only the
    `CommandBus` gets it (see the Log). In the System context the write side is a no-op, so its
    own buses post nothing.
  - `FilterChain` converts an error ack from a filter: the `ErrorCase` mapping, the diagnostic
    event built from the `Error` and the envelope, posted under the envelope's tenant, and the
    slim ack passed on.
  - `MessageIdExtensions`: `causedError(ErrorCase)`; the `Error` overload deprecated.
  - `CommandAckMonitor` and `CommandLogProjection` move to `error_case`.
- [x] **Services.**
  - `ServiceDelegate`: internal type and no context answer `UNSUPPORTED_REQUEST` and log
    `UnsupportedRequestReceived`. The stack trace stops reaching clients.
  - `CommandService`: error acks carry `error_case`; an unexpected exception answers
    `INTERNAL_ERROR` (A1).
  - `QueryService`, `SubscriptionService`: validator errors post their diagnostic event to the
    context's System and fail the RPC with the code and trailer above; unexpected exceptions
    fail with `INTERNAL` (A1). `transport.Statuses` builds these failures;
    `invalidArgumentWithCause` goes away (it is `@Internal`).
- [x] **`DiagnosticLog`** as designed; registered in `SystemContext.newInstance()`.
- [x] **Java client.**
  - `CommandRequest.handleStatus`: `ERROR_CASE` cancels the subscriptions and calls the
    `ServerErrorHandler`; `REJECTION` is handled (A3) instead of throwing.
  - `ServerErrorHandler` takes an `ErrorCase` (A2); `LoggingServerErrorHandler`, `Client`,
    `Subscriptions.CancellationObserver` follow.
  - `StreamObservers.errorCaseOf(Throwable)` reads the trailer; `fromStreamError` and
    `MetadataConverter` deprecated.
- [x] **Test library.** `BlackboxProbe` drops its `DiagnosticLog`; `UnsupportedCommandGuard`
  checks `CommandErrored.error_case`; `ResponseStatusSubject` asserts `error_case`.
- [ ] **Tests** (Kotlin `*Spec`, Kotest assertions):
  - the mapping, value by value;
  - every row of the mapping table: the client gets only the `ErrorCase` (no `error` in the
    ack, no description or `spine-error-bin` in read failures), and the diagnostic event has
    the details;
  - no stack trace for an internal command type;
  - `DiagnosticLog`: levels, masking of values, no payload, the rate limit;
  - the client: an error-case ack, a rejection ack with and without the duplicate event,
    `errorCaseOf`;
  - the existing tests below, updated.
- [ ] **Docs.** Javadoc of `CommandService.post()`, `QueryService`, `SubscriptionService`,
  `BusFilter.reject()` (what the client sees), `CommandFilter`, `ServerErrorHandler`.
- [ ] **Build.** `./gradlew clean build dokkaGenerate` (proto changes); read the test counts,
  not only the exit code; regenerate the dependency reports with the full build.
- [ ] **Review and PR.** `pre-pr`; open the PR.
- [ ] **Follow-up issue** (the owner asked for it): once code migrates, remove `Status.error` and
  `CommandErrored.error`, keeping their names and numbers `reserved`. First replace the
  in-bus carrier (Q9) with a filter result type. Also remove the deprecated
  `Responses.errorWith`, `ResponseMixin.error()`, `causedError(Error)`,
  `StreamObservers.fromStreamError`, `MetadataConverter`.

## Expected impact on existing tests

Assert on error acks or `Error` trailers today, so move to `error_case` or diagnostic events:
`CommandServiceTest`, `StatusesTest`, `MultiTenantCommandBusTest`,
`AbstractCommandBusTestSuite`, `CommandAckMonitorTest`, `BusFilterTest`,
`CommandServiceMultiTenancySpec`, `CommandServiceUnpublishedLanguageSpec`,
`CommandServiceSingleTenancySpec`, `MessageIdExtensionsSpec` (`server`);
`MetadataConverterTest` (`client`); the three `server-testlib` files that read
`Status` errors. Query and subscription service tests that expect `UNKNOWN` now get the
mapped code.

## Follow-ups

- **Tenants proposal** (`reject-unregistered-tenants.md`): rewrite sections 3–6. Tenant errors
  answer `TENANT_ERROR`; one gRPC code for all tenant cases; `TenantError.Reason` becomes
  `TenantCheckFailed.Reason`; no `ErrorInfo` or `spine-error-bin`; the
  `CommandValidationError.TENANT_NOT_SPECIFIED` alias loses its purpose for clients; post
  `TenantCheckFailed` without writing data under an unregistered tenant; revisit Q8 there
  (spine-ts `NOT_FOUND` versus the JVM ack).
- **spine-ts:** the copy of `response.proto` and `error_case.proto`; `ErrorCase` in command
  acks (`commandPostError` stops sending `ValidationError`); the gRPC code mapping and the
  trailer for reads.
- **Dart client:** the same `Status` change.
- **Test library:** an assertion API over diagnostic events for `BlackBox` users.
- **Throttling:** the diagnostic events are the hook for counting failures per actor or tenant.

## Log

- 2026-11-04 — questions Q1–Q9 decided; plan drafted.
- 2026-11-04 — executing. Commits and the PR authorized by the owner's prompt; version bumped
  to `2.0.0-SNAPSHOT.620` (a breaking PR: `ServerErrorHandler` and the wire statuses change).
- 2026-11-04 — differences from the draft, found while implementing:
  - the `EventBus` gets no System write side: it rejects every event without subscribers
    (`DeadEventTap`), which is normal, and events are not client requests. Only the
    `CommandBus` posts diagnostics for filter errors;
  - a subscription to a topic no context serves is not an error (the service subscribes in
    every context), so `NO_CONTEXT` applies to commands and queries only;
  - A3 simplified: the Java client handles a rejection ack like an accepted one; the rejection
    reaches the client through the event subscription, as one thrown by a handler does. No
    de-duplication, no race between the two streams;
  - `REQUEST_DENIED` maps to the gRPC `PERMISSION_DENIED` (not produced by reads today);
  - `CommandLog` records a handler failure as `INTERNAL_ERROR` (the details stay in
    `HandlerFailedUnexpectedly`);
  - the test library's `DiagnosticLog` and its test are removed; `UnsupportedCommandGuard`
    reads `UnsupportedRequestReceived`.
