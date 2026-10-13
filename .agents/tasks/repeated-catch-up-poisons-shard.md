---
slug: repeated-catch-up-poisons-shard
branch: claude/dazzling-noyce-92ab0c
owner: claude
status: in-review
started: 2026-10-06
---

# A repeated catch-up of a projection poisons its shard

Issue: none filed. Suspected by reading during the review of [core-jvm#1682][pr-1682];
reproduced on `master` at `3159f7c1cc3`.

## Status

- [x] Call path verified by reading — see "What the code says"
- [x] Reproduced with a real catch-up flow — see "Reproduction"
- [x] Option 1 tried in a scratch copy — see "Fix options"
- [x] Plan approved on 2026-10-06, with option 1
- [x] Tests: `CatchUpStationSpec`, `RepeatedCatchUpIgTest`. Against the unfixed code, 9 of
      their 11 cases fail, each on its stated assertion — see "Regression tests"
- [x] Fix in `CatchUpStation` — see "As implemented"
- [x] Verification on the base `3159f7c1cc3`: with the fix, the 11 new cases, the existing
      `CatchUpStationTest` and `InMemoryCatchUpTest` pass (31 tests, `--no-build-cache`);
      `./gradlew check dokkaGenerate` passes: all modules tested, `:server:test` ran 2097
      tests, 0 failed; `detekt` and Dokka clean. `build` itself is gated on the version
      bump, deferred until the commit is authorized
- [x] Reviews — see "Reviews"
- [x] Merge of `master`, which got [core-jvm#1682][pr-1682] (`7b64d415975`, `.563`)
      meanwhile — a fast-forward, as the branch had no commits yet. #1682 touches none
      of the files of this change; the 138 tests of `io.spine.server.delivery` pass on it
- [x] Version bump to `2.0.0-SNAPSHOT.564`, dependency reports. `./gradlew build` on
      the merged base passes: all modules tested, `:server:test` ran 2100 tests, 0 failed
- [x] Stale Javadoc of `CatchUpProcess` corrected, on the user's request
- [x] Pull request [core-jvm#1683][pr-1683]
- [x] First review round: Copilot's ordering remark, the Codecov gap — see "Reviews"
- [x] The maintainer's review: a job index instead of the scan of all jobs per message,
      and a plainer description — plan approved on 2026-10-08, see "Review by armiol"

## Problem

Once a projection has been caught up, the next catch-up of the same instances throws in
`CatchUpStation` on every delivery run of the shards it touches. Each such run applies the
replayed events once more, or, depending on the storage order of the jobs, deletes the
state of the instances again. It delivers nothing else from the shard, the live messages
of unrelated entities included. The second catch-up never completes.

## What the code says

Read at `3159f7c1cc3`. Paths are under `server/src/main/java/io/spine/server/delivery/`.

- **A finished job stays forever.** The only removal of `CatchUp` records is
  `CatchUpStorage.clear()` (:110), `@VisibleForTesting`, called only by the test fixture
  `CatchUpTest`. `CatchUpProcess` writes its state on each change (`store()`, :809).
- **A new catch-up starts next to it.** `CatchUpStarter.checkNotActive()` (:134) drops
  the `COMPLETED` jobs before looking for intersecting targets.
- **Each run sees each job.** `Delivery.refreshCatchUpJobs()` (:567) passes
  `catchUpStorage.readAll()`, of any status, to `CatchUpStation` (:609).
- **A message matches both jobs.** `CatchUpMixin.matches()` (:55) checks the projection
  type and the targets, not the status.
- **Each matching job processes the message.** `JobFilter.accept()` (:289) loops over the
  jobs. For a replayed `TO_CATCH_UP` message, in either order of the jobs:
  - `completedWith()` puts it into `dispatchToCatchUp`; then `inProgress()` finds its
    `DispatchingId` there and calls `conveyor.remove(message)`;
  - or `inProgress()` puts it, and `completedWith()` removes it.
- **The removed message is still dispatched, then the NPE.** `dispatch()` (:106) delivers
  `dispatchToCatchUp.values()`, then calls `conveyor.markDelivered()` (:115) →
  `changeStatus()` → `mutableMessage()` → `checkNotNull(messages.get(id))` (:184).
- **Nothing of the page is stored.** The exception leaves `Delivery.launch()` before
  `LiveDeliveryStation` runs and before `conveyor.flushTo()` (:600). Each run reads the
  shard from its start, so the next run meets the same page.
- **Only the log tells.** `Delivery.onNewMessage()` (:649) catches what the shard observer
  throws and logs it as `SEVERE`. `catchUp()` returns normally.

- **The `STARTED` phase may throw too.** With the jobs read as `[new STARTED, old
  COMPLETED]`, the `CatchUpStarted` signal throws: `started()` puts it, `completedWith()`
  removes it. The order is the one of `readAll()` of the storage in use. Observed, see
  "Reproduction".

By reading only, not run:

- While the new job is `FINALIZING`, the old `completedWith()` dispatches the `TO_CATCH_UP`
  messages the new job holds, the paused live ones included.
- Once two jobs of a projection are `COMPLETED`, each `TO_CATCH_UP` message matching both
  throws: the first `completedWith()` puts it, the second removes it. So the third catch-up
  of the same instances throws from its first signal, in any order.

## Reproduction

A throwaway probe in `server/src/test/kotlin/io/spine/server/delivery/`, later rewritten
as the regression test `RepeatedCatchUpIgTest`.
It uses `CounterView` and a single-shard `Delivery`, whose shard observer is the one of
`LocalDispatchingObserver` plus a record of what `deliverMessagesFrom()` throws.

The history is 10 `NumberAdded` events per target, posted live through `BlackBox`, so
each has its own real timestamp. Then a 1 s pause lets them leave the 500 ms turbulence
period. No frozen clock, no shared timestamps. The weight of an event is 10 during the
first catch-up and 100 from the second one on, so a total of 1000 means 10 events
applied once since the reset.

Scenario 1 — `catchUpAll()` twice, then two live events to a third instance:

| After                | Jobs                         | `first` | `second` | Leftovers in the inbox             | NPEs |
|----------------------|------------------------------|--------:|---------:|------------------------------------|-----:|
| the first catch-up   | `COMPLETED`                  |     100 |      100 | none                               |    0 |
| the second catch-up  | `COMPLETED`, `IN_PROGRESS`   |    1000 |      900 | 19 `TO_CATCH_UP`, 1 `TO_DELIVER`   |    1 |
| a live event         | the same                     |    2000 |     1800 | 19 `TO_CATCH_UP`, 2 `TO_DELIVER`   |    2 |
| another live event   | the same                     |    3000 |     2700 | 19 `TO_CATCH_UP`, 3 `TO_DELIVER`   |    3 |

Scenario 2 — `catchUpAll()`, then `catchUp(since, setOf("first"))`, then two live events
to `second`, which the second catch-up does not touch:

| After                | Jobs                         | `first` | `second` | Leftovers in the inbox             | NPEs |
|----------------------|------------------------------|--------:|---------:|------------------------------------|-----:|
| the first catch-up   | `COMPLETED`                  |     100 |      100 | none                               |    0 |
| the second catch-up  | `COMPLETED`, `IN_PROGRESS`   |    1000 |      100 | 10 `TO_CATCH_UP`, 1 `TO_DELIVER`   |    1 |
| a live event         | the same                     |    2000 |      100 | 10 `TO_CATCH_UP`, 2 `TO_DELIVER`   |    2 |
| another live event   | the same                     |    3000 |      100 | 10 `TO_CATCH_UP`, 3 `TO_DELIVER`   |    3 |

Scenario 2 once more: the in-memory `readAll()` returned the jobs in the other order, and
the `CatchUpStarted` signal threw:

| After                | Jobs                         | `first` | `second` | Leftovers in the inbox             | NPEs |
|----------------------|------------------------------|--------:|---------:|------------------------------------|-----:|
| the first catch-up   | `COMPLETED`                  |     100 |      100 | none                               |    0 |
| the second catch-up  | `STARTED`, `COMPLETED`       |  absent |      100 | 1 `TO_CATCH_UP`, 1 `TO_DELIVER`    |    1 |
| a live event         | the same                     |  absent |      100 | 1 `TO_CATCH_UP`, 3 `TO_DELIVER`    |    2 |
| another live event   | the same                     |  absent |      100 | 1 `TO_CATCH_UP`, 5 `TO_DELIVER`    |    3 |

- The NPE comes from the place predicted: `Preconditions.checkNotNull` ←
  `Conveyor.mutableMessage` (:184) ← `changeStatus` (:165) ← `markDelivered` (:99, :111)
  ← `CatchUpStation.dispatch` (:115).
- Each run applies one more pass of the stuck replays: `first` grows by 1000 per run.
- The catch-up stalls on its own next event, stuck behind the failing page as
  `TO_DELIVER`: `HistoryEventsRecalled` in scenario 1, `EntityPreparedForCatchUp` in the
  `STARTED` case.
- In the `STARTED` case, each run delivers `CatchUpStarted` again. It deletes the state of
  `first`, which is never rebuilt, and emits one more `EntityPreparedForCatchUp`.
- In scenario 2, `second` should reach 300, but its live events are never delivered.
- 19, not 20, in scenario 1: `recallMoreEvents()` holds back the events of the last
  timestamp of a page for the next round, which never comes.

## Fix options

1. **One job per message (recommended).** In `JobFilter`, find the single job that decides
   on a message: an ongoing job, of any status but `COMPLETED`, wins; otherwise any one
   `COMPLETED` job, since they all treat a message alike. One production file. It works
   for the jobs already stored by deployed applications, for any order of `readAll()`,
   and in each phase of the new job. The cost of reading all jobs on each run stays.
2. **Delete a job after it completes.** Not a fix on its own:
   - deleting at `COMPLETED` orphans the paused `TO_CATCH_UP` messages: with no matching
     job, no station delivers or removes them;
   - deleting later needs one more round of the `ShardProcessingRequested` /
     `ShardProcessed` handshake, and `MaintenanceStation` stamps the run info only while
     some job is `FINALIZING`, so the protocol of the process changes;
   - the jobs stored before the change stay, and keep breaking new catch-ups.

   Worth a follow-up for the cost: each run reads each job ever created.
3. **Skip a `COMPLETED` job superseded by a newer one.** `CatchUp` has no creation time
   to tell the newer one, and the coverage of target sets (all instances vs. some IDs)
   must be computed per pair of jobs. More code than option 1 for the same effect.

Option 1, tried in a scratch copy of the repository with the throwaway spec: the whole
`:server:test` ran 2088 tests, all passed, the probe and `InMemoryCatchUpTest`
included. In both scenarios the second job reaches `COMPLETED`, `first` ends at 1000,
`second` at 1000 and 300, the live events are delivered, the inbox is empty and nothing
throws.

## Plan

1. `CatchUpStation.JobFilter`: `accept()` takes the job from a new private
   `jobFor(InboxMessage)`, whose Javadoc states the precedence and why. The Javadoc of
   `JobFilter` says that each message is processed by one job.
2. Tests, new, in Kotlin:
   - `CatchUpStationSpec`: a hand-built conveyor, two jobs matching the same messages,
     each case in both orders of the jobs:
     - `COMPLETED` and `IN_PROGRESS`: a `TO_CATCH_UP` message is delivered once and marked
       `DELIVERED`, nothing throws; a `TO_DELIVER` one is removed;
     - `COMPLETED` and `FINALIZING`: nothing is delivered, a `TO_DELIVER` message is
       marked `TO_CATCH_UP`;
     - `COMPLETED` and `STARTED`: the `CatchUpStarted` signal is delivered once;
     - two `COMPLETED`: a `TO_CATCH_UP` message is delivered once and kept for longer;
     - added while writing: a message which only the `COMPLETED` job matches is still
       processed by it, so the choice is made per message, not per list of jobs.
   - `RepeatedCatchUpIgTest`, `@SlowTest`: the two scenarios above, without the printing,
     asserting no failure, all jobs `COMPLETED`, the expected totals and no
     `TO_CATCH_UP` or `TO_DELIVER` leftovers.

   A Kotlin spec, not a new case of the Java fixture `CatchUpTest`, as new tests are
   Kotlin. So the downstream storages, which rerun `CatchUpTest`, will not run it; the
   order of their `readAll()` is covered by the unit spec instead.
3. Verification: the new specs fail without the fix and pass with it; `./gradlew build`
   and `dokkaGenerate`; the reviewers of `pre-pr`.
4. Only when authorized: version bump (`.564`, as [core-jvm#1682][pr-1682] takes
   `.563`), dependency reports, commit, PR.

## Regression tests

Run against the unfixed code: 11 tests, 9 failed, 2 passed.

- `CatchUpStationSpec`, both orders of the jobs unless noted:
  - `IN_PROGRESS`, and two `COMPLETED` jobs: the NPE at `Conveyor.mutableMessage()`;
  - `FINALIZING`: `expected:<0> but was:<1>` for the delivered count — the completed job
    dispatched a message the finalizing one holds. Seen by reading before, now observed;
  - `STARTED`: with the completed job read first, the signal comes out kept for longer,
    so the completed job processed it; read last, the NPE;
  - the message which only the completed job matches: passes, as it should, since one
    job matches it.
- `RepeatedCatchUpIgTest`: all four soft assertions fail in both scenarios — the recorded
  NPEs, the second job `STARTED` or `IN_PROGRESS`, the totals, the leftovers. The checks
  after the first catch-up pass.

## As implemented

- `CatchUpStation.JobFilter.accept()` processes a message by the job that
  `CatchUpJobs.jobFor()` chooses: a matching job in any status but `COMPLETED`,
  otherwise a matching `COMPLETED` one, otherwise none. The first version did the same
  with a loop over all jobs per message, which master had too; see "Review by armiol".
- `CatchUpJobs` (new, package-private): the jobs read from the storage, indexed by
  projection type for the jobs of all instances, and by type and packed instance ID for
  the others, with the precedence applied while indexing. `Delivery.refreshCatchUpJobs()`
  builds it once per read of the jobs; the station needs two lookups per message.
- The stale Javadoc of `CatchUpProcess`, added to this change on the user's request
  (2026-10-06):
  - the class doc said that the events of a `COMPLETED` catch-up are "considered junk and
    are immediately deleted"; it now says they are deduplicated and delivered in their
    chronological order, as `completedWith()` does;
  - "stuck in limbo, and will eventually be deleted" became the real risk of completing
    too early: some historical events may reach the projections after the live events
    that followed them;
  - the Javadoc of `handle(LiveEventsPickedUp)` had an unfinished sentence about
    `MaintenanceStation`, and claimed the shards would hold no historical events. It now
    says what the station stamps, and that each affected shard must be processed by
    a `Delivery` that saw the catch-up `FINALIZING`, and so held its events.
- The copyright hook replaced the headers of `CatchUpStation.java` and
  `CatchUpProcess.java` with the one of the CodeMatters profile, the default of
  `.idea/copyright` since `d332c472665`. Files changed by the recent PRs carry it too,
  so it stays.

## Reviews

- Design, by a planning agent before the plan: sound and minimal, no regression. Its
  findings went to "Behavior change beyond the fix" and "Noticed along the way".
- `review-docs`, approved with changes, all applied:
  - restrictive "which" → "that", three times;
  - `@return` in sentence case;
  - "at most one job" in the Javadoc of `JobFilter`;
  - the clause in the Javadoc of `jobFor()` rewritten;
  - clearer test names ("the start signal", "the job in progress");
  - KDoc for the members of `JobOrder`.
- `kotlin-engineer`, approved with changes:
  - applied: the observer wraps `LocalDispatchingObserver` instead of copying it;
    `delivery` is a `val`; `shouldHaveSize(1)` before `single()`;
  - declined: back-dating the history instead of `Thread.sleep`. The task asks for
    a real flow, and the sleep costs 1 s per test and cannot make it flaky — it only
    makes the events older. Verified by five reruns, all green.
- `spine-code-review`, approved with changes, no finding in the production logic:
  - applied: the integration suite renamed to `RepeatedCatchUpIgTest`, as `testing.md`
    asks; the duplicated name of the parameterized tests moved to a constant;
    `under<Tests> { }` instead of `ServerEnvironment.under()`;
  - deferred: the version bump to `.564`, which waits for the authorization to commit.
- First round on the PR (2026-10-07):
  - Copilot: the new Javadoc of `CatchUpProcess` promised a global chronological order,
    while `CatchUpStation` orders and deduplicates within one batch. Qualified;
  - Codecov: the two uncovered patch lines were the `switch` of `accept()`, moved by the
    fix. Covered by a case with a job without a status;
  - Codex: no findings.

### Review by armiol

Changes requested on 2026-10-07: "For each accepted `InboxMessage`, a cycle over all jobs
is performed", while a catch-up processes hundreds of thousands to millions of messages;
and the description should explain the issue in a plainer, longer way.

- The scan was not new: on master, `accept()` loops over all jobs per message, and
  `matches()` scans the target IDs of each. It runs on every message of every page,
  live ones included, and the jobs only accumulate. The first version kept it.
- Now `CatchUpJobs` makes the choice two lookups per message, built once per read of
  the jobs, which costs the same order as reading them. Building it per page was
  rejected: with a catch-up of 100,000 IDs, it would cost about 200 million inserts
  per million messages.
- A design check before the plan confirmed that the index matches exactly the jobs
  that `matches()` does: the same `String` and `Any` equality. `CatchUpJobsSpec` checks
  it over every subset of a pool of jobs, in both orders.
- The PR description was rewritten around an example, with a sequence diagram.

## Behavior change beyond the fix

A paused message of the old job, still in the inbox when the new job of the same target
starts, now follows the new job like any other message of that target: delivered in
`IN_PROGRESS` or after `COMPLETED`, held in `FINALIZING`. Before, the old job delivered
it on the next run, whatever the phase of the new job. It is not lost either way, and
it may be applied twice either way: the replay of the new job brings the same event, and
`CatchUpStation` drops a duplicate only within one batch. The window is narrow: between
`COMPLETED` of the old job and the run of its last `ShardProcessingRequested`.

## Noticed along the way

All pre-existing. Decided with the user on 2026-10-06: the Javadoc is fixed here, the
signal guard and the empty repository went to separate sessions, the rest needs plans
of their own.

- **Fixed in this change:** the stale Javadoc of `CatchUpProcess` — see "As implemented".
- **Separate session, reproduction first:** `dispatchAsCatchUpSignal()`
  (`CatchUpStation.java`) ignores the status of the message. If a run keeps a stale list
  of jobs across a full page, the completed job delivers the `CatchUpStarted` of the new
  one and keeps it for 1 s as `DELIVERED`; the next run sees the new job `STARTED` and
  delivers that copy again. A guard on `TO_CATCH_UP` would do, once reproduced.
- **Separate session:** `catchUpAll()` on a repository with no stored instances sends
  `CatchUpStarted` to nobody (`ProjectionRepository.kt:371`), so no
  `EntityPreparedForCatchUp` ever comes and the job stays `STARTED` forever. Meanwhile,
  `started()` drops each live event of the type, and no new catch-up can start. By
  reading only, not run.
- **Needs a plan of its own:** the cost of reading every job ever created on each run —
  option 2 above.
- **Needs a plan of its own:** `CatchUpStarter.start()` checks for active jobs, then posts
  `CatchUpRequested`; the job is stored only when the process handles it. Two quick
  calls can both pass the check. An atomic "start unless active" touches the
  `CatchUpStorage` SPI.
- **Needs a plan of its own:** the double application of the paused messages of the old
  job — see "Behavior change beyond the fix". The starter would have to treat that job
  as active until its last `ShardProcessingRequested` is processed.
- **Small follow-up:** while any job is `FINALIZING`, `UpdateShardProcessingEvents` stamps
  every job, with all its target IDs, into each `ShardProcessingRequested`, and the copy
  is persisted and carried into `ShardProcessed`. Its only reader,
  `CatchUpProcess.findJob()`, needs one job. Stamping only the job named in the event
  would bound the size.
- **Small follow-up:** `CatchUpProcess.dispatchAll()` unpacks all the target IDs again on
  every round of reading the history.

[pr-1682]: https://github.com/SpineEventEngine/core-jvm/pull/1682
[pr-1683]: https://github.com/SpineEventEngine/core-jvm/pull/1683
