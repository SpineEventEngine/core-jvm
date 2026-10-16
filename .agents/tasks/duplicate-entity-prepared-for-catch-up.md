---
slug: duplicate-entity-prepared-for-catch-up
branch: claude/nostalgic-raman-81fde4
owner: claude
status: in-review
started: 2026-10-07
---

# A repeated `EntityPreparedForCatchUp` starts the catch-up over

Issue: none filed. Option 2 ("count prepared instances, not events") of
`catch-up-started-delivered-twice` (branch `claude/nostalgic-tesla-e45bb5`, uncommitted).

## Status

- [x] The path verified by reading, at `7b64d415975`
- [x] Reproduced with a real catch-up flow, system events posted in parallel —
      see "Reproduction"
- [x] Candidate fixes tried in a scratch copy — see "Options"
- [x] Plan approved on 2026-10-07: option A
- [x] Fix and tests — see "As implemented"
- [x] Verification and reviews — see "Verification"
- [x] `master` merged on 2026-10-16 (`1ebc52b9193`), the change re-checked — see
      "After merging `master`"
- [x] Version bump, dependency reports, PR — authorized on 2026-10-16

## Problem

`CatchUpProcess.handle(EntityPreparedForCatchUp)` counts events, not instances, and acts in
any status of the job. Each report past the expected count starts reading the history over:
the job goes back to `IN_PROGRESS` from `FINALIZING` or `COMPLETED`, and runs one more
finalization handshake. Once the job is `COMPLETED`, the extra round dispatches the events
that came after the catch-up once more, to projections that are not reset: those events are
applied twice.

## What the code says

Line numbers refer to `7b64d415975` (`master`).

1. **The handler acts in any status.** `handle(EntityPreparedForCatchUp)` (:407-423)
   decrements `instances_to_clear` per event (:410-411). Unless the result is still positive
   (:412), it sets `IN_PROGRESS` (:415) and calls `recallMoreEvents()` (:417).
2. **The extra round reads past the catch-up.** `recallMoreEvents()` (:458-474) reads
   the history after `when_last_read`, up to the turbulence period, and dispatches it
   to the targets as catch-up messages. After `COMPLETED`, `when_last_read` is the last event
   read by `runFinalization()` (:493-502), so the read returns the events that came after
   the catch-up. They were delivered live already.
3. **Nothing drops the second copy.** Only `CatchUpStarted` resets a target
   (`CatchUpEndpoint.onCatchUpStarted()`, `projection/`, :85-91), and the extra round sends
   none. Projections do not deduplicate events. `CatchUpStation.inProgress()` (:211-222) and
   `completedWith()` (:268-281) check duplicates only within the page they process; unlike
   `LiveDeliveryStation`, they do not consult the messages delivered before.
4. **Only the stored job can tell a duplicate.** `AbstractStatefulReactor.EventEndpoint`
   loads the job, runs the handler, then stores the job (`dispatchTo()`).
5. **Where a repeated report comes from:**
   - *The same event, delivered again.* `LiveDeliveryStation` drops a message whose
     `DispatchingId` (signal and inbox) is in its page or in `DeliveredMessagesCache`. The
     cache lives in memory, holds at most 1000 entries, and is filled by
     `Conveyor.markDelivered()` before `flushTo()`. So, within one JVM, a copy of a recently
     processed event is dropped. After a crash between the dispatch and
     `conveyor.flushTo()` (`Delivery.launch()`, :590-603), the cache is gone, the job is
     stored (step 4), and the message is still `TO_DELIVER`: it is delivered again.
   - *A new event for the same instance.* An instance reports each time it is reset, e.g.
     by the repeated `CatchUpStarted` of `catch-up-started-delivered-twice`. The event ID
     differs, so no deduplication applies.

## Reproduction

`DuplicateEntityPreparedIgTest` (new, `@SlowTest`):

- `CounterView` in `BlackBox`; a single-shard `Delivery` with the default page sizes and
  no deduplication window.
- System events are posted in parallel, through an executor that runs each task in
  the calling thread, or holds it (as in `RepeatedCatchUpResetIgTest`).
- Recorded: the failures of the delivery runs and of the receptors
  (`DeliveryMonitor.onReceptionFailure()`), each state of the job written to
  `CatchUpStorage`, each message sent to an inbox. A watchdog stops the delivery after 30 s.
- History: 10 `NumberAdded` per target to `first` and `second`, posted live, then a 1 s
  pause. The catch-up of both weighs each event 10: 100 per target.
