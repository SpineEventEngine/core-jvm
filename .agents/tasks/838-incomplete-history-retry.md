# Task: #838 follow-up — retry delivery on `IncompleteHistoryException`

Status: **implemented & tested** on branch `claude/determined-cerf-ecbbd6`.
Delete this file on merge to master.

Issue: https://github.com/SpineEventEngine/core-jvm/issues/838

## Context

The #838 fix (merged here from `claude/trusting-bardeen-56899a`) makes an incomplete,
eventually-consistent aggregate-history read throw `IncompleteHistoryException` from
`AggregateStorage.read` instead of persisting a corrupted snapshot. But the delivery layer
then **drops the signal**: `DeliveryMonitor.onReceptionFailure` defaults to `markDelivered()`,
so the message is never retried. The only existing retry hook,
`FailedReception.repeatDispatching()`, retries **immediately and synchronously**, which cannot
let eventual consistency settle and can spin.

This task adds an **opt-in** way to retry such a signal after a **backing-off delay**, without
blocking the single-threaded-per-shard delivery. Default behavior stays "drain" (decided with
the maintainer; see also the #1472 task notes).

## What changed

### Delivery core — make `onReceptionFailure` authoritative (the #1472 "Option B" clobber)

`LiveDeliveryStation.process()` used to mark **every** dispatched message `DELIVERED`
*after* the monitor already acted, so a monitor could never leave a message for a later retry.

- `Conveyor` — new `keepForRedelivery(InboxMessage)` / `isKeptForRetry(InboxMessageId)`; a
  deferred message keeps its `TO_DELIVER` status (no storage write needed).
- `LiveDeliveryStation.process()` — marks delivered only the messages **not** kept for retry;
  the delivered count reflects that. Non-retry path is unchanged (`delivered == toDispatch`),
  so `ReceptionFailureTest` and the rest of the delivery suite stay green. `MonitoringDispatcher`
  and `CatchUpStation` are untouched.
- `FailedReception.keepForRedelivery()` — new SPI action leaving the message `TO_DELIVER`.
- `DeliveryMonitor.shouldDeliverNow(InboxMessage)` — new default-`true` SPI predicate.
- `Delivery.deliverMessagesFrom()` — gates the end-of-run self re-trigger through
  `shouldDeliverNow`, so a deferred message does not hot-loop while it waits out its back-off.

### The opt-in monitor

- `io.spine.server.aggregate.IncompleteHistoryRetryMonitor` — recognizes
  `IncompleteHistoryException` via `error.getType()`; keeps the message for redelivery and
  schedules `Delivery.deliverMessagesFrom(index)` on a daemon `ScheduledExecutorService` after
  an exponential, capped back-off; drains after a configurable max attempts; overrides
  `shouldDeliverNow` to avoid spinning. Per-message state is a bounded Guava `Cache`; the monitor
  is `AutoCloseable`. Overridable `isRetryable(Error)` for other transient failures.
- `IncompleteHistoryException` Javadoc now points at the monitor as the built-in recovery.

## Tests

- `io.spine.server.aggregate.IncompleteHistoryRetryMonitorSpec` (Kotlin) — the default monitor
  recognizes the real `IncompleteHistoryException` type (and rejects others, incl. its
  superclass); exponential back-off grows and caps. Deterministic (injected clock/scheduler).
- `io.spine.server.delivery.IncompleteHistoryRetryTest` (Java, `@SlowTest`) — end-to-end through
  the real delivery pipeline: retries until the storage settles then handles the signal; gives up
  and drains after max attempts; does not block another entity in the same shard; ignores an
  unrelated (non-retryable) failure. Uses a `TransientFailure` stand-in thrown by
  `ReceptionistAggregate` (extended additively with per-id failure control + an applier-invocation
  counter); the real-type recognition is covered by the spec above.
- Regression: `./gradlew :server:test --tests "io.spine.server.delivery.*" --tests
  "io.spine.server.aggregate.*"` — 348 tests, 0 failures.

## Notes / follow-ups

- The mechanism simulates the failure in tests because in-memory storage is strongly consistent;
  it engages against real reads via the #838 throw once that ships.
- Back-off/attempt state is in-memory, per node — sufficient for eventual-consistency recovery
  (any node re-reading after the data settles succeeds; back-off just paces one node). A
  persistent, restart-safe variant (an `inbox.proto` `deliver_after` + attempt count and a
  time-gated redelivery station) is a possible future enhancement.
- One new `@SPI` method (`DeliveryMonitor.shouldDeliverNow`, default `true`) —
  backward compatible.
- **Catch-up scope (by design):** only `LiveDeliveryStation` honors `keepForRedelivery`;
  `CatchUpStation` marks its messages delivered regardless. This is intentional — catch-up is
  projection-only, so an aggregate `IncompleteHistoryException` does not arise on that path, and
  deferring a `TO_CATCH_UP` message would interfere with the catch-up state machine. Documented on
  `FailedReception.keepForRedelivery()` and the monitor. If a future transient failure needs retry
  during catch-up, mirror the `isKeptForRetry` filter into `CatchUpStation.dispatch`.
- **Monitor lifecycle:** `IncompleteHistoryRetryMonitor` is `AutoCloseable`, but the framework
  never calls `close()`. Its scheduler is a daemon `ScheduledThreadPoolExecutor` with
  `allowCoreThreadTimeOut(true)` (1-min keep-alive), so a monitor discarded without `close()`
  releases its thread when idle rather than leaking it. Documented on the class.
