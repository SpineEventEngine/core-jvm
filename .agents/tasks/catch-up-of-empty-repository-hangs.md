---
slug: catch-up-of-empty-repository-hangs
branch: claude/funny-shannon-456685
owner: claude
status: in-progress
started: 2026-10-06
---

# `catchUpAll()` of a projection with no stored instances never completes

Issue: none filed. Suspected by reading; also listed under "Noticed along the way" of
`repeated-catch-up-poisons-shard` (branch `claude/dazzling-noyce-92ab0c`). Reproduced on
`master` at `7b64d415975`.

## Status

- [x] Call path verified by reading — see "What the code says"
- [x] Reproduced with a real catch-up flow, 1 and 3 shards — see "Reproduction"
- [x] Fix tried in a scratch copy; it exposed a second stall, fixed there too — see
      "A second stall behind the first". The whole `:server:test` passes with it
- [x] Checked together with the fix of `repeated-catch-up-poisons-shard` — see
      "Interaction with `repeated-catch-up-poisons-shard`"
- [x] Design review by a planning agent — see "Design review"
- [x] Plan approved on 2026-10-06, in the variant recorded under "Plan" (part 2, option 1b)
- [x] Tests: `EmptyRepositoryCatchUpIgTest` — see "Regression tests"
- [x] Fix in `CatchUpProcess` — see "As implemented"
- [x] Verification on the base `7b64d415975`: `./gradlew check dokkaGenerate` passes —
      every module tested, `:server:test` ran 2094 tests (2089 + the 5 new ones), 0 failed;
      `detekt`, Checkstyle, PMD and Dokka clean. After the review changes, which touch
      `catch_up.proto`: `./gradlew clean check dokkaGenerate` passes the same way, 2094
      tests in `:server:test`, 0 failed in all modules. With the branch of #1683 merged and
      the version at `.565`: `./gradlew clean build dokkaGenerate` passes, `:server:test`
      ran 2107 tests, the specs of both fixes included, 0 failed in all modules