- The duplicate is posted the way `CatchUpEndpoint` posts the original:
  `repository.lifecycleOf(id).onEntityPreparedForCatchUp(job)`, a system event with an ID
  of its own. It comes at four points:
  - while `STARTED`: the reports are held; one is released; the duplicate of that one is
    posted at once; then the other report is released;
  - while `IN_PROGRESS` and while `FINALIZING`: a test reactor posts it on the first
    `HistoryEventsRecalled`, or on `LiveEventsPickedUp`, which the process emits in that
    status;
  - after `COMPLETED`: posted by the test, after a live event to `first` and a pause longer
    than the turbulence period.
- No frozen clock, no shared timestamps.

At `7b64d415975`: 4 tests, 4 failed (XML report); no failure of a run or a receptor; the
watchdog did not fire.

| The duplicate arrives | Status trail of the job                                                           | `instances_to_clear` | `HistoryFullyRecalled` | `ShardProcessingRequested` | Totals                     |
|-----------------------|-----------------------------------------------------------------------------------|---------------------:|-----------------------:|---------------------------:|----------------------------|
| while `STARTED`       | `STARTED, IN_PROGRESS, FINALIZING, COMPLETED, IN_PROGRESS, FINALIZING, COMPLETED` |                   −1 |                      2 |                          4 | 100, 100                   |
| while `IN_PROGRESS`   | `STARTED, IN_PROGRESS, FINALIZING, COMPLETED`                                     |                   −1 |                      2 |                          3 | 100, 100                   |
| while `FINALIZING`    | `STARTED, IN_PROGRESS, FINALIZING, IN_PROGRESS, FINALIZING, COMPLETED`            |                   −1 |                      2 |                          4 | 100, 100                   |
| after `COMPLETED`     | `STARTED, IN_PROGRESS, FINALIZING, COMPLETED, IN_PROGRESS, FINALIZING, COMPLETED` |                   −1 |                      2 |                          4 | `first` 120 instead of 110 |

Expected in each case: the trail `STARTED, IN_PROGRESS, FINALIZING, COMPLETED`, the count
at 0, one `HistoryFullyRecalled`, and the totals 100 and 100 — 110 for `first` in the last
case.

- **While `STARTED`:** the duplicate brought the count to zero while the other report was
  still held; the job completed within the delivery of the duplicate. The held report then
  started the second round.
- **While `IN_PROGRESS`:** a second chain of history reads ran beside the first one; both
  ended in `HistoryFullyRecalled`, the second one while `FINALIZING`, which ran
  the finalization once more.
- **After `COMPLETED`:** the second round read the live event that came after
  the catch-up and dispatched it to `first` again — the event was applied twice.

Each report past the count costs one more reading of the history and one more finalization
handshake. In the run of `catch-up-started-delivered-twice`, 849 of them made the handshakes
outnumber the page size, and the delivery run livelocked.

## Options

Both were tried in a scratch copy of `7b64d415975` against the four cases above.

### A. Count the reports only while `STARTED` (recommended)

```java
handle(EntityPreparedForCatchUp event) {
    if (builder().getStatus() != STARTED) {
        return EitherOf3.withC(noReaction());
    }
    // ...as before
```

- One condition in one production file. No change to the stored state or its schema;
  jobs stored before keep working.
- The job leaves `STARTED` once. One chain reads the history, one finalization runs.
  As far as this event goes, the status only moves forward, and the count never goes
  below zero.
- Closes each cascade reproduced, the livelock of `catch-up-started-delivered-twice`
  included: there, the extra reports came after the count had reached zero.
- **Leaves:** a duplicate *while* `STARTED` still counts. The count may reach zero before
  every instance has reported, and the history is read early.
  - On one node, that is safe. A target that has not reported yet is either reset already,
    its report on the way, or has its start signal still in its inbox, written before
    the history. In its shard, the signal comes first, and `CatchUpStation` puts
    `CatchUpStarted` first within a batch. So the target is reset before its history
    arrives.
  - The count gave more than that: the history is written only after each reset was
    *delivered*, whatever the clocks. With an early zero, a target whose start signal is
    still in its inbox relies on the order of `when_received` stamps of two writes, which
    two nodes may make with skewed clocks. That is the eventual order the framework accepts
    elsewhere (`feedback_delivery_order_is_eventual`), but for this target the reset could
    then come after some of its history, and drop it.
  - It takes a repeated report while `STARTED`, from a crash or a repeated reset (the latter
    is stopped at the source by the guard of `catch-up-started-delivered-twice`), plus
    another target not reset yet, plus skewed clocks.
