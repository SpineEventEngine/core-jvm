---
slug: stale-catch-up-job-list
branch: claude/silly-jemison-611222
owner: claude
status: in-review
started: 2026-10-07
---

# A delivery run decides on catch-up messages by an outdated job list

Issue: none filed. Option 3 and "Noticed along the way" of `catch-up-started-delivered-twice`
(branch `claude/nostalgic-tesla-e45bb5`, uncommitted).

## Status

- [x] Each consequence reproduced with a real catch-up flow — see "Reproduction"
- [x] Two candidate fixes tried in a scratch copy — see "Fix options"
- [x] Plan approved on 2026-10-07, with "re-run on change" — see "Design as approved"
- [x] Branch based on PR #1683, at the user's request — see "Landing"
- [x] Fix, tests, docs — see "As implemented"
- [x] Verification and reviews — see "Verification"
- [x] Version bump to `.565`, dependency reports, PR against `claude/dazzling-noyce-92ab0c`
      — authorized on 2026-10-07

## What the code says

Line numbers refer to `7b64d415975` (`master`).

`Delivery.runDelivery()` (:536-565) reads the first page (:539), then the catch-up jobs
(:544). It reads the jobs again only after a page smaller than `pageSize` (:554-556), and then
before it reads the next page (:557). Two gaps follow:

1. **A run whose pages stay full keeps the list read at its start.** Each page after the first
   is delivered by a list that may predate its messages.
2. **Even a refreshed list predates the page it is used for.** A message written between
   the read of the jobs and the read of the page — by another thread, shard or node — is
   delivered by a list older than the message.

`CatchUpProcess` stores the status of a job before it sends the messages that an older
status would mishandle:

- `STARTED` (:382-383) before `CatchUpStarted` goes to the targets (:385);
- `FINALIZING` (:487-488) before `runFinalization()` reads the rest of the history (:490);
- `COMPLETED` (:595-596) before `CatchUpCompleted`.

Jobs read after the page reflect each change stored before the messages of the page were
written. `runDelivery()` reads them that way once per run, for the first page only.

`IN_PROGRESS` is set without a flush before the first replayed events are sent (:415-417).
That is harmless: under `STARTED`, `CatchUpStation` keeps replayed events for a later run.

Completed jobs are never deleted: the only removal, `CatchUpStorage.clear()` (:110), is
`@VisibleForTesting`. Each read returns every job ever created.

## Reproduction

A throwaway probe, `server/src/test/kotlin/io/spine/server/delivery/StaleJobListProbeSpec.kt`,
uncommitted, to be deleted. `CounterView` in a `BlackBox`, a single-shard `Delivery`, and:

- tracing subclasses of `InboxStorage` (pages read, writes, removals) and `CatchUpStorage`
  (reads of the jobs, changes of status);
- a `DeliveryMonitor` that traces the runs, records reception failures, and stops a run
  after 200 handshake requests or 2000 pages — the watchdog;
- a shard observer that wraps `LocalDispatchingObserver`, records failures, can pause,
  and runs one-off hooks when a given message is written. A hook posts live events,
  standing for a concurrent writer;
- system events posted three ways: synchronously (the default under `Tests`), in parallel
  through an executor that runs each task in the calling thread, and in parallel, held
  until the catch-up call returns.

History: live `NumberAdded` events with timestamps of their own, then a 1 s pause, past
the turbulence period. No frozen clock. Each case asserts the correct outcome, so a
failure shows a consequence.

Bases: `master`; "stacked" is `master` with the `CatchUpStation` of `b53637bf21a` (PR #1683,
`jobFor()`) and the uncommitted guard of `catch-up-started-delivered-twice`.

| Case | Setup                                         | `master`             | stacked            | Correct       |
|------|-----------------------------------------------|----------------------|--------------------|---------------|
| C1a  | page size 1, one catch-up                     | livelock             | livelock           | completes     |
| C1b  | page size 2, two catch-ups, one run           | livelock             | livelock           | completes     |
| C2   | page size 3, window 1 min, repeated catch-up  | NPE of PR #1683      | `first` = 1200     | 1100          |
| C3   | page size 3, window 1 min, one catch-up       | `first` = 100        | `first` = 100      | 110           |

All three postings of system events gave the same outcome. The exception is C2 held on
`master`: the known repeated dispatch of a kept `CatchUpStarted`, fixed by the guard.

### C1 — the finalization handshake livelocks the run

