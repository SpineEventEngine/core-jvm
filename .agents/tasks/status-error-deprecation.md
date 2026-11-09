---
slug: status-error-deprecation
branch: claude/status-error-deprecation-f599ba
owner: claude
status: in-progress
started: 2026-11-09
---

## Goal

`spine.core.Status.error` is deprecated with its helpers, and the Java client hands
an `ErrorCase` to `ServerErrorHandler` (today `BiConsumer<Message, Error>`). Slice 4 of
splitting #1705; after #1713 (slice 3, the services), which is merged.

## What the server still sends

Nothing in production puts an `Error` into a `Status` any more:

- the `CommandBus` filters refuse with `reject(envelope, ErrorCase)` (slice 2);
- the services answer an `Ack` with `error_case`, or fail the call with a fixed gRPC
  status that carries the error case (slice 3);
- the `Stand` answers `Responses.ok()` in `QueryResponse`, `SubscriptionUpdate`, and
  the answer to a cancellation.

`Responses.errorWith(Error)` and `causedError(Message, Error)` are called only by tests.
An application `BusFilter` can still build an `Ack` with `Status.error` by hand: after this
slice that is deprecated API, and the Java client treats such an `Ack` as an invalid status.

## Decisions (2026-11-09)

- `Status.error` gets `[deprecated = true]` and "Deprecated: the field is going to be
  removed.", as `rejection`. The doc of `Response` stops listing `error`.
- Deprecated, with "`Status.error`, which this method sets/reads, is going to be removed.":
  `Responses.errorWith(Error)`, `ResponseMixin.error()`,
  `MessageIdExtensions.causedError(Message, Error)`.
- `ResponseMixin.isError()` and `ResponseStatusSubject.isError()` are not deprecated: they
  check `ERROR_CASE` from now on, so they outlive the field. `ResponseMixin.errorCase()` is
  added. A response with the deprecated `error` is not an error for them.
- `ServerErrorHandler extends BiConsumer<Message, ErrorCase>`. Lambdas compile unchanged;
  classes that implement it change the parameter type.
- `ErrorCase.toError()` (core, added by #1707 for the old handler) is removed with its spec:
  nothing uses it any more.
- `StreamObservers.fromStreamError()` and `MetadataConverter` are removed with their tests:
  they read the `spine-error-bin` trailer, which the server stopped sending in slice 3.
  Nothing else in the Spine repositories calls them.
- `CommandRequest`: an `ERROR_CASE` ack goes to the handler as is. An ack with
  the deprecated `ERROR` joins `REJECTION` and `STATUS_NOT_SET`: `IllegalStateException`
  naming the status.
- `Subscriptions`, cancellation: a failed call that carries an error case
  (`StreamObservers.errorCaseOf()`) goes to the `ServerErrorHandler` with the subscription;
  other failures go to the streaming handler, as today. A `Response` with an error case
  goes to the `ServerErrorHandler` too.
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
  - `CommandRequestUnexpectedStatusSpec`: an ack with the deprecated `error` fails naming
    `ERROR`.
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

- Removing `Status.error` and `Status.rejection` (#1706).
- spine-ts and the Dart client.
- What the server does with an `Ack` that an application filter builds with `Status.error`
  (today: passed to the client; `CommandAckMonitor` treats it as a refusal).