- Scratch copy: the cases while `IN_PROGRESS`, while `FINALIZING`, and after `COMPLETED`
  pass. In the case while `STARTED`, everything passes but the check that the job is still
  `STARTED` with a count of 1 right after the duplicate, which only option B gives.
  The whole `:server:test`: 2094 tests, 1 skipped (`EventReactionRoutingSpec`,
  pre-existing), 1 failed — that check, nothing else. Among them: `InMemoryCatchUpTest` 10,
  `CatchUpStationTest` 10, `InMemoryDeliveryTest` 17, `CatchUpMessagesTest` 3,
  `ProjectionRepositoryCatchUpSpec` 2.

### B. Count distinct instances, on top of A

A new field `repeated google.protobuf.Any prepared_instance = 9` in `CatchUp`. A report of
an instance in the list is ignored; otherwise, the instance is added and counted. The list
is cleared on leaving `STARTED`, after which A rejects any report.

- Exact: the history is read only after each instance has reported, whatever repeats.
- Scratch copy: all four cases pass.
- Costs:
  - The job grows by one packed ID per target while `STARTED`, and is stored again on each
    report: about N²/2 IDs written for N targets.
  - `Delivery` reads every job ever stored — completed ones are never deleted — at the start
    of each run and after each page that is not full (`refreshCatchUpJobs()`), on every
    shard. While any job is `FINALIZING`, `MaintenanceStation` copies all of them into each
    `ShardProcessingRequested`. Clearing the list on leaving `STARTED` keeps this cost to
    the `STARTED` phase.
  - Each ID is a packed `MessageId` (the entity ID and its type URL, packed twice), so
    over 100 bytes even for a short string ID. A `catchUpAll()` of some 10,000 instances
    makes a job of over 1 MB, the limit of a record in some storages (a Datastore entity is
    at most 1 MiB).
  - A new field in a stored message: a compatible schema change, but one to keep.

### C. Remember the IDs of the counted events

Rejected. A report after a repeated reset has a new event ID. The same event delivered
again within a JVM is dropped by `LiveDeliveryStation` already. The costs are those of B.

## Plan (option A)

1. `CatchUpProcess.handle(EntityPreparedForCatchUp)`: return `NoReaction` unless the job
   is `STARTED`. Import `STARTED` statically, as the file does for the other statuses.
2. Javadoc of the handler: the reports are counted while `STARTED`; a report that comes
   later is ignored, e.g. one delivered again after a failure; counting it would read the
   history again and dispatch the events read before once more. The class-level section
   on `STARTED` says the same in one sentence. No change to `catch_up.proto`.
3. `DuplicateEntityPreparedIgTest`, as reproduced, with the case while `STARTED` reshaped
   to what A guarantees: the job completes once, and the late report is ignored. The check
   right after the duplicate goes. Name the case after it: "complete once if an instance
   reports twice while the catch-up is started".
4. Prove each case fails without the fix (as above) and passes with it, counts from the
   XML reports. Then `InMemoryCatchUpTest`, `CatchUpStationTest`,
   `InMemoryDeliveryTest`, and `./gradlew check dokkaGenerate` (the version gate blocks
   `build` until the bump).
5. Reviews: `spine-code-review`, `kotlin-engineer`, `review-docs`.
6. Only when authorized: the version bump, the dependency reports, a commit, and a PR.

Independent of the open catch-up branches; it applies to `master` as it is:

- `claude/dazzling-noyce-92ab0c` (`jobFor()`, PR #1683): no overlap. The new test runs one
  catch-up per case, so it does not need `jobFor()`.
- `claude/nostalgic-tesla-e45bb5` (the guard against a repeated `CatchUpStarted`):
  complementary. The guard stops a known source of repeated reports; this change makes
  the process tolerate any.
- `claude/funny-shannon-456685` (`catchUpAll()` of an empty repository, uncommitted):
  edits the same handler, moving its tail into `startRecalling()`. The guard goes before
  the count either way; whichever lands second takes a small merge.
- The version: `master` is at `.563`, and `.564` and `.565` are taken by the branches above;
  the number is chosen at landing.

(As of 2026-10-16, see "After merging `master`": the first and the third landed, the second
did not.)

## After merging `master`

On 2026-10-16 the branch, which had no commits, was fast-forwarded to `origin/master`
(`1ebc52b9193`, version `.571`); the uncommitted change was stashed and applied back without
a conflict.

What landed meanwhile, as far as this change goes:

- #1683 (`jobFor()`), #1684 (`catchUpAll()` of an empty repository: the tail of the handler
  is now `startRecalling()`, also called by `handle(CatchUpRequested)` when no instance
  receives the start signal), #1688 (a catch-up of all resets the inactive instances too),
  #1689 (`AbstractCatchUpIgTest` in test fixtures), #1686 (the catch-up jobs read for each
  page with a message to deliver).