**C1a**, page size 1, one catch-up of `first`, 5 history events. The run read the jobs
once, at its start: `J1=STARTED`. Within the run, the job went `IN_PROGRESS`, then
`FINALIZING`. With one message per page, no page is short, so the list never changes.
`MaintenanceStation` stamps a `ShardProcessingRequested` only if the list holds a
`FINALIZING` job (:65-69), so every `ShardProcessed` came back unstamped:

```
page 1/1 full: SPR(J1)/live
   + SP(J1,seen=none)/live
page 1/1 full: SP(J1,seen=none)/live
   + SPR(J1)/live
...
WATCHDOG: run #6 stopped after 411 pages and 200 handshake requests
```

The replayed events stayed held under `STARTED`, and the projection stayed absent. After
the stop, the next run read `J1=FINALIZING`, and the catch-up completed with correct totals.
Without a stop, the run never ends and holds the shard. So, with page size 1, every
catch-up livelocks a run, unless something stops the run: a monitor, or a node going down.

**C1b**, page size 2. Two catch-ups of `first` and `second` were requested while the delivery
was paused, as a scheduler that runs the delivery after several writes would do. Both jobs
finalized within one run, with the list `J1=STARTED, J2=STARTED`. Each page held the two
handshakes in lockstep, until the watchdog stopped the run after 200 requests, 211 pages.

The other task observed 3 handshakes at page size 3, multiplied by repeated
`CatchUpStarted` dispatches. With the guard, a job has one handshake per affected shard.
The condition is as many handshakes in a shard as the page size. By reading, not run:
live traffic that fills every page has the same effect for as long as it lasts.

### C2 — a live event applied twice

Stacked base. J1, a catch-up of all instances, completed: 100 per target. Live events to
`third` made the next message fill a page. J2, a catch-up of `first`, weight 100. A hook
posted a live event E to `first` right after the start signal of J2 was written:

```
jobs read: J1=COMPLETED
page 3/3 full: 2 done; Req(J2)/live
   * job J2: null -> STARTED (clear=0)
   + Started(J2->first)/cu
   + E(first)/live
page 2/3 SHORT: Started(J2->first)/cu, E(first)/live
   + Prepared(J2)/live                                  <- J1 reset `first` by J2's signal
   = Started(J2->first)/done+keep, E(first)/done+keep   <- E delivered live after the reset
jobs read: J2=STARTED, J1=COMPLETED
...
page 1/3 SHORT: FullyRecalled(J2)/live
   * job J2: IN_PROGRESS -> FINALIZING (clear=0)
   + E(first)/cu                                        <- the final read replays E
...
page 3/3 full: 2 done; E(first)/cu
   = E(first)/done+keep                                 <- delivered again
```

`first` = 1200, while 10 history events and E, once each, give 1100. On `master`, the run
starts the same way, and then J2 never completes: the replayed events match both jobs and
hit the NPE fixed by PR #1683. C2 is observable only with `jobFor()`.

### C3 — a live event lost

Both bases. One catch-up of `first`, weight 10. Two hooks: three live events to `third`
right after `HistoryFullyRecalled` is written, which fill its page, and a live event E to
`first` right after `LiveEventsPickedUp` is written, so after `runFinalization()` read the
history:

```
jobs read: J1=IN_PROGRESS
page 3/3 full: FullyRecalled(J1)/live, N(third)/live, N(third)/live
   * job J1: IN_PROGRESS -> FINALIZING (clear=0)
   + LivePickedUp(J1)/live
   + E(first)/live
page 3/3 full: N(third)/live, LivePickedUp(J1)/live, E(first)/live
   - REMOVED E(first)/live                              <- `inProgress()`, never delivered
```

`first` = 100 instead of 110. E reached the event store after the final read, so no
replay brings it back.

Without the events to `third`, the page of `HistoryFullyRecalled` is short, and the run
reads the jobs right after it. On one thread, nothing else writes into that window: it
lasts from the change of status to the next short page, which under load is the rest of
the backlog. By reading: under a stale `STARTED`, `started()` removes the live events of
the targets too (:180-181).

## Fix options

**Option U.** Read the jobs after reading each page that holds any message. Drop the read
at the start of a run and the one after a short page.

**Option C.** As U, but only for a page that holds a message not yet `DELIVERED`. Such
pages are the only ones a job can affect. A page of delivered messages, kept for
deduplication, gets an empty list:

