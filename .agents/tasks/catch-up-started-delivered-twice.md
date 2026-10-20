---
slug: catch-up-started-delivered-twice
branch: claude/nostalgic-tesla-e45bb5
owner: claude
status: in-progress
started: 2026-10-06
---

# A delivered `CatchUpStarted` signal is dispatched again

Issue: none filed. Suspected by reading in `repeated-catch-up-poisons-shard` (#1683), listed
there as "separate session, reproduction first".

## Status

- [x] Investigated and reproduced on 2026-10-07, at `7b64d415975` — see "History"
- [x] Based on `81c6387bb97` (#1686 and #1692 merged) on 2026-10-20; scope narrowed to
      the station, since the follow-ups of #1692 belong to `repeated-catch-up-signals`
- [x] Reproduced on `81c6387bb97` with a lagging catch-up storage — see "Reproduction"
- [x] Fix and tests — see "Change"
- [x] Verification and reviews — see "Verification"
- [ ] Version bump, dependency reports, PR — only when authorized

## Problem

`CatchUpStation.JobFilter.dispatchAsCatchUpSignal()` dispatches `CatchUpStarted` in any
status, while the other branches of the filter check the status of the message.

When a delivery run processes the start signal of a new job J2 with jobs that do not include
J2 yet, a completed job J1 of the same targets matches it. `completedWith()` dispatches it and
keeps it in the inbox: `DELIVERED`, with `keep_until` 1 s ahead. A later run that sees J2
`STARTED` passes the kept copy to `started()`, which dispatches it again: the target is reset
once more and reports once more. Since #1692, a report repeated while `STARTED` still counts,
so the history may be read before every target has reported.

## History

On 2026-10-07, before #1686, a run read the jobs only at its start and after a page that was
not full, so its list went stale within the run. A throwaway probe showed the repeated
dispatch with system events posted in parallel, the production default: 1 run in 3 with the
common pool, and every run with the events held. Each extra report drove `instances_to_clear`
below zero and started another finalization handshake; with as many handshakes as the page
size, the run never read the jobs again and livelocked, holding the shard. Under `Tests`,
system events are posted synchronously, and the reports landed in the same run, so the copy
was never met with J2 `STARTED`.

Since then:

- #1686 reads the jobs again for each page and restarts the run when they change, so
  a consistent storage never shows a run a list that lacks J2;
- #1692 counts a report only while `STARTED`, so a repeat after that changes nothing.

A storage whose queries lag behind its writes still shows such a list: `Delivery` reads the
jobs with `CatchUpStorage.readAll()`, a query, while the process loads and stores its job by
ID. #1692 names the same source for repeated reports.

## Reproduction

`KeptStartSignalIgTest`, `@SlowTest`. `CounterView` of `first` and `second`, a single shard,
system events posted in parallel through an executor that runs each task in the calling thread
or holds it. The catch-up storage can freeze what `readAll()` returns, while the writes and the
reads by ID go through.

J1 completes. Then, with the query frozen and the system events held, J2 starts for the same targets:
J1 delivers and keeps J2's two start signals (the precondition, asserted). Then the query is
unfrozen and the reports released.

Without the guard, at `81c6387bb97`: the precondition held, and each target reported twice —
`["first", "first", "second", "second"]` instead of `["first", "second"]`. The job still
completed with the counter at 0 and 1000 per target: #1692 counted the first two reports and
ignored the rest, which came after the job left `STARTED`.

## Change

- `dispatchAsCatchUpSignal()` accepts a message only in `TO_CATCH_UP` status, in which the
  signal is always sent (`ProjectionRepository.sendToCatchingUp()` → `toCatchUp()`). Its
  Javadoc says why; the Javadoc of `started()` names the start signal and says that messages
  in `DELIVERED` status are left as they are.
- The kept copy then: `started()` neither dispatches nor removes it, as it is not
  `TO_DELIVER`; the `CleanupStation` of a run after `keep_until` removes it.
- The Javadoc of `completedWith()` said the messages are kept only if a deduplication window
  is set; the code keeps them for `HOW_LONG_TO_KEEP` in any case, and the new Javadoc relies
  on that. Corrected.
- `CatchUpStationSpec`: J1 `COMPLETED`, J2 `STARTED`, J2's start signal `DELIVERED` with
  `keep_until`, both orders of the jobs → nothing dispatched, the message stays as it is.
- `KeptStartSignalIgTest`: one report per target, J2 `COMPLETED` with the counter at 0,
  1000 per target, nothing left to deliver, no failures; once `keep_until` passes, a run
  leaves no start signal in the inbox. Standalone on `AbstractDeliveryTest`, as
  `AbstractCatchUpIgTest.useDelivery()` takes no storages yet; it can move onto the base once
  `repeated-catch-up-signals` extends it.

## Verification

- [x] Without the guard (scratch copy, otherwise the same): `CatchUpStationSpec` fails 2 of
      13, the new case in both orders (`expected:<0> but was:<1>`); `KeptStartSignalIgTest`
      fails on the reports, two per target.
- [x] With the guard, all pass: `CatchUpStationSpec` 13, `KeptStartSignalIgTest` 1,
      `DuplicateEntityPreparedIgTest` 4, `CatchUpJobsPerPageIgTest` 6,
      `EmptyRepositoryCatchUpIgTest` 5, `InactiveInstancesCatchUpIgTest` 5,
      `RepeatedCatchUpIgTest` 2, `CatchUpStationTest` 10, `InMemoryCatchUpTest` 10.
- [x] `./gradlew check dokkaGenerate --no-build-cache`: passed; `:server:test` 2142 tests,
      0 failed, 1 skipped (`EventReactionRoutingSpec`, pre-existing); every module passed.
- [x] Reviews — `spine-code-review` and `review-docs` approved with changes, `kotlin-engineer`
      approved. Applied: the Javadoc names the `DELIVERED` status, `if (dispatched)`, the
      `completedWith()` Javadoc, wording of the test and of this file, stdlib types in the
      lagging storage, a clue on the job state. Deferred:
  - the version bump and the dependency reports, until a commit is authorized;
  - sharing `RecordingInboxStorage` and `HoldingExecutor` with `DuplicateEntityPreparedIgTest`,
    which `repeated-catch-up-signals` is renaming and moving onto `AbstractCatchUpIgTest`;
    this test can follow once that lands.
  - Not applied: a bare `Timestamps.add` import in `CatchUpStationSpec`, as a bare `add(...)`
    inside `copy { }` reads like adding to a collection.

## Noticed along the way

- Delete this file once the change is merged, as `AGENTS.md` asks.
- Tests post system events synchronously (`SystemSettings.defaults()`), which hides races
  that production's parallel posting shows. See the team memory
  `system-events-synchronous-in-tests.md`.