- The handler is unchanged in what matters: `handle(EntityPreparedForCatchUp)` (:420 now)
  still counts every report and calls `startRecalling()` in any status. No test on
  `master` sends a repeated report.
- The guard of `catch-up-started-delivered-twice` did not land. Per
  `stale-catch-up-job-list.md`, #1686 makes its known source — a kept `CatchUpStarted`
  dispatched again under a stale job list — unreachable, as long as
  `CatchUpStorage.readAll()` sees the writes completed before it; a storage whose queries
  lag behind its writes can still produce it.
- `catch-up-of-empty-repository-hangs.md` lists this very guard under "Noticed along
  the way" and left it out on purpose.

So the repeated report now comes from a redelivery after a crash between the dispatch and
`conveyor.flushTo()`, or from a storage with lagging queries. Both are within the
at-least-once delivery the framework gives, and the consequence is unchanged.

Re-checked on the merged code, counts from the XML reports, `--no-build-cache`:

- `DuplicateEntityPreparedIgTest` with the guard: 4 tests, 0 failed.
- The same test on `master`'s `CatchUpProcess` (a scratch copy of the merged tree):
  4 tests, 4 failed, with the same symptoms as before the merge — the trail going back to
  `IN_PROGRESS` from `FINALIZING` and from `COMPLETED`, the count at −1, two
  `HistoryFullyRecalled`, and `first` at 120 instead of 110 after `COMPLETED`.
- `./gradlew check dokkaGenerate --no-build-cache` with the guard: passed. `:server:test`
  2139 tests, 0 failed, 1 skipped (`EventReactionRoutingSpec`), 580 fresh XML reports —
  among them the suites landed meanwhile: `CatchUpJobsPerPageIgTest` 6,
  `EmptyRepositoryCatchUpIgTest` 5, `InactiveInstancesCatchUpIgTest` 5,
  `RepeatedCatchUpIgTest` 2. Every other module ran fresh, none failed; `detekt`,
  `checkstyleMain`, `pmdMain` and `dokkaGenerate` ran for `server`.

