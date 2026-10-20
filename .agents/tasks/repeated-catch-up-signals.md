---
slug: repeated-catch-up-signals
branch: repeated-catch-up-signals
owner: claude
status: in-review
started: 2026-10-20
---

# A repeated catch-up signal moves the process back

The follow-ups of #1692 (`duplicate-entity-prepared-for-catch-up`), as listed in its
description and in "Noticed along the way" of its task file. That file is deleted here
(see `git show 81c6387bb97:.agents/tasks/duplicate-entity-prepared-for-catch-up.md`).

## Status

- [x] The handlers read at `81c6387bb97` (`master`, #1692 merged)
- [x] Plan approved on 2026-10-20
- [x] Test fixtures: `useDelivery()` takes an observer and a hook for the delivery settings
- [x] `CatchUpJobsPerPageIgTest` and the suite of #1692 moved onto `AbstractCatchUpIgTest`
- [x] The status guards, with a case for each repeated signal — revised for crash safety,
      see "Deviation from the plan"
- [x] Verification and reviews — see "Verification"
- [x] Version bump, dependency reports, PR — authorized on 2026-10-20. The version is
      `.575`: #1693 and #1694 hold `.573` and `.574`

## Problem

#1692 made `handle(EntityPreparedForCatchUp)` act only while `STARTED`. The other handlers
that move the process forward still acted in any status. A signal delivered again after
a crash between the dispatch and `conveyor.flushTo()` in `Delivery.launch()` — the delivery
is at-least-once, and the cache of delivered messages lives in memory — moved the job back.

Line numbers refer to `81c6387bb97`.

- `handle(HistoryFullyRecalled)` (:504-512) moves a job in any status to `FINALIZING` and
  runs the finalization: it reads past `when_last_read` with no upper bound and dispatches
  what it reads. After `COMPLETED`, the job goes `COMPLETED → FINALIZING → COMPLETED`, and
  the live events that came after the catch-up reach the projections once more.
- `handle(HistoryEventsRecalled)` (:454-457) reads the history in any status. After
  `COMPLETED`, it reads the live events that came after the catch-up and dispatches them
  again; then its `HistoryFullyRecalled` finalizes the job once more, as above.
- `handle(CatchUpRequested)` (:373-394) sets `STARTED` and sends `CatchUpStarted` again in
  any status: the targets are reset, and the job runs from the start once more.

Harmless on a repeat, left as they are: `handle(LiveEventsPickedUp)` and
`on(CatchUpCompleted)` only request the processing of shards; `handle(ShardProcessed)`
ignores a `COMPLETED` job and adds a shard to `finalized_shard` at most once.

The test side: `AbstractCatchUpIgTest.useDelivery()` (#1689) accepted only a strategy. So
`DuplicateEntityPreparedIgTest` (#1692) and `CatchUpJobsPerPageIgTest` (#1686) each kept their
own repository, failure list, watchdog, `emitHistory()`, `totals()`, and `undelivered()`.

## As implemented

### The status guards — `CatchUpProcess.java`

A signal that comes once the process has moved past the step it starts is ignored
(`NoReaction`):

- `handle(CatchUpRequested)` acts while not started or `STARTED`; the return type is
  unchanged.
- `handle(EntityPreparedForCatchUp)` acts while `STARTED` (#1692, unchanged).
- `handle(HistoryEventsRecalled)` acts while `IN_PROGRESS`; it returns
  `EitherOf3<HistoryEventsRecalled, HistoryFullyRecalled, NoReaction>`.
- `handle(HistoryFullyRecalled)` acts while `IN_PROGRESS` or `FINALIZING`; it returns
  `EitherOf2<LiveEventsPickedUp, NoReaction>`. While `FINALIZING`, it runs the finalization
  again, as before.

So a repeat can no longer move a job back. `recallMoreEvents()` returns the `EitherOf3` of
the handlers, so `startRecalling()` lost its conversion. The Javadoc of each guarded
handler says what it ignores and why; the class-level doc says the status only moves
forward.

### Deviation from the plan

The plan had `CatchUpRequested` act only on a new job and `HistoryFullyRecalled` only while
`IN_PROGRESS`. `spine-code-review` found that this breaks crash recovery. Both handlers
store their new status in the middle (`flushState()`), before their work: `STARTED` before
the start signals are sent, `FINALIZING` before the finalization. After a crash in between,
the signal is delivered again and finds the new status. The strict guards would ignore it:
the job would stay `STARTED` or `FINALIZING` for good, its targets held by `Delivery`, and
a new catch-up of them refused. The `!= FINALIZING` check in `handle(HistoryFullyRecalled)`
was there for this, not only to skip a second `flushState()`, as the plan said.

So each handler that stores its status mid-way also acts in that status. Checked in
`AbstractStatefulReactor.EventEndpoint`: the emitted events are posted before the final
`flushState()`, so the other handlers have no such window. The cost: a repeat while
`STARTED` sends the start signals again, and one while `FINALIZING` runs the finalization
again. Both are harmless and covered by a case.

### Test fixtures — `AbstractCatchUpIgTest`

- `useDelivery(strategy, observer = LocalDispatchingObserver(), configure = {})` returns
  the `Delivery`. `configure: (DeliveryBuilder) -> Unit` sets the storages, the page size,
  or the deduplication window; the monitor is set after it.
- `configure` takes the builder as a parameter, not as a receiver. The first version,
  `DeliveryBuilder.() -> Unit`, let the builder's synthetic `inboxStorage` (its throwing
  getter) hide the test's field of the same name: every case failed in `@BeforeEach`.
- The monitor is a watchdog: it records the receptor failures, and once 30 s have passed
  since `useDelivery()`, it stops each run and records the stop in `failures`; the observer
  is no longer called. It replaces the time-based copy of #1692 and the page-count copy of
  `CatchUpJobsPerPageIgTest`.
- New: `jobStatuses(stateType)`, `waitPastTurbulence()`, `inboxContents()`.

### `CatchUpJobsPerPageIgTest`

On the base. It keeps `useSingleShard()` (page size, window, storage), `HookedObserver` with
only the hooks, and `checkRunsWentOn()`, now one check of `failures`. The six cases stay.
A livelock is stopped after 30 s rather than after 1000 pages of a run.

### `RepeatedCatchUpSignalIgTest` (was `DuplicateEntityPreparedIgTest`)

`git mv`, then onto the base. It keeps the recording storages, `OnFirstSignal`, and
`HoldingExecutor`. A repeat is a new event with the message of the original: a report is
posted the way `CatchUpEndpoint` posts it, any other signal the way `CatchUpStarter` posts
`CatchUpRequested` (`CatchUpEventFactory` and the event bus of the context). The test records
each repeat, and `assertCompletedOnce()` checks that exactly one was made and reached
an inbox, the reports, the status trail, `instances_to_clear`, the number of finalizations
(`LiveEventsPickedUp`), the totals, and the undelivered messages.

| Group                      | Case                                                | Finalizations |
|----------------------------|-----------------------------------------------------|--------------:|
| `EntityPreparedForCatchUp` | the four cases of #1692                             |             1 |
| `HistoryEventsRecalled`    | a second chain while `IN_PROGRESS`                  |             2 |
|                            | ignored while `FINALIZING`, after `COMPLETED`       |             1 |
| `HistoryFullyRecalled`     | the finalization runs again while `FINALIZING`      |             2 |
|                            | ignored after `COMPLETED`                           |             1 |
| `CatchUpRequested`         | the start signals sent again while `STARTED`        |             1 |
|                            | ignored in `IN_PROGRESS`, `FINALIZING`, `COMPLETED` |             1 |

## Verification

- [x] The strict guards (the plan): the suite 12 of 12, `CatchUpJobsPerPageIgTest` 6 of 6.
      Without the guards (`master`'s `CatchUpProcess` in a scratch copy): 8 of 12 failed —
      every case of the three new groups; the four of #1692 passed. The trail went
      `COMPLETED → FINALIZING → COMPLETED` after `COMPLETED`; `CatchUpRequested` reset each
      target again (4 reports); the other cases ran the finalization twice.
- [x] The crash-safe guards: the suite 13 of 13, `CatchUpJobsPerPageIgTest` 6 of 6.
      Without them: 6 of 13 failed — exactly the cases that ignore a repeat. The three that
      describe a harmless repeat, and the four of #1692, passed either way. Counts from
      the XML reports, `--no-build-cache`.
- [x] `./gradlew check dokkaGenerate --no-build-cache`: passed; no task up to date or from
      the cache. `:server:test` 2148 tests (2139 + 9), 0 failed, 1 skipped
      (`EventReactionRoutingSpec`, pre-existing), 583 XML reports. Other modules: `core` 180,
      `client` 189, `server-testlib` 159, `core-testlib` 25, `server-otel` 22,
      `client-testlib` 2 — none failed. `detekt`, `checkstyleMain`, `pmdMain`,
      `dokkaGenerate` ran for each module.
- [x] Reviews on the strict version: `review-docs`, `kotlin-engineer`, and
      `spine-code-review` approved with changes; applied, the crash-safety finding included.
      `spine-code-review` re-checked the crash-safe guards: approved.
- [x] Pre-PR reviews of the committed branch: `spine-code-review` approved; `kotlin-engineer`
      and `review-docs` approved with changes — a named argument and wording, applied.
- IntelliJ IDEA inspections were not run.

## Coordination

- PR #1693 (a `CatchUpStation` guard) is open at `.573`, and #1694 (the routing of
  catch-up messages to the process of their repository) at `.574`; this branch is at `.575`.
  Overlap with #1693: at most an append to `.agents/memory/MEMORY.md`. #1694 also edits
  `CatchUpProcess` (routing, `canDispatch()`), not these handlers; a trial three-way merge
  is clean. Whichever of #1694 and this branch lands second merges `master` and runs
  `:server:test` again.
- Its new `KeptStartSignalIgTest` keeps its own holding executor, recording storages, and
  failure recorder. Once both land, it can move onto `useDelivery(strategy, observer,
  configure)`.

## Noticed along the way

Not fixed here; the same on `master`.

- **A third status stored before the work is done.** `completeProcess()` stores `COMPLETED`
  before `CatchUpCompleted` is posted. After a crash in between, the `ShardProcessed`
  delivered again is ignored, as the job is `COMPLETED`, and `on(CatchUpCompleted)` never
  requests the second round of shard processing. The events held for the catch-up then wait
  for the next delivery run of their shards.
- **A report before the repeated request.** After a crash between storing `STARTED` and
  the end of `handle(CatchUpRequested)`, the job is `STARTED` with `instances_to_clear` at 0.
  A report counted before the request is delivered again would start the history early.
  It cannot happen as long as the process reads its shard in the order of the messages:
  a report exists only because of the request, so it comes after it.
