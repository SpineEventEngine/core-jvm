---
slug: status-error-deprecation
branch: claude/status-error-deprecation-f599ba
owner: claude
status: in-progress
started: 2026-11-09
---

## Goal

`spine.core.Status` loses its `error` and `rejection` fields, with their helpers, and the Java
client hands an `ErrorCase` to `ServerErrorHandler` (today `BiConsumer<Message, Error>`).
Slice 4 of splitting #1705; after #1713 (slice 3, the services), which is merged. It does
the core-jvm part of #1706.

## What the server still sends

Nothing in production puts an `Error` into a `Status` any more:

- the `CommandBus` filters refuse with `reject(envelope, ErrorCase)` (slice 2);
- the services answer an `Ack` with `error_case`, or fail the call with a fixed gRPC
  status that carries the error case (slice 3);
- the `Stand` answers `Responses.ok()` in `QueryResponse`, `SubscriptionUpdate`, and
  the answer to a cancellation.

`Responses.errorWith(Error)` and `causedError(Message, Error)` are called only by tests.

## Decisions (2026-11-09)

- First `Status.error` was deprecated (as `rejection` already was); then the owner asked to
  remove both fields in this PR. Their names and numbers are `reserved` in `Status`, and
  `response.proto` no longer imports `spine/base/error.proto` and `spine/core/event.proto`.
- Removed with the fields: `Responses.errorWith(Error)`, `Responses.rejectedBecauseOf(Event)`,
  `ResponseMixin.error()`, `MessageIdExtensions.causedError(Message, Error)`, and
  `ResponseStatusSubject.isRejection()`; `CommandAckMonitor` reads only `ok` and
  `error_case`.
- `ResponseMixin.isError()` and `ResponseStatusSubject.isError()` stay: they check
  `ERROR_CASE`. `ResponseMixin.errorCase()` is added.
- `ServerErrorHandler extends BiConsumer<Message, ErrorCase>`. Lambdas compile unchanged;
  classes that implement it change the parameter type.
- `ErrorCase.toError()` (core, added by #1707 for the old handler) is removed with its spec:
  nothing uses it any more.
- `StreamObservers.fromStreamError()` and `MetadataConverter` are removed with their tests:
  they read the `spine-error-bin` trailer, which the server stopped sending in slice 3.
  Nothing else in the Spine repositories calls them.
- `CommandRequest`: an `ERROR_CASE` ack goes to the handler as is. An ack without a status
  fails with `IllegalStateException` naming the status, as before. A Codex review asked to
  cancel the subscriptions made for the command first; the owner dismissed it.
- `Subscriptions`, cancellation: a failed call that carries an error case
  (`StreamObservers.errorCaseOf()`) goes to the `ServerErrorHandler` with the subscription;
  other failures go to the streaming handler, as today. A `Response` with an error case
  goes to the `ServerErrorHandler` too; one without a status goes to the streaming handler
  as an `IllegalStateException`.
- Not changed: other failed calls stay where they go today.
  - Posting a command that fails with `INTERNAL_ERROR` keeps throwing
    `StatusRuntimeException`: the bus may have acknowledged the command, so reporting it
    as refused and cancelling the subscriptions made for it would be wrong.
  - `subscribe(topic)` and `read(query)` are blocking calls that return a value; they keep
    throwing `StatusRuntimeException`, as before slice 3. The caller reads the error case
    with `StreamObservers.errorCaseOf()`.
  - A failed activation goes to the streaming handler: the stream of updates failed.
- Client docs say what the client gets, never how the server keeps the details.

## Plan

- [x] `response.proto`: deprecate `error`; fix the `Response` doc.
- [x] Remove `Status.error` and `Status.rejection` (names and numbers `reserved`), the API
  listed above, and the tests of it; rewrite `docs/migration/command-filter-rejection.md`
  for `.620` and `.660` (new title, a section on moving off the fields).
- [x] `LoggingServerErrorHandlerSpec` covers the line Codecov reported.
- [x] Core: deprecate `Responses.errorWith(Error)` and `ResponseMixin.error()`;
  `ResponseMixin.isError()` checks `ERROR_CASE`; add `ResponseMixin.errorCase()`;
  `git rm` `ErrorCases.kt` and `ErrorCasesSpec.kt`.
- [x] Client: remove `StreamObservers.fromStreamError()`, `MetadataConverter`, and their tests.
- [x] Server: deprecate `causedError(Message, Error)`; fix the `Bus.post()` doc, which says
  a refused message gets an `Error` status and a rejected one a rejection status.
- [x] Client: `ServerErrorHandler`, `LoggingServerErrorHandler`, `CommandRequest`,
  `Subscriptions`; docs of `onServerError()` in `Client.Builder` and `ClientRequestBase`.
- [x] `server-testlib`: `ResponseStatusSubject.isError()` checks `ERROR_CASE`.
- [x] Docs: `command_service.proto` stops listing `Status.error`; one sentence in
  `docs/migration/command-filter-rejection.md` (section 2) that the handler receives
  the `ErrorCase` itself since `.660`.
- [x] Tests (new ones in Kotlin):
  - `CommandRequestErrorCaseSpec`: the handler gets `INVALID_REQUEST` itself.
  - `CommandRequestUnexpectedStatusSpec`: only the ack without a status is left.
  - New `SubscriptionCancellationErrorCaseSpec`: cancelling a subscription the server
    does not know passes `UNSUPPORTED_REQUEST` and the subscription to the handler; with
    a stub service, a failure without an error case goes to the streaming handler, and
    a `Response` with an error case goes to the `ServerErrorHandler`.
  - Updated: `CommandRequestTest`, `ClientErrorHandlersTest`, `ResponsesTest`,
    `SignalExtrasSpec`, `MessageIdExtensionsSpec`, `CommandAckMonitorSpec`,
    `ResponseStatusSubjectTest`, `QueryResultSubjectTest`, `GivenResponseStatus`.
- [x] Version `.640` → `.660` (breaking: public client API). First `.650`; re-bumped because
  #1716, which takes `.650`, merges first.
- [x] `./gradlew clean build dokkaGenerate`; read the test counts (core 190, client 185,
  server 2519 + 1 skipped, server-testlib 158); `pre-pr`.
- [x] Draft the new text of #1706 for the owner (its steps 1, 3, and 4 are out of date).

## Differences from the plan

- Two client docs pointed at the wrong behavior and are fixed: `CommandRequest.post()` said
  a refused command goes to the streaming error handler (it goes to the server error
  handler), and `Subscriptions.cancel()` now says where a refused cancellation goes.
- `ResponseStatusSubject` gets no new check for a specific error case: query responses never
  carry one, since a failed query fails the call.

## Not in this slice

- spine-ts and the Dart client drop the fields from their copies of `response.proto`
  (the last step of #1706).