`AbstractCatchUpIgTest` (#1689) covers the repository, the event weight, `emitHistory()`,
`totals()`, `undelivered()`, and a `Delivery` that records failures. The test cannot take
it as it is: `useDelivery()` accepts only a strategy, while the test needs its own
storages (to record the writes) and a monitor that is also a watchdog; and the base keeps
its failure list private. `CatchUpJobsPerPageIgTest`, which also needs a watchdog, extends
`AbstractDeliveryTest` too. Taking the base would need a hook in `useDelivery()` for
the storages and the monitor — a change to shared fixtures, for a follow-up.

## As implemented

Option A, as approved on 2026-10-07.

- `CatchUpProcess.handle(EntityPreparedForCatchUp)` returns `NoReaction` unless the job is
  `STARTED`. `STARTED` is imported statically, and `handle(CatchUpRequested)` uses
  the import instead of `CatchUpStatus.STARTED`. The copyright hook rewrote the header of
  the file to the CodeMatters one, as intended.
- The Javadoc of the handler: the reports are counted only while `STARTED`; a later one,
  such as one delivered again after a failure, is ignored; counting it would start another
  round of history reading, and some events could reach the instances twice.
- **Deviation from the plan:** the class-level section on `STARTED` is left as it is. The
  then uncommitted change of `funny-shannon` rewrote that section; a sentence there would
  only have made a conflict. Landed as #1684, the section now says that the process waits
  for each report and then moves to `IN_PROGRESS`; the handler's Javadoc covers the rest.
- `DuplicateEntityPreparedIgTest`: the four cases of the reproduction, in the order of
  the statuses. The case while `STARTED` is named "complete once if an instance reports
  twice before another one reports". Each case checks, in `assertCompletedOnce()`, that
  each target reported and one of them once more, then that the job completed once.

## Verification

- [x] The new suite with the fix: 4 tests, 0 failed. Without it (`master`'s
      `CatchUpProcess` in the scratch copy, the same test): 4 tests, 4 failed. In the case
      while `STARTED`, the precondition held, and the trail, the count (−1) and the second
      `HistoryFullyRecalled` failed. Counts from the XML reports, `--no-build-cache`.
- [x] `./gradlew check dokkaGenerate --no-build-cache`: passed. Each `test`, `detekt` and
      `dokkaGenerate` task ran. `:server:test`: 2094 tests, 0 failed, 1 skipped
      (`EventReactionRoutingSpec`, pre-existing), 571 fresh XML reports, the new suite's 4
      among them. Other modules: `core` 180, `client` 189, `server-testlib` 159,
      `core-testlib` 25, `server-otel` 22, `client-testlib` 2 — none failed.
- [x] Reviews, all approving; nothing blocking:
  - `review-docs`: the last sentence of the new Javadoc misdescribed the consequence. The
    extra round reads only after `when_last_read`, so it does not dispatch again what it
    dispatched; it can apply twice the events the instances received live. Reworded as
    suggested, keeping one cause ("delivered again after a failure"). Applied the test
    KDoc nits too: "that" for a restrictive "which", "repeated report" over "duplicate",
    clearer wording of several helpers, and comments on why each step is there.
  - `spine-code-review` and `kotlin-engineer` both found that the cases while
    `IN_PROGRESS`, while `FINALIZING`, and after `COMPLETED` did not check that
    the repeated report reached the process, so they could pass vacuously.
    `assertCompletedOnce()` now checks the reports in every case: one per target and
    the repeated one.
  - Also applied: a helper for the two cases that differ only in the signal, a clue that
    names what it asserts, `shouldHaveSize`, a block body for `assertCompletedOnce()`,
    a monotonic clock for the watchdog, the watchdog recorded wherever it stops
    the delivery, `Watchdog` given `failures` explicitly, `events()` typed
    `List<EventMessage>`, `heldCount` as a property, `withoutHolding()` for `runThrough()`,
    and `value = 0` dropped.
  - Deferred, as `spine-code-review` suggested: moving the harness — the recording
    storages, the watchdog, the holding executor — to `server/src/test/kotlin/.../given/`.
    `RepeatedCatchUpResetIgTest` (`nostalgic-tesla`), `SeveralRepositoriesCatchUpIgTest`
    (`quirky-agnesi`) and `EmptyRepositoryCatchUpIgTest` (`funny-shannon`) hold copies;
    moving it now would only collide. A follow-up for whichever lands last.
  - Not applied: deriving the expected totals from the constants, and a `TARGETS`
    constant. The literal totals read directly; the test names the weight where it sets it.
- [x] After the review changes:
  - the new suite with the fix: 4 tests, 0 failed; without it: 4 tests, 4 failed, each on
    the cascade (the trail, the count at −1, two `HistoryFullyRecalled`, `first` at 120
    after `COMPLETED`), while the new check of the reports passed in all four;
  - `./gradlew check dokkaGenerate --no-build-cache`: passed. `:server:test`,
    `:server:detekt`, `checkstyleMain`, `pmdMain` and `:server:dokkaGenerate` ran again:
    2094 tests, 0 failed, 1 skipped, 571 fresh XML reports. The modules not touched were
    up to date from the first run, which passed.
- IntelliJ IDEA inspections were not run.

## Noticed along the way

All pre-existing; none is fixed by the plan.

- **The other handlers of `CatchUpProcess` act in any status, too.** With A, a repeated
  report spawns no extra chain any more; these remain for a chain signal delivered again
  after a crash:
  - `handle(HistoryFullyRecalled)` (:483-491) moves any status but `FINALIZING` to
    `FINALIZING`, `COMPLETED` included, and runs the finalization: it reads past
    `when_last_read` with no upper bound and dispatches the result. After `COMPLETED`, that
    applies the events that came after the catch-up once more, as in the reproduction.
  - `handle(HistoryEventsRecalled)` (:433-436) reads more history in any status.
  - `handle(CatchUpRequested)` (:371-387) sets `STARTED` and resends the start signals in
    any status.
  A follow-up with its own plan: each handler acts only in the status it expects.
- `CatchUpStation.inProgress()` and `completedWith()` deduplicate only within a page and do
  not consult the delivered messages. That is how a second dispatch of an event reaches
  a projection in the reproduction.
- The comment of `EntityPreparedForCatchUp.instance_id` says "the identifier of
  the instance"; `EntityLifecycle` packs a `MessageId` there, which holds the ID and the type
  of the entity.
- In the class-level Javadoc of `CatchUpProcess`, the section "Not started" repeated the one
  on `STARTED` word for word. Fixed by #1684.