- `LiveDeliveryStation` and `MaintenanceStation` act on messages to deliver only;
  a stamped `ShardProcessingRequested` that is delivered already is not written back;
- `CatchUpStation` dispatches or removes messages in `TO_CATCH_UP` and `TO_DELIVER` only.
  The exception is a kept `CatchUpStarted`, which `master` dispatches again under
  `STARTED`; the guard stops that, and the fix makes the copy unreachable;
- `CleanupStation` does not use the jobs.

Tried in scratch copies:

- Both options: the 12 probe cases pass on the stacked base, for each posting.
- Option C on `jobFor()` alone, without the guard: the 12 probe cases,
  `RepeatedCatchUpIgTest`, `InMemoryCatchUpTest`, `CatchUpStationTest` pass. The scenario of
  `RepeatedCatchUpResetIgTest`, with its precondition removed, ends correct: both jobs
  `COMPLETED`, 1000 per target. The completed job never delivered the new start signals.
- Option C, stacked, full `:server:test`: 2118 tests, 1 failure, 1 skipped
  (`EventReactionRoutingSpec`, `@Disabled`). The failure is the precondition of
  `RepeatedCatchUpResetIgTest`: "the start signals, delivered and kept by the completed job"
  — see "Landing".
- Handshake requests per catch-up: C1a 2, C3 2 (3 on `master`): a request is no longer
  answered from a list read before the job was `FINALIZING`.

### Cost

Reads of the jobs, per run:

| Run                                 | today | U     | C                                     |
|-------------------------------------|-------|-------|---------------------------------------|
| empty shard                         | 2     | 0     | 0                                     |
| one short page                      | 2     | 1     | 1, or 0 if all its messages are kept  |
| `n` full pages, then nothing        | 1     | `n`   | one per page with a message to deliver |
| `n` full pages and a short one      | 2     | `n+1` | one per page with a message to deliver |

Measured in the probe, in memory — today / U / C:

| Case | today            | U   | C   |
|------|------------------|-----|-----|
| C2   | 100              | 244 | 54  |
| C3   | 83               | 199 | 36  |
| C1a  | 20, livelocked   | 22  | 22  |
| C1b  | 45, livelocked   | 27  | 27  |

Each run starts from the head of the shard, so it re-reads every delivered message kept for
the deduplication window. U pays one read for each such page; C pays none. Without a window
— the default of `DeliveryBuilder` — U and C read the same.

Neither option changes the size of a read: every job ever created. That cost is the
follow-up "completed jobs pile up".

Both rely on `CatchUpStorage.readAll()` seeing the writes completed before it. A storage
whose queries lag behind its writes can still yield an older list; the guard of the other
task stays useful against that.

Option C was recommended; the review of the plan changed the design — see below.

## Review of the plan

A planning agent reviewed the plan before approval. Its findings, and what came of them:

- **Blocking — the order at a change of status.** `CatchUpStation` holds messages for a
  status: replayed events under `STARTED`, events paused under `FINALIZING`. They stay
  behind the page cursor until the next run. A list read per page makes a change of status
  visible at the next page, so later live events overtake the held ones. Today, that
  happens after a short page — the tail of a run. With a read per page, it happens on busy
  shards too. Confirmed by C4 below; answered by the re-run on a change.
- **Blocking — the two-shard test of the plan** was neither deterministic nor observable.
  Replaced by its simpler design: a synthetic job and a hook in the read of the jobs (C5).
- C1b needs both catch-ups in lockstep: too fragile for a regression test. Dropped there.
- C2 had been measured with the guard. Re-measured on `b53637bf21a` alone: 1200.
- Skipping the read for a page of delivered messages: confirmed safe, station by station.
- A replicated storage could return an older status within a run. Not addressed: see
  "Noticed along the way".

### C4 — held events overtaken at the completion

`ConsecutiveProjection` ignores a value that does not follow the last one. Values 1-5 are
the history. A hook posts 6 as `HistoryFullyRecalled` is written, so the last read of
the history replays it, held by `FINALIZING`. Another hook posts 7 as `CatchUpCompleted` is
written. "Busy": every handshake message is followed by live events to another instance,
so the pages around the completion are full.

| C4    | today | option C | option C, re-run on a change |
|-------|-------|----------|------------------------------|
| quiet | 6     | 6        | 7                            |
| busy  | 7     | 6        | 7                            |

Today, on `b53637bf21a`, quiet:

