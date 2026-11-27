---
slug: lost-subscriber-cleanup
branch: claude/issue-1739-analysis-ffbc02
owner: claude
status: in-progress
started: 2026-11-27
---

## Goal

Issue #1739. A client that goes away without `Cancel` no longer leaves its subscription in
the `Stand`s that hold it. A `Cancel` ends the `Activate` stream. The gRPC server notices a
client that disappears silently within about a minute. A failed delivery is logged with
the ID of the subscription.

## Context

A probe on master `0627cb56796` (in-process server, the `users` test context) showed:

- After `channel.shutdownNow()` without `Client.close()`, `Stand.hasSubscription()` stays
  `true`. Each matching update logs `SEVERE` from `UpdateHandler.deliverUpdate()` with an empty
  message and `CANCELLED: call already cancelled`.
- After a `Cancel`, the record is gone, but the `Activate` stream is never completed.
  `Client.close()` with the default timeout took 5005 ms, and `isOpen()` stayed `true`.
- A subscription that is never activated stays too. No call carries it, so this change does
  not cover it.

gRPC 1.85: `setOnCancelHandler()` works only while the service method runs. The handler runs
on the call's serializing executor after `activate()` returns. With a handler set, `onNext()`
after cancellation is dropped instead of throwing. Netty's server keepalive is two hours by
default.

## Plan

- [x] Bump the version to `.803` (`.802` is taken by an open PR)
- [x] A: `SubscriptionService.activate()` sets an on-cancel handler that releases the
      subscription in the contexts that hold it, only where this call's callback is active
- [x] B: `Stand.cancel()` tells the activation callback, which completes the stream
- [x] Name the subscription in the `UpdateHandler` delivery log
- [x] C: `GrpcContainer` sets keepalive (1 min / 20 s) on port-based servers, before
      `ConfigureServer`
- [x] Docs: `subscription_service.proto`, `SubscriptionService`, `Stand`, `SubscriptionCallback`,
      `GrpcContainer`
- [x] Specs for A, B, C, the log, and `Client.close()`
- [x] `clean build`, `dokkaGenerate`, reviewers

## Log

- 2026-11-27 — Plan written after the probe. One PR, a commit per part.
- 2026-11-27 — B made the rollback of a failed activation complete the stream before the
  failure reached it, and `GuardedObserver` then dropped the error. `ActivationImpl.serveIn()`
  now reports the failure first and cancels in a `finally`. No spec covers it: the holders are
  found right before the activation, so one of them cannot be made to fail.
- 2026-11-27 — The users test context emits `UserLoggedIn` from a process manager, which is not
  among the `Stand`'s exposed event types. A topic of it is held by every context.
- 2026-11-27 — Mutation checks: without the on-cancel hook, 3 of 4 lost-client specs fail;
  without the callback check, the re-activation spec fails; without `notifyCancelled()`, all
  the B specs and `SubscriptionServiceTest.cancelSubscription` fail.
- 2026-11-27 — The keepalive lives in an internal Kotlin object: `GrpcContainer` has no logger,
  and `WithLogging` would add `logger()` to its public API. In-process builders throw
  `UnsupportedOperationException` for keepalive, which the spec uses.
- 2026-11-27 — `clean build --no-build-cache` and `dokkaGenerate` passed (server 2744, client
  206, core 174 tests). Reviews (Spine rules, Kotlin, docs) had no must-fix. Taken:
  `ForwardingCallback` completes once and drops later updates (the public `forwardingTo()`
  has no `GuardedObserver` around it); a failing `onCancelled()` is logged and the
  cancellation is still answered; read-only fixture state; `waitFor` with a description; a
  second, live subscription to prove an update was delivered; docs wording. Kept: the rollback
  of a failed activation cancels in every holder. A `release()` there would leave
  unactivated records that could never be activated again.
- 2026-11-27 — After the review fixes, `build` and `dokkaGenerate` passed (server 2748 tests).
