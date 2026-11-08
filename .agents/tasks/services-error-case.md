---
slug: services-error-case
branch: claude/services-error-case-1d6e78
owner: claude
status: in-progress
started: 2026-11-06
---

## Goal

`CommandService`, `QueryService`, and `SubscriptionService` answer clients with an
`ErrorCase` only, and each problem is recorded as a specific System event of the serving
context. Slice 3 of splitting #1705; stacked on #1711 (slice 2, the buses).

## Decisions (2026-11-06)

- No masking; the details are stored as System events of the serving context.
- A request that reaches no context gets `UNSUPPORTED_REQUEST`; nothing is stored (#1709).
- One event per kind of problem, named after the kind of request. Event types live in
  `*events.proto` files; an event for `CommandLog` starts with `core.CommandId id = 1`.
- Read failures: a fixed gRPC status, and `grpc-status-details-bin` carries the `ErrorCase`;
  clients read it with `StreamObservers.errorCaseOf()`.
- `Status.error` and `ServerErrorHandler` change in slice 4.
- Validation options of the events, as in slice 2: `(required)` on each field that is
  always set when the event is posted (`validation_error`, `error`, and every field of the
  events posted after the request passed validation). `id` and `actor_context` stay
  optional where the request may lack them, with the reason in the field doc. No
  `(validate)`.

## What the client gets, and what is recorded

| Problem | Client gets | Recorded in the serving context |
|---|---|---|
| Internal type, type unknown to the server, no context | `UNSUPPORTED_REQUEST` | nothing |
| The type of the request cannot be read | `INVALID_REQUEST` | nothing |
| The `Stand` finds the request invalid | `INVALID_REQUEST` | `QueryValidationFailed`, `TopicValidationFailed`, `SubscriptionValidationFailed` |
| The `Stand` does not serve the target | `UNSUPPORTED_REQUEST` | `UnsupportedQueryReceived`, `UnsupportedTopicReceived` |
| The subscription is unknown | `UNSUPPORTED_REQUEST` | `UnknownSubscriptionActivationRequested`, `UnknownSubscriptionCancellationRequested` |
| Unexpected exception | `INTERNAL_ERROR` | `CommandPostingFailed`, `QueryExecutionFailed`, `SubscriptionServiceFailed` |

- Commands: an `Ack` with `error_case`. `INTERNAL_ERROR` fails the call instead, because
  the bus may have acknowledged the command already.
- Reads: the call fails with `INVALID_ARGUMENT` (`INVALID_REQUEST`, `UNSUPPORTED_REQUEST`,
  `TENANT_ERROR`), `PERMISSION_DENIED` (`REQUEST_DENIED`), or `INTERNAL` (`INTERNAL_ERROR`);
  no description; `grpc-status-details-bin` holds a `google.rpc.Status` with a packed
  `spine.core.Status { error_case }`.
- Events are posted under the tenant of the request. A command is the origin of its event;
  an origin that fails validation falls back to the default one, as in the `EventBus`.
  The read events carry the actor context of the request in a field (see "Differences").
- A topic or subscription that no context serves is still handled in every context, as
  today; a problem there gets its error case, and nothing is stored.

## Plan

- [x] Events, `(internal_all)`, `java_package = "io.spine.system.server.event"`:
  - `command_log_events.proto`: `CommandPostingFailed { CommandId id; base.Error error }`;
    `ProcessingStatus.posting_error = 8`; `CommandLogProjection` subscribes.
  - new `query_events.proto`: `QueryValidationFailed { QueryId id; ValidationError }`,
    `UnsupportedQueryReceived { QueryId id; string type_url }`,
    `QueryExecutionFailed { QueryId id; base.Error error }`.
  - new `subscription_events.proto`: `TopicValidationFailed`, `UnsupportedTopicReceived`
    (`TopicId id` first), `SubscriptionValidationFailed`,
    `UnknownSubscriptionActivationRequested`, `UnknownSubscriptionCancellationRequested`
    (`SubscriptionId id` first), `SubscriptionServiceFailed { TopicId topic;
    SubscriptionId subscription; base.Error error }`.
- [x] `Stand`: `RequestValidator` throws `InvalidRequestException` (holds the
  `ValidationError`; "limit without ordering" becomes a `ConstraintViolation`) or the new
  `UnsupportedRequestException` (Kotlin). `InvalidQueryException`, `InvalidTopicException`,
  `InvalidSubscriptionException`, and the `Error`-building hooks are removed.
- [x] `ServiceDelegate`: the flow in the table; hooks `originOf()`, `failureEvent()`,
  `respondWithError()`; `reject()` records an event and answers. `handleInternal()` goes.
- [x] `CommandService`, `QueryService`, `SubscriptionService` on top of it. The no-context
  paths call the `Stand` directly. Cancelling a subscription no context has answers
  `UNSUPPORTED_REQUEST` (today: no response). `runThreadSafe()` answers `INTERNAL_ERROR`.
- [x] `transport.Statuses.failure(ErrorCase)` replaces `invalidArgumentWithCause()`.
  `StreamObservers.errorCaseOf()`; `fromStreamError()` and `MetadataConverter` deprecated
  (the server stops sending `spine-error-bin`); `TestClient` logs the error case.
- [x] Removed as unused: `UnsupportedCommandException`, `CommandException`,
  `MessageUnhandled`, `MessageError`, `UnknownEntityStateTypeException`, and the enums
  `QueryValidationError`, `TopicValidationError`, `SubscriptionValidationError` (only the
  `Stand` validators produced them). spine-ts uses them (its server sends them; `client-web`
  recreates a subscription on `UNKNOWN_SUBSCRIPTION`): a spine-ts follow-up.
- [x] System diagnostic events reach clients (also on `master`): the domain `Stand` shares
  its subscription registry with the System `Stand`, so a topic of `HandlerFailedUnexpectedly`
  or any other type of `diagnostic_events.proto` — the only System event file without
  `(internal_all)` — is subscribed and delivered. Verify with a spec, then mark the file
  `(internal_all)`, so the services refuse such a topic as an internal type.
- [x] Tests (Kotlin): an error-case spec per service (each row of the table, the tenant of
  the recorded event), `StatusesSpec`, `errorCaseOf()`, `CommandLogProjectionSpec`;
  the existing Java tests updated, `StatusesTest` replaced.
- [x] Version `.640`; `clean build dokkaGenerate` (server 2502 tests); `pre-pr`.
- [x] PR #1713, against `master` since #1711 was merged. It also carries the validation
  options of the slice-2 events (`c300eaddfe9`), pushed after #1711 was merged.

## Differences from the plan

- The read events do not use the request as their origin: the origin of a signal must be
  a command or an event (`server-otel`'s `SignalSpan` casts the root message ID to
  `SignalId`). Each read event carries `core.ActorContext actor_context` instead, and the
  System context is the origin.
- `QueryService` reads the target type with `Target.type()`: `Query.targetType()` throws
  `IllegalStateException` for a type unknown to the server, which then could not be told
  from a malformed one.
- A topic that no context serves is subscribed with a `Subscription` built without
  validation, so that the `Stand` of each context reports an invalid topic as
  `INVALID_REQUEST`. `Subscriptions.from()` threw `ValidationException`, which became
  `INTERNAL_ERROR`.
- Found while reviewing: an activation of a subscription that no context serves may
  activate it in some contexts and fail the call; and the cancellation of such a subscription
  calls `Stand.hasSubscription()` outside a tenant-aware operation. Filed as #1712 (`v2.0`),
  not changed here.
- The leak of System diagnostic events was confirmed by a spec before the fix: a client
  subscribed to `HandlerFailedUnexpectedly` through `SubscriptionService` received it with
  its `Error`. The same code is on `master`.

## Not in this slice

- `Status.error` and its helpers; client `Subscriptions` and `ServerErrorHandler` (slice 4).