- [x] Reviews — see "Reviews"
- [x] Commits, authorized on 2026-10-07: the fix; the merge of `claude/dazzling-noyce-92ab0c`,
      the branch of [core-jvm#1683][pr-1683], on which this PR stacks; the version bump to
      `.565`, as #1683 takes `.564`; the dependency reports
- [ ] Pull request, based on `claude/dazzling-noyce-92ab0c`: #1683 merges first

## Problem

`ProjectionRepository.catchUpAll(since)` on a repository with no stored instances never
completes. Its job stays `STARTED` forever. Meanwhile, every live event to the projection
type is dropped, and every later `catchUp()` or `catchUpAll()` of the type throws
`CatchUpAlreadyStartedException`. The first call gives no sign of trouble: nothing is
thrown or logged, and `catchUpAll()` returns normally.

The main use case at risk: introducing a new projection type and building it from the
event history. A repository whose stored instances are all archived or deleted is hit
the same way, as `index()` returns active records only.

The defect dates from `f705445fa78` (2020), which made the process wait for every target
instance to clear its state before reading the history, without handling the case of
no targets.

## What the code says

Read at `7b64d415975`. Paths are under `server/src/main/`.

- **No targets.** `CatchUpProcess.handle(CatchUpRequested)` (`java/.../CatchUpProcess.java`
  :372) → `dispatchCatchUpStarted()` (:389) → `targetsForCatchUpSignals()` (:601): for a
  request with no targets, `ImmutableSet.copyOf(repository().index())`.
  `EntityRecordStorage.index()` (:105) lists the active records only.
- **The start signal reaches nobody.** An empty target set becomes `null` in
  `dispatchAll()` (:698); `ProjectionRepository.sendToCatchingUp()`
  (`kotlin/.../ProjectionRepository.kt`:372) sends a `CatchUpSignal` to
  `restrictToIds ?: index()` — nobody. So `instancesToClear` is set to 0 (:393).
- **Nothing else leaves `STARTED`.** `handle(EntityPreparedForCatchUp)` (:409) is the only
  transition to `IN_PROGRESS`, and that event is emitted only by the instances
  `CatchUpStarted` reached (`CatchUpEndpoint.onCatchUpStarted()`).
- **Live events are dropped.** A job with no targets matches every inbox message of the
  type (`CatchUpMixin.matches()` :63). While it is `STARTED`,
  `CatchUpStation.JobFilter.started()` (:175) removes each matched `TO_DELIVER` message
  (:181); `Conveyor.flushTo()` (:245) deletes it from `InboxStorage`. In a working
  catch-up that is fine, as the replay brings these events back from the `EventStore`;
  here, the replay never comes.
- **No new catch-up.** `CatchUpStarter.checkNotActive()` (:134) counts any job not
  `COMPLETED` as active; with no targets, it intersects any request
  (`hasIntersections()` :146).
- **Unnoticed by tests.** `CatchUpTest.AllowCatchUp.onEmptyEventStore()` and
  `ifPreviousCatchUpCompleted()` (`server/src/testFixtures/.../CatchUpTest.java` :161–181)
  start exactly this catch-up and assert nothing; `tearDown()` clears all jobs. The only
  asserting catch-up-all test, `testCatchUpAll()`, stores all its instances first.

## Reproduction

A throwaway probe, `EmptyIndexCatchUpProbeSpec` (1 shard) and
`EmptyIndexCatchUpThreeShardsProbeSpec` (3 shards), in
`server/src/test/kotlin/io/spine/server/delivery/`. `CounterView` repository; `Delivery`
built like `RepeatedCatchUpIgTest` does: the shard observer of `LocalDispatchingObserver`
plus a record of what `deliverMessagesFrom()` throws.

The history: 5 `NumberAdded` events for each of `first` and `second`, appended to the
`EventStore` through `BlackBox.append()`, bypassing the repository, as if emitted before
the projection type existed. Each event has a timestamp of its own, a second apart, ten
minutes ago. No frozen clock, no shared timestamps. `since` is an hour ago.

Unfixed, 1 and 3 shards alike (`first` → shard 2, `second` → 1, `third` → 0 of 3):

| Step                                           | Job       | `first` | `second` | `third` |
|------------------------------------------------|-----------|--------:|---------:|--------:|
| A1. `catchUpAll()`, nothing stored             | `STARTED` |  absent |   absent |  absent |
| A2. live events to `first` and `third`         | the same  |  absent |   absent |  absent |
| A3. `catchUpAll()` again                       | the same  |  absent |   absent |  absent |
| A4. `catchUp(since, {third})`                  | the same  |  absent |   absent |  absent |
| D1. `catchUpAll()`, nothing stored, no history | `STARTED` |  absent |   absent |  absent |

The job keeps `instancesToClear` at 0. The inbox stays empty throughout: the live messages
of A2 were removed undelivered. A3 and A4 throw `CatchUpAlreadyStartedException`.

Controls, which complete: B, `first` stored by a live event before `catchUpAll()` —
totals 6, 5, then `third` 1 after a live event; C, `catchUp(since, {first, second})`
with nothing stored — totals 5, 5. So the catch-up does build new instances from the
history; it only needs one instance to get past `STARTED`.

No failure was recorded in any run: the hang is silent.

## A second stall behind the first

The obvious fix — proceed to reading the history when `CatchUpStarted` reached nobody —
was tried in a scratch copy. Scenario A then completed (totals 5, 5; live events
delivered after). Scenario D, with no history, stalled in `FINALIZING` instead:

| Step (part 1 of the fix only) | Job          | `third` | Inbox           |
|-------------------------------|--------------|--------:|-----------------|
| D1. `catchUpAll()`            | `FINALIZING` |  absent | empty           |
| D2. a live event to `third`   | the same     |  absent | 1 `TO_CATCH_UP` |
| D3. `catchUpAll()` again      | the same     |  absent | 1 `TO_CATCH_UP` |

D3 throws `CatchUpAlreadyStartedException`.

The process completes once each *affected* shard reports `ShardProcessed` while the job
is `FINALIZING`. A shard counts as affected only when the catch-up dispatched something
into it (`recordAffectedShards()` :644). With nothing dispatched, `handle(LiveEventsPickedUp)`
(:520) requests no shard, so no `ShardProcessed` ever comes. Here the live messages are
not dropped but held back: `finalizingWith()` (:242) marks those of the type as
`TO_CATCH_UP` in every shard, and only a run that sees the job `COMPLETED` releases them.

Unreachable today, as the first stall comes earlier.

## Fix options

Part 1 — no instance to clear:

1. **Read the history at once (recommended).** In `handle(CatchUpRequested)`, if
   `CatchUpStarted` reached no instance, do what the last `EntityPreparedForCatchUp` would:
   set `IN_PROGRESS` and read the first page. The return type becomes that of
   `handle(EntityPreparedForCatchUp)`; both call one private method.
2. Emit `HistoryEventsRecalled` to itself to trigger the first read. Misuses the event:
   nothing was recalled yet.

Part 2 — no affected shard:

1. **Process every shard.** If no shard is affected, record all shards of the `Delivery`
   as affected. The existing handshake then runs unchanged: each shard is seen
   `FINALIZING`, then `COMPLETED`, and the second round (`on(CatchUpCompleted)`) releases
   the live messages held back meanwhile in any shard. Costs 2 × the shard count of
   maintenance events. Two places to decide it:
   - a. *Tried first:* in `handle(LiveEventsPickedUp)`, when no shard is affected by then.
   - b. *Chosen:* in `handle(CatchUpRequested)`, when `CatchUpStarted` reached nobody.
     Covers the case with history too: there, the history marks only its targets' shards
     as affected, and a live event to a new instance in another shard, arriving after the
     last read of the history, would stay held back. One decision point, and
     `handle(LiveEventsPickedUp)` stays unchanged.
2. Complete at once, skipping the handshake. Leaves the live messages held back during
   the short `FINALIZING` window paused until the next delivery run of their shard, which
   may not come in a quiet shard.
3. Treat all shards as affected for every catch-up of all instances. Also closes the gap
   in "Noticed along the way", but changes the regular flow; a follow-up at most.

Tried in a scratch copy, part 1 option 1 with part 2 option 1a, 1 and 3 shards:

| Scenario                                  | Job         | Totals after                   | Inbox |
|-------------------------------------------|-------------|--------------------------------|-------|
| A. nothing stored, history                | `COMPLETED` | 5, 5; live events: 6, 5, 1     | empty |
| D. nothing stored, no history             | `COMPLETED` | live event: `third` 1          | empty |
| B, C. controls                            | `COMPLETED` | unchanged                      | —     |

The whole `:server:test` on the scratch copy with this fix, without the probe: 2089 tests,
2089 passed (executed, not restored from the cache).

## Plan

As approved. The line references are at `7b64d415975`.

1. `CatchUpProcess` (one production file):
   - `handle(CatchUpRequested)` returns
     `EitherOf3<HistoryEventsRecalled, HistoryFullyRecalled, NoReaction>`. When
     `CatchUpStarted` reached no instance (`instancesToClear` is 0), it records every shard
     as affected — new private `affectAllShards()` — and starts reading the history at once
     — new private `startRecalling()`, which `handle(EntityPreparedForCatchUp)` calls too.
   - Javadoc of `handle(CatchUpRequested)` and of the `STARTED` section of the class says
     what happens with no instance to clear. The `STARTED` section repeats the text of
     "Not started" (:122–146); fixed, as it is edited anyway.
   - Not touched: the paragraphs the branch of `repeated-catch-up-poisons-shard` rewrites
     (in `3e1ee105834`) — "considered junk" (:191–198) and the unfinished sentence in
     the Javadoc of `handle(LiveEventsPickedUp)` (:509–514), so the branches merge.
2. Tests, new, in Kotlin: `EmptyRepositoryCatchUpIgTest`, `@SlowTest`. Each case builds its
   `Delivery`, recording what the delivery runs throw and what
   `DeliveryMonitor.onReceptionFailure()` reports — a receptor's exception never reaches
   a shard observer.
   The history is built as in "Reproduction". Each case checks: nothing failed, the job
   `COMPLETED`, the totals, no `TO_DELIVER` or `TO_CATCH_UP` leftovers.
   - builds the instances from the history, 1 and 3 shards: 5, 5; then live events to
     `first` and `third`: 6, 5, 1;
   - completes when there is no history, 1 and 3 shards: a live event to `third` after it;
   - delivers a live event held back while it finalizes: a test reactor emits a live event
     to `third` on `LiveEventsPickedUp`, that is, after the last read of the history, while
     `FINALIZING`. A test strategy puts `third` alone into shard 0 of 3, and the history
     and the catch-up process into shard 1, so the history dispatches nothing to shard 0.

   Expected: all red against the unfixed code; the third case red against option 1a.
3. `CatchUpTest` unchanged. Its `AllowCatchUp` cases still assert nothing. Tightening them
   is a follow-up: the fixture is shared with the downstream storages, a case that asserts
   should be `public` there so that they can disable it, and `ifPreviousCatchUpCompleted()`
   would then exercise the bug of `repeated-catch-up-poisons-shard` downstream.
4. Verification: the new spec against the unfixed code, option 1a and the final code;
   `:server:test --no-build-cache`; `./gradlew check dokkaGenerate`; the reviewers
   `kotlin-engineer`, `spine-code-review`, `review-docs`. Delete the throwaway probe.
5. Only when authorized: version bump, dependency reports, commit, PR.

## Design review

By a planning agent, before the plan. Verdict: sound with changes.

- Part 1 is safe: the first page of the history is dispatched while the stored job is
  still `STARTED`, exactly as on the existing path through `handle(EntityPreparedForCatchUp)`;
  `started()` keeps the `TO_CATCH_UP` messages, and a later run delivers them. The zero
  check uses the count `sendToCatchingUp()` actually sent to. Only a catch-up of all
  can reach 0.
- Part 2: no livelock in the handshake; `newIndex()` is always valid; `CatchUpStorage` is
  single-tenant; `EventReactorSignature` accepts the new return type.
- Taken: option 1b instead of 1a; recording `onReceptionFailure()` in the tests.
- Left out: assertions in `CatchUpTest` (see "Plan", step 3); a status guard in
  `handle(EntityPreparedForCatchUp)` against duplicated `EntityPreparedForCatchUp` — see
  "Noticed along the way".

## Regression tests

`EmptyRepositoryCatchUpIgTest`, 5 tests, run against the unfixed code, option 1a (in the
scratch copy) and the final code; outcomes read from the XML reports.

| Case                                               | Unfixed | Option 1a | Final |
|----------------------------------------------------|---------|-----------|-------|
| builds the instances from the history, 1 shard     | red     | green     | green |
| builds the instances from the history, 3 shards    | red     | green     | green |
| completes when there is no history, 1 shard        | red     | green     | green |
| completes when there is no history, 3 shards       | red     | green     | green |
| delivers a live event held back while it finalizes | red     | red       | green |

- Unfixed: each case fails on the job status, `STARTED`, and on the totals, absent. Nothing
  is recorded as failed and nothing is left in the inbox: the hang is silent.
- Option 1a: the last case fails on `third`, absent, and on one message left for it in
  shard 0, the held-back live event. The job is `COMPLETED`.

## As implemented

- `CatchUpProcess.handle(CatchUpRequested)` returns
  `EitherOf3<HistoryEventsRecalled, HistoryFullyRecalled, NoReaction>`. After
  `dispatchCatchUpStarted()`, if `instancesToClear` is 0, it calls
  `recordAllShardsAsAffected()` and `startRecalling()`.
- `startRecalling()` is extracted from `handle(EntityPreparedForCatchUp)`, which calls it.
- `recordAllShardsAsAffected()` — `affectAllShards()` in the plan, renamed in the review —
  sits next to `recordAffectedShards()`, which keeps merging into the set it records.
- Javadoc: the "Not started" section says the status becomes `STARTED`, not `IN_PROGRESS`;
  the `STARTED` section, which repeated it, describes the wait and the case of no
  instance, in prose: the catch-up events are `@Internal`, so the published class Javadoc
  does not name them. `handle(CatchUpRequested)` describes both outcomes.
- `catch_up.proto`: the comments of `total_shards` and `affected_shard`, which the change
  made stale, describe the case of all shards. Found in the review; not in the plan.
- The copyright hook replaced the headers of `CatchUpProcess.java` and `catch_up.proto`
  with that of the CodeMatters profile, as for the files of the recent PRs; they stay.
- The throwaway probe is deleted.

## Behavior change beyond the fix

- A catch-up of all whose `CatchUpStarted` reached nobody emits a `ShardProcessingRequested`
  for every shard, twice, so its completion waits on every shard, busy ones included.
- A second catch-up of the same type now starts after the first completes, so it meets
  the bug of `repeated-catch-up-poisons-shard` — see "Interaction with
  `repeated-catch-up-poisons-shard`". The new tests start one catch-up each.
- A repository whose stored instances are all archived or deleted now completes the
  catch-up instead of hanging, with the caveat in "Noticed along the way".
- Jobs already stuck in `STARTED` in deployed storages stay stuck: no event will come for
  them. Such a job can be overwritten as `COMPLETED` through `CatchUpStorage.write()`. No
  automatic healing: a stuck job looks exactly like the transient state each job has
  between the first `flushState()` of `handle(CatchUpRequested)` and the end of it.

## Interaction with `repeated-catch-up-poisons-shard`

With this fix alone, the probe's second `catchUpAll()` (steps A3, D3) hit that bug, as
expected: `NullPointerException` in `Conveyor.mutableMessage()`, the states deleted,
live messages stuck. In one run of the whole `:server:test`, the 3-shard probe spun in
A3 for 448 s and the test JVM ran out of heap. The symptoms fit the jobs being read as
`[COMPLETED, STARTED]`: `completedWith()` keeps the new `CatchUpStarted` for 1 s as
`DELIVERED`, and `dispatchAsCatchUpSignal()`, which ignores the status of the message,
delivers it again on each run while the job is `STARTED`. Each delivery deletes the state
and emits one more `EntityPreparedForCatchUp`, which reads the history again; the
synchronous local delivery nests these runs. The isolated runs ended with the NPE instead:
the order of the jobs in `readAll()` differs between runs.

With both fixes — this one and `CatchUpStation.java` of that branch — in another scratch
copy, every repeated catch-up of the probe completed, 1 and 3 shards: `catchUpAll()`
twice, then `catchUp(since, {third})`; totals 6, 5, 1, then `third` 2 after one more live
event; nothing thrown.

Before this fix, a repeated catch-up was already reachable through any repository with
stored instances, so the fix opens no new kind of exposure. Still, the owner chose to
merge [core-jvm#1683][pr-1683] first: this branch merges its branch, which merged
without conflicts, and the PR of this branch targets it.

## Reviews

- `kotlin-engineer` — approved. Applied: the KDoc of `weighEachEventOne()`, which looks
  removable but is not, as `CatchUpTest` leaves the static weight at 100; the totals
  derived from `EVENTS_PER_TARGET`; the KDoc of `ThirdApart`; the unused import of
  `newIndex()`; `catch (e: Exception)`, the very type `Delivery.onNewMessage()` swallows;
  the job statuses read from `ServerEnvironment`, so no `lateinit`; the subject of
  `@DisplayName` in backticks.
- `spine-code-review` — approved with changes. Applied: the published class Javadoc no
  longer names the `@Internal` catch-up events; the summary of `handle(CatchUpRequested)`
  mentions `IN_PROGRESS`; an early return in `startRecalling()`;
  `recordAllShardsAsAffected()`. Deferred, see "Follow-ups": the duplicated test helpers.
- `review-docs` — approved with changes. Applied: the `STARTED` section in the order of
  `startRecalling()` and with "affected", as elsewhere; "active" rather than "stored",
  since `index()` lists active records only; the KDoc of `ThirdApart` — the catch-up does
  send `ShardProcessingRequested` to the shard of `third`, the history does not; the
  antecedents of "which"; the comments in `catch_up.proto`; this document.

## Follow-ups

- Once this branch and that of `repeated-catch-up-poisons-shard` are merged, move the
  helpers both integration tests define — the recording `Delivery`, `jobStatuses()`,
  `totals()`, `undelivered()`, the IDs — to `server/src/testFixtures`.
- Then let `CatchUpTest.AllowCatchUp` assert that its catch-ups complete — see "Plan",
  step 3.

## Noticed along the way

By reading only, not run; all pre-existing and out of scope.

- **Archived and deleted instances are replayed without a reset.** A catch-up of all
  sends `CatchUpStarted` to `index()`, the active records only. The replay still routes
  the history to archived and deleted instances, and `findOrCreate()` loads them with
  their state (`RecordBasedRepository.find()` ignores the lifecycle flags), so the events
  since `since` are applied on top of it once more.
- **Live messages held back in a shard the catch-up did not affect.** While a catch-up
  of all is `FINALIZING`, a live event to an instance in a shard that received nothing
  from the catch-up is held back as `TO_CATCH_UP`, if it arrives after the final read of
  the history. Neither round of `ShardProcessingRequested` covers that shard, and
  `newestMessageToDeliver()` looks for `TO_DELIVER` only, so the message waits for the
  next delivery run of its shard. Part 2 option 3 would close it.
- **No status guard on `EntityPreparedForCatchUp`.** `handle(EntityPreparedForCatchUp)`
  (:409) does not check the status. A duplicated event, as the repeated `CatchUpStarted`
  of `repeated-catch-up-poisons-shard` produces, restarts the reading of the history and
  may set a `FINALIZING` or `COMPLETED` job back to `IN_PROGRESS`.
- **All the catch-up processes share one inbox.** Each `CatchUpProcess` builds its inbox
  for the state type `CatchUp`, and `InboxDeliveries.register()` (:70) keeps one delivery
  per type URL, the last one registered. So, with several projection repositories in one
  JVM, every catch-up runs on the process of the last repository, with its
  `dispatchOperation` and `EventStore`, while `repository()` resolves the right one.
  Spotted by the planning agent, confirmed by reading; offered as a separate task.

[pr-1683]: https://github.com/SpineEventEngine/core-jvm/pull/1683
