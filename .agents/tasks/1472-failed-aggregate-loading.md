# Task: #1472 — Handle failed aggregate loading during message delivery

Status: **regression test added & passing** (branch `claude/optimistic-kirch-89f0d3`);
scope chosen: **A — regression test + recommend closing the issue**.
Delete this file on merge to master.

## Outcome

Added a deterministic (synchronous) regression test proving the current behavior
matches #1472's *expected* behavior:

- `server/src/test/java/io/spine/server/delivery/FailedAggregateLoadingTest.java` —
  a "poison" `ReceptionistAggregate` whose applier throws receives two distinct
  commands; a second entity in the same single shard then receives one. Asserts:
  no `CannotDispatchDuplicateCommand`, the other entity is handled
  (`howManyCmdsHandled == 1`), the reception failure is observed, and the inbox
  drains (no endless re-delivery). A `handlerFailureEvents().hasSize(2)` assertion
  proves the `DiagnosticMonitor` is live on the same `EntityLifecycle.onDispatchingFailed`
  sink that emits duplicate diagnostics — so the empty `duplicateCommandEvents()` is
  a real absence, not a missed subscription.
- `server/src/testFixtures/java/io/spine/server/delivery/given/ReceptionFailureTestEnv.java` —
  added `blackBoxWith(DiagnosticMonitor)` (registers the monitor via the proven
  `internalAccess().registerEventDispatcher` path) and `configureSynchronousDelivery`.

`./gradlew :server:test` — `FailedAggregateLoadingTest` (1) and `ReceptionFailureTest`
(2) all green.

**Recommendation:** close #1472 as resolved by the Conveyor/Station + `FailedReception`
redesign, with this test as the regression guard. The residual SPI limitation (option B
below) can be a separate follow-up issue if the team wants custom monitors to leave a
failed message for later.

Issue: https://github.com/SpineEventEngine/core-jvm/issues/1472

## The reported bug (filed against older code)

When an `Aggregate` cannot be loaded/applied (e.g. an `@Apply` method throws),
the reporter observed:

1. Commands/events targeting the corrupted aggregate stay in `InboxStorage`
   forever and are re-delivered on every shard iteration.
2. A spurious `CannotDispatchDuplicateCommand` is raised for a *second, distinct*
   command to the same aggregate.
3. A command to *another entity* sharing the same `ShardIndex` is not handled.

Expected: the aggregate still fails, but **no** duplicate diagnostic is raised and
the other entity's command **is** handled.

## What the current code actually does (verified by reading source)

The delivery subsystem was rewritten around a `Conveyor` + `Station` pipeline
(© 2022) and gained the `FailedReception` / `DeliveryMonitor.onReceptionFailure`
SPI (© 2023). Against *current* `master`:

- **Not stuck forever.** A failed dispatch produces an error `DispatchOutcome`
  (`AbstractMessageEndpoint.dispatchTo` catches everything). `MonitoringDispatcher`
  (`TargetDelivery.java:158`) calls `monitor.onReceptionFailure(...)`; the default
  (`DeliveryMonitor.java:119-121`) returns `reception.markDelivered()`. The message
  becomes `DELIVERED` and `CleanupStation` (`CleanupStation.java:53-57`) **removes**
  it. Confirmed by the existing `ReceptionFailureTest` which asserts
  `assertInboxEmpty()` after an applier throws.
- **Distinct second command is not a duplicate.** Delivery-level dedup keys on
  `DispatchingId = (signalId, inboxId)` (`DispatchingId.java:44-47`); two distinct
  commands have distinct signal ids. The aggregate `IdempotencyGuard`
  (`Aggregate.java:267`, `IdempotencyGuard.java`) keys on *committed history*, and a
  failed `@Apply` rolls back and is never stored (`AggregateEndpoint.java:73-79`
  only stores on success). So neither dedup path flags command #2.
- **Other entity is isolated.** Dispatch is per-message
  (`MonitoringDispatcher.dispatch`) and per-target-type-segment
  (`GroupByTargetAndDeliver.executeFor`, catches per segment). With the default
  monitor no error propagates, so `launch()`'s `throwIfAny()` (`Delivery.java:596`)
  never fires and the batch flush is not aborted.

**Conclusion:** the three originally reported symptoms appear resolved by the
redesign. The remaining, defensible concern is that the **default silently drops**
a permanently-failing message (`markDelivered`), surfacing it only via the
`onDispatchingFailed` / `onCorruptedState` diagnostics.

## Residual defect candidate (production)

`LiveDeliveryStation.process()` (`LiveDeliveryStation.java:104-106`) unconditionally
marks **every** dispatched message `DELIVERED` at line 106 — *after*
`action.executeFor(...)` has already let the monitor act. This means a custom
`DeliveryMonitor.onReceptionFailure` **cannot** choose to leave a failed message
`TO_DELIVER` (for a later retry) or move it to a dead-letter state: line 106
clobbers the decision. Only `markDelivered()` (redundant) and `repeatDispatching()`
(synchronous retry-until-success) work today. Making `onReceptionFailure`
authoritative requires `DeliveryAction.executeFor` to report *which* messages
succeeded, so line 106 marks only those — a change threaded through
`DeliveryAction` → `GroupByTargetAndDeliver` → `TargetDelivery` → `MonitoringDispatcher`.

## Proposed options (scope decision)

- **A (recommended, low risk): regression test + close.** Add a deterministic,
  synchronous test reproducing #1472 (applier throws for two commands to one
  aggregate; a command to another entity in the same single shard). Assert: no
  `CannotDispatchDuplicateCommand`, the other entity is handled, the inbox drains,
  the failing aggregate has no state. Locks in the fixed behavior; recommend
  closing the issue as verified-fixed. Test-only.
- **B: A + make `onReceptionFailure` authoritative.** Also fix the line-106
  clobber so custom monitors can implement retry-later / dead-letter. Core,
  multi-file semantics change; default behavior kept (still `markDelivered`).
- **C: A + a first-class terminal/dead-letter status** for permanently-failing
  messages (needs an `inbox.proto` status + migration). Largest.

## Test infrastructure (found)

- Throwing aggregate: `ReceptionistAggregate` (`makeApplierFail()`/`makeApplierPass()`).
- `ReceptionFailureTest` / `ReceptionFailureTestEnv` (monitors, `blackBox()`,
  `inboxMessages()`), `AbstractDeliveryTest`, `FixedShardStrategy(1)`,
  synchronous `LocalDispatchingObserver`, `InboxContents.get()`,
  `DiagnosticMonitor` (captures `CannotDispatchDuplicateCommand`).
- Open implementation risk: confirming a `DiagnosticMonitor` registered on the
  BlackBox context actually captures delivery-path diagnostics (needs a positive
  control) so the "no duplicate" assertion is meaningful rather than vacuous.