```
   * job J1: FINALIZING -> COMPLETED (clear=0)
   + V7(x)/live
jobs read: J1=COMPLETED
page 2/3 SHORT: Completed(J1)/live, V7(x)/live
   = Completed(J1)/done+keep, V7(x)/done+keep           <- 7 delivered live
 -- runDelivery() --
page 3/3 full: 2 done; V6(x)/cu
   = V6(x)/done+keep                                    <- the held 6 comes after 7
```

With the re-run, the run that reads `J1=COMPLETED` ends without delivering that page;
the next run delivers the held 6, then 7.

## Design as approved

On 2026-10-07, the user chose "re-run on a change": option C, and a run that reads job
statuses different from those it read for the previous page ends without delivering the
page. `deliverMessagesFrom()` then runs again from the head of the shard, so the held
messages go first. The first read of a run sets the baseline, so each run delivers at least
one page; statuses only move forward, so the re-runs are bounded.

Reads of the jobs in the probe with this design, synchronous posting: C2 63, C3 40,
C1a 26, C1b 31 — against 100, 83, 20 and 45 today (the last two livelocked).

## As implemented

- `Delivery`:
  - `runDelivery()` reads the jobs through `CatchUpJobsOfRun`, a private inner class.
    `readFor(page)` returns no jobs for a page of delivered messages. Otherwise, it reads
    all the jobs and compares their statuses with the previous read; `changed()` tells
    the result. On a change, the run breaks out of its loop before delivering the page.
  - The read at the start of a run, the one after a short page, and `refreshCatchUpJobs()`
    are gone.
  - Javadoc: `deliverMessagesFrom()` names the second reason to run again;
    `runDelivery()` refers to `CatchUpJobsOfRun`, whose Javadoc explains the read after
    the page, the empty list, and the re-run.
- `RunResult`: a third flag, `catchUpJobsChanged`; `shouldRunAgain()` is true for it,
  unless the monitor stopped the run.
- `CatchUpProcess` class Javadoc: `Delivery` reads the catch-up details each time it reads
  a batch to deliver; a run that sees a status change starts over, so the held events go
  first; "this `Delivery` run" became "the `Delivery`".

## Tests

`CatchUpJobsPerPageIgTest`, `@SlowTest`, single shard, synchronous system events:

1. with pages of one message, a catch-up completes (C1a);
2. a live event arriving as a repeated catch-up starts is applied once (C2);
3. a live event arriving after the history is read to the end is delivered (C3);
4. the events held for a catch-up are delivered before the live events that follow
   its completion (C4, quiet);
5. each page is delivered according to the jobs read after it (C5): a synthetic
   `IN_PROGRESS` job of `first`; right after the jobs are read, a hook stores it
   `FINALIZING` and posts a live event to `first`, which must end held, `TO_CATCH_UP`.

A `DeliveryMonitor` watchdog stops a delivery after 1000 pages and then stops every run,
while the shard observer stops dispatching: a livelock fails the case at once. Failures are
recorded from the shard observer and from `onReceptionFailure()`.

Outcomes in scratch copies of the branch:

| Case | without the fix | option C only | read before each page | no skip   | the fix |
|------|-----------------|---------------|-----------------------|-----------|---------|
| C1a  | fails: stopped  | passes        | passes                | passes    | passes  |
| C2   | fails: 1200     | passes        | passes                | passes    | passes  |
| C3   | fails: 100      | passes        | passes                | passes    | passes  |
| C4   | fails: 6        | fails: 6      | fails: 6              | passes    | passes  |
| C5   | passes          | passes        | fails: removed        | passes    | passes  |
| C6   | fails: 2 reads  | not run       | not run               | fails: 1  | passes  |

- "Read before each page" mutates the code without the fix: the jobs are read before
  the first page and before each `next()`.
- "No skip" mutates the fix: the jobs are read for a page of delivered messages too.
- C6, the skipped read, was added after the first two variants were run.
- The probe, 14 cases, passes with the fix; it is deleted from the worktree.

A sixth case pins the skipped read: a run over a shard of delivered messages, kept for
deduplication, reads no jobs, and the run must happen. `RunResultSpec` covers
`shouldRunAgain()` in five cases, through named arguments.

## Verification

- [x] `CatchUpJobsPerPageIgTest`: all cases pass with the fix, `--no-build-cache`, read from
      the XML report. The table above for the other variants.
- [x] With the fix: `CatchUpStationSpec` 11, `CatchUpStationTest`, `InMemoryCatchUpTest` 10,
      `InMemoryDeliveryTest` 17, `RepeatedCatchUpIgTest` 2 — all passed.
