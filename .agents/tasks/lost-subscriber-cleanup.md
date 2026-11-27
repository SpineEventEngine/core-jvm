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

- [x] Bump the version: `.803` at first, `.811` after merging master at `.810`
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
- 2026-11-27 — A topic of `UserLoggedIn` alone is held by the users test context only, so
  the specs that need two holders add a `CTaskCreated` target. The users context lists no
  `UserLoggedIn` in `exposedEventTypes()` when the fixture is built, so the fixture finds its
  contexts by name.
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
- 2026-11-27 — Merged master (`.810`, from #1745 and #1746) and re-bumped to `.811`. PR #1749
  opened after `clean build dokkaGenerate` and a second review round with no must-fix.
- 2026-11-27 — Second-round suggestions applied: clearer docs and log texts,
  `ForwardingCallback` delegates to `GuardedObserver`, the fixture documents its direct
  executors and waits for the server to stop, test names claim only what they check.
  Two reviewers reported a double close of the stream on a failed activation. That cannot
  happen: `activate()` wraps the stream in a `GuardedObserver`, which drops a completion
  after the error.
- 2026-11-27 — PR review (Codex, Copilot), both valid: activating a subscription again left
  the stream of the earlier activation open, and a custom callback could get an update after
  `onCancelled()` (from a snapshot that `EventTap` took before the cancellation, or from the
  other targets when the callback cancels mid-delivery). `SubscriptionRecord` now activates,
  updates and cancels one at a time under its own monitor, with a `cancelled` flag, and tells
  a displaced callback that it serves the subscription no more. The registry calls the record
  only outside its lock, because a callback may call the registry.
- 2026-11-27 — `codecov/project` failed (−0.07%, threshold 0.05%). New specs cover a call whose
  cancellation handler cannot be set, and the rollback of a failed activation. The rollback is
  reached when the later activation displaces a callback that then cancels the subscription
  in the other context; with the old order of the rollback, that spec fails. The defensive
  `catch` and the debug message of `ActivationImpl.release()` stay uncovered.
- 2026-11-27 — A race that master has too: a client that subscribes and closes at once sends
  `Activate` and `Cancel` together. If the cancellation removes the subscription from a
  context after the activation finds it there, the activation answered INTERNAL and logged
  SEVERE (13 of 60 rounds on a real port). Now `Stand.activate()` reports a missing or
  cancelled record as `UnsupportedRequestException`. The activation then releases what it
  activated and completes the stream, and a cancellation skips a context that lost the
  subscription meanwhile. The probe shows 0 of 60 after the fix. The generic rollback of
  a failed activation is unreachable in single-threaded specs again.
- 2026-11-27 — Filed #1751 for a subscription that is never activated (owner: fix in 2.0).