- [x] `./gradlew check dokkaGenerate`, before the review changes: 2108 tests in `:server`,
      0 failed, 1 skipped (`EventReactionRoutingSpec`, `@Disabled`). Two earlier runs failed:
      detekt `ReturnCount` in the watchdog, and C4 when run after `CatchUpTest` — see
      "Noticed along the way". Both fixed.
- [x] `./gradlew check dokkaGenerate` after the review changes: BUILD SUCCESSFUL; 2113 tests
      in `:server`, 0 failed, 1 skipped (the same `@Disabled` case);
      `CatchUpJobsPerPageIgTest` 6 and `RunResultSpec` 4 passed; detekt clean.
- [x] Reviews:
  - `kotlin-engineer`: approved. Applied: a failing hook is recorded (the hook runs inside
    the `try`); a `Hook` class instead of a `Pair`; `stopped` as a flag; `isEvent` bound to
    `EventMessage`, without `crossinline`; `nextReadAction`; `/* multitenant = */ false`;
    the KDoc of the class and of `useDelivery()`; why `SEPARATE_THREAD`; the
    `@Suppress("TooGenericExceptionCaught")` dropped — detekt is clean without it.
  - `spine-code-review`: approved with changes. Applied: the Javadoc of the `RunResult`
    constructor; a sticky `changed` flag; `jobsOfRun`, `hasUndelivered`, `messages`;
    the backticked subject in `@DisplayName`; `storage`; a case for the skipped read;
    `RunResultSpec`. Declined: a merge function for duplicate job IDs — the storage is
    keyed by ID, so a duplicate breaks its contract, and failing loudly is right.
  - `review-docs`: approved with changes. Applied: `shouldRunAgain()` reworded; the claim
    "no station acts on such messages according to a job" replaced, see "Landing";
    the two sentences of `CatchUpProcess`; "held under", "the statuses of the catch-up jobs",
    "the previous read"; the KDoc of the test states the contract instead of the history.
  - Pre-PR round, on the committed branch: `review-docs` approved; `spine-code-review` and
    `kotlin-engineer` approved with changes. Applied on 2026-10-08, with the user's
    consent: a case for a stopped run whose jobs changed; named arguments for `RunResult`
    in its spec, through a helper; the sixth case asserts that the run happened; doc
    nits, `previous`, and prose instead of a link to the `@Internal` `InboxMessageStatus`.

## Landing

C2 needs a second catch-up of the same instances to complete, so its test needs `jobFor()`.

**Decided on 2026-10-07:** this branch is based on PR #1683's branch,
`claude/dazzling-noyce-92ab0c`, and its PR targets that branch. The user asked for it; the
branch was fast-forwarded from `7b64d415975` to `b53637bf21a`, the head of PR #1683.

With the fix, `RepeatedCatchUpResetIgTest` of the guard can no longer set up its scenario:
its precondition, "the start signals, delivered and kept by the completed job", fails.
Without that precondition, its scenario ends correct. Whichever of the two lands second
adapts it, for instance into a check that a completed job no longer delivers the start
signals of a new one. The guard and its `CatchUpStationSpec` case stay valid, as defense
in depth. Do not drop the guard as redundant: on this branch, `started()` still dispatches
a delivered `CatchUpStarted` again. The fix only keeps such copies from arising, and skips
the jobs for a page of delivered messages; a page that also holds a message to deliver
still reaches `started()`.

Both branches would bump the version to `.565`; the second to merge bumps again.

## Noticed along the way

- In a livelocked run, `runDelivery()` keeps a `DeliveryStage` per page, so memory grows
  until the run is stopped. The other task saw such a run end in an `OutOfMemoryError` in
  `Origin.validate()` after 4.5 h. Moot once runs no longer livelock.
- The tests run system events synchronously; C1–C4 did not depend on it.
- `CatchUpTest` switches the static mode of `ConsecutiveProjection` to negatives and does
  not switch it back. C4 passed alone and failed in the full `:server:test` (the projection
  got no events) until it set the mode itself.
- From the review, not addressed here:
  - with `jobFor()`, a new job on the same targets takes over the paused messages of
    a completed job that are not delivered yet — the PR #1683 follow-up "paused messages
    of the old job may be applied twice";
  - a `CatchUpStorage` whose reads lag behind its writes can return an older status;
    keeping the highest status per job within a run would guard against it.
