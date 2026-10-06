# `InboxStorage`: no monitor during storage I/O, and no message left behind by the pages

Related: [SpineEventEngine/core-jvm#1678][issue-1678] — the same class of problem,
in `RepositoryCache`. There is no issue for this one yet.

Two changes shipped together: removing the monitor of `InboxStorage`, and fixing the page
cursor of `Delivery` — see "Fix of the page cursor".

## Status

Both plans approved on 2026-10-05. Implemented in the working tree; not committed.

- [x] Code and history read — see "What the code says"
- [x] Reproduction: `InboxStorageSpec`, 3 cases, passed against the unchanged class
- [x] The fix tried in a scratch copy outside the repository — see "What was run"
- [x] Independent review of the claim that nothing relies on the monitor
- [x] Plan approved
- [x] `InboxStorage`: the three `synchronized` modifiers dropped, a Javadoc paragraph added
- [x] `InboxStorageSpec` inverted — see "Tests"
- [x] Version: the fix for issue #1678 (PR #1680) reached `master` first, at `.561`;
      this branch merged `master`, bumped to `.562`, and then to `2.0.0-SNAPSHOT.570` —
      see "Version and reports"
- [x] `./gradlew build dokkaGenerate` is green; the dependency reports are regenerated
- [x] Reviews by `spine-code-review`, `kotlin-engineer` and `review-docs`; their findings
      are applied — see "Reviews of the change"
- [x] Decide whether the change ships before, with, or after a fix of the page cursor —
      decided: fix the cursor first, ship both together
- [x] Cursor fix: `InboxStorage.readToDeliver()`, the look-back in `Delivery`, `DeliverySpec`,
      a contract case in `InboxStorageTest` — see "Fix of the page cursor"
- [x] Cursor fix verified: mutations, the delivery suites, `./gradlew build dokkaGenerate`
- [x] Reviews of the cursor fix: `spine-code-review`, `kotlin-engineer`, `review-docs`;
      their findings applied, verification repeated
- [x] Commits, pushed; PR #1682 opened after `pre-pr` passed
- [x] Second review round, by the `pre-pr` reviewers: the should-fixes applied in one
      follow-up commit

## Problem

`InboxStorage` (`server/src/main/java/io/spine/server/delivery/InboxStorage.java`)
declared `write(InboxMessage)`, `writeBatch(Iterable)` and `removeBatch(Iterable)` as
`synchronized`. Each of them only forwards to the underlying `RecordStorage`, so
the monitor was held for exactly as long as the storage call took.

A `Delivery` has one `InboxStorage`, and a `ServerEnvironment` has one `Delivery`.
So these calls queued behind one monitor, JVM-wide:

- every inbox write: `InboxPart.store()` → `NotifyingWriter.write()` → `write()`;
- every conveyor flush: `Delivery.launch()` → `Conveyor.flushTo()` → `writeBatch()`,
  then `removeBatch()`. A page is up to 500 messages by default.

A thread posting a signal to an entity of one shard thus waited for the storage I/O
of a flush of another shard.

**The mechanism is reproduced. Its cost in production is not measured.** The numbers
of issue #1678 are about the cache monitor. With the cache override in place they
show almost no monitor blocking left in that deployment (0.2% of the slow delivery
pages), which does not suggest a second bottleneck of that size there. The data was
not collected to answer this question, though.

## What the code says

Read at `d6a73060f96`, before the fix.

- **Nothing but the storage call ran under the monitor.** `write()` and
  `writeBatch()` call `MessageStorage`, which converts the messages to records and
  calls `DelegatingRecordStorage` → the record storage. `removeBatch()` maps the
  messages to identifiers and calls `deleteAll()`. `NotifyingWriter` notifies the shard
  observers after `write()` returned, so no delivery was nested into the monitor.
  The monitor was never re-entered.
- **Nothing else locks on the storage.** There is no `synchronized` block on
  an `InboxStorage` anywhere in the repository. The reading methods are not
  synchronized, so the monitor never isolated readers from writers.
- **Writers do not share records.** `InboxPart.store()` gives each message a new
  UUID-based identifier. A `Conveyor` is confined to one thread and only touches
  the messages of the shard this thread picked up through `ShardedWorkRegistry`,
  which hands a shard to one worker at a time.
- **The monitor never ordered the messages.** `whenReceived` and `version` are
  assigned in `InboxPart.store()` before the write.
- **The monitor never made a flush atomic.** `flushTo()` took it twice, once for
  the written batch and once for the removed one. In a multi-node deployment the other
  nodes were not bound by it at all.
- **The in-memory storage synchronizes itself.** `TenantRecords` keeps the records
  in a `synchronizedMap` and runs the queries under its lock. `MultitenantStorage`
  creates the slices under a lock.
- **Record conversion is stateless.** `RecordSpec` has only final fields and builds
  a new map on each call. The getters of `InboxColumn` are method references.

### Where the monitor came from

- 2019-11-05, `a784ad6b0a3`: all the methods of `InMemoryInboxStorage` — at that time
  the in-memory implementation of the `InboxStorage` *interface* — became
  `synchronized`. Its map went from a concurrent one to `synchronizedMap(HashMap)`
  in the same commit.
- 2019-11-21, `00b9f3d5216` and `c045d97e23f`: the reading methods were released, and
  the reason was written down: "Mutating operations are made `synchronized` to avoid
  simultaneous updates of the same records. That allows to operate in a concurrency-heavy
  environment notwithstanding the thread-safety of the underlying storage."
- 2020-03-28, `976f411e6ed` "Introduce `MessageStorage`": `InMemoryInboxStorage` was
  deleted, `InboxStorage` became a class over a record storage of any vendor, and
  the modifiers moved onto it. The explanation did not.

So the monitor was the thread-safety measure of one in-memory class. It ended up
in front of every backend by a refactoring, not by a decision.

## What was run

All with JDK 17 (Corretto 17.0.19) on macOS.

Before the fix, in the working tree:

- The reproduction form of `InboxStorageSpec` passed, 3 of 3. Each case held a thread
  inside the record storage and asserted — through `ThreadMXBean` — that a thread
  writing another message was `BLOCKED` at the monitor of this `InboxStorage`, owned
  by the held thread.
- `./gradlew :server:detekt` passed. It covers the test sources.

In a scratch copy of the sources outside the repository, with and without the fix:

| What | Result |
|---|---|
| Reproduction spec, each case 200 times, unchanged class | 600 of 600 pass |
| Reproduction spec, the three modifiers removed | 3 of 3 **fail** |
| Inverted spec, modifiers removed, each case 200 times | 600 of 600 pass |
| Inverted spec, unchanged class | 3 of 3 fail |
| Inverted spec, `synchronized` left on one method at a time | 1 of 3 fails |
| `io.spine.server.delivery.*`, modifiers removed, 5 runs | 127 of 127 pass, each run |
| The whole `:server:test`, modifiers removed | 2,048 tests, 0 failed, 1 skipped |

- This is what the JVM reported in the first row. Thread 27 is the one parked in
  the record storage, in `GatedRecords.deleteAll()`:

  ```
  "test-worker" Id=28 BLOCKED on io.spine.server.delivery.InboxStorage@156cfa20
      owned by "test-worker" Id=27
  ```

- In the second row the other writer ran to completion while the first thread was
  held. So the reproduction was able to fail: it told a storage with the monitor
  from a storage without one.
- In the fourth and the fifth rows the cases fail with "The worker did not complete
  in time." In the fifth, the failing case is the one for the method that kept
  the modifier — for each of the three methods.
- The rows on the inverted spec were run again with its final text, after the reviews.
- The delivery suites include the multi-thread ones: `InMemoryDeliveryTest`,
  `InMemoryCatchUpTest` and `ReceptionFailureTest`.
- The skipped test is `@Disabled` in the sources.

After the fix and the reviews, in the working tree:

- `./gradlew :server:test --tests 'io.spine.server.delivery.InboxStorageSpec'` —
  the inverted spec passes, 3 of 3.
- `./gradlew build dokkaGenerate` is green. The tests of all the modules ran: 2,048
  in `server` (0 failed, 1 skipped), 180 in `core`, 189 in `client`, 159 in
  `server-testlib`, 25 in `core-testlib`, 2 in `client-testlib`, 22 in `server-otel`.
  Checkstyle, PMD, detekt and both Dokka publications ran for `server`. Detekt passed
  on 169 Kotlin files, the test sources included.
- The dependency reports differ from `master` by the version and the dates only.

## Independent review of the idea

Before the plan was presented, a separate reviewer got the code and the proposal, not
the conclusions above, and was asked to break the proposal by reading. It found no
dependency on the monitor in production code. The one dependent was the reproduction
form of the spec. It ran nothing, and did not read the vendor storages, the `delivery`
repository or the multi-thread delivery suites — which are the suites that pass in
the runs above.

The weaknesses it found exist with or without the monitor. They are listed under
"Noticed along the way".

## Not verified

- **The cost of the monitor in production.** See "Problem".
- **Thread safety of the vendor record storages** (Datastore, JDBC) under concurrent
  inbox writes. Inferred from the event store: `DefaultEventStore.store()` already
  appends events from several threads into one `RecordStorage` through
  `MessageStorage.write()`, with no monitor in between. So a record storage that is not
  thread-safe would be broken today. Entity records are not such an example yet:
  within one repository their writes are serialized by the cache monitor of issue #1678.
  The vendor code was not read.
- **Subclasses of `InboxStorage` outside the organization.** `write()` and
  `writeBatch()` are `protected`, and the class is an SPI.
- **IntelliJ IDEA inspections** were not run.

## Design

### 1. `InboxStorage.java`

`synchronized` is removed from `write(InboxMessage)`, `writeBatch(Iterable)` and
`removeBatch(Iterable)`. The two overrides stay: they exist to expose the methods
to the `delivery` package.

One paragraph is added to the class Javadoc:

```java
 * <p>A {@link Delivery} serves all its shards with a single instance of this storage, so
 * messages may be written and removed by several threads at once. Subclasses should not
 * serialize that access, and the underlying record storage must be thread-safe.
```

This is not the text of the approved plan. That one began with "The storage takes
no lock of its own", which all three reviewers found untrue: the inherited two-argument
`write(id, message)` is still `synchronized` in `MessageStorage`.

The copyright header of the file was replaced by the copyright hook, from the profile
selected for the repository on 2026-10-02.

### 2. Version and reports

`version.gradle.kts` → `2.0.0-SNAPSHOT.570`. The fix for issue #1678 (PR #1680) reached
`master` first, at `.561`, so the branch was bumped to `.562`. Then the change was classified
as breaking, which the version policy rounds up to the next multiple of ten: a storage that
reads its messages from elsewhere than the record storage, like `RemoteInboxStorage` of
the `delivery` repository, fails until it overrides the new `readToDeliver()`.
The dependency reports are regenerated by the full build.

## Tests

`server/src/test/kotlin/io/spine/server/delivery/InboxStorageSpec.kt`.

The spec was first written to reproduce the defect, and said so: `make a write of
another message wait` / `while a message is being written`, `while a batch is being
written`, `while a batch is being removed`. It asserted the `BLOCKED` state of
the second writer through `ThreadMXBean`.

The fix inverted it into `serve other messages` with the same three cases. Each
holds one of the three methods inside the record storage, and requires another thread
to complete `write()`, `writeBatch()` and `removeBatch()` on other messages meanwhile.
Holding every method and calling every method is what pins each modifier separately.
Each case also checks that the held operation takes effect once it is let go.
The `ThreadMXBean` code is gone with the reproduction.

The gate holds a thread for twice as long as a test waits for a worker. With equal
limits the two give up at the same moment, and a case behind a monitor fails with
`expected:<false> but was:<true>` instead of "The worker did not complete in time." —
seen in the first round of the mutation runs.

The specs use `Gate` and `Worker` of `io.spine.server.entity.given.concurrency`, which
came with the fix for issue #1678 (PR #1680). Until that was merged, the specs had copies
of their own. The hold limit moved into the shared `Gate` with the merge.

`GatedStorageFactory` (`server/src/test/kotlin/io/spine/server/delivery/given/`) stubs
the record storage for both specs, following the pattern of `FailingHistoryFactory`:
the in-memory storage wrapped in a `DelegatingRecordStorage` that stops at a latch.
A test holds the operations on a record with a given ID, or the next write of any record.

## Reviews of the change

All three verdicts were "approve with changes". Everything below is applied, and
the build and the mutation runs were repeated afterwards.

- **The Javadoc paragraph** — all three. See "Design".
- **`kotlin-engineer`:** named arguments for the three-parameter constructor of
  `GatedRecords`; `try`/`finally` around the body of `assertServesDuring()`, so that
  a failing case opens the gate instead of leaving a thread parked; a clue on the check
  that the holder is still held; `Gate.awaitReached(by)` rethrowing the failure of
  a worker that never arrived, as the helper of issue #1678 does now; the KDoc of
  `GatedRecords` naming the three methods it gates.
- **`spine-code-review`:** the check that the held write lands, in the two write cases;
  the note on the hold limit in the `TODO`.
- **`review-docs`:** wording of the KDoc in the spec and of this file.

## Behavior changes

1. Inbox writes and conveyor flushes no longer wait for each other — the fix.
2. The record storage behind an `InboxStorage` is called concurrently for writes and
   removals. Worth a line in the release notes, as for issue #1678.
3. **Not intended:** within one JVM, a slow inbox write no longer holds back the writes
   that start after it. A message written later can therefore become readable first,
   which opens one more way into the out-of-order delivery described under "Delivery
   order". Across nodes that way was always open.

## Delivery order

Investigated on 2026-10-05, after the reviewer's note on stamping. Reproduced in
a scratch copy with a throwaway spec (`DeliveryOrderSpec`, kept in the session
scratchpad), which drives a real `Delivery`, `InboxStorage`, `Conveyor` and
`InboxPage` with a stub endpoint. Each scenario was run against both the original
and the fixed `InboxStorage`.

### The cause: a time cursor

`InboxPage` reads a shard page by page. Each next page asks for the messages with
`received_at` strictly greater than that of the last message read
(`InboxStorage.readAll(index, sinceWhen, pageSize)`). This is correct only if
the messages become readable in the order of their stamps, and if no two messages
share a stamp. Neither is guaranteed:

- A message is stamped in `InboxPart.store()` before it is written. A message stamped
  earlier but readable later than one already read falls behind the cursor.
- Two stamps can be equal. `Time.currentTime()` gives distinct values within one JVM
  for up to 1,000 calls per millisecond (`IncrementalNanos`), but every JVM starts its
  sub-millisecond counter at zero in each millisecond, so two nodes stamping in the same
  millisecond can collide. A frozen clock in tests makes all stamps equal. Read in
  `Time.java` of `base-libraries`, not run.

### What was reproduced

| # | Scenario | Original class | Fixed class |
|---|---|---|---|
| 1 | Slow write; a later write is read meanwhile | blocked by the monitor | `Y:1, X:2, X:1` |
| 2 | Stamped before, written after another is read | `Y:1, X:2, X:1` | same |
| 3 | A page ends inside equal stamps: storage | the rest not returned | same |
| 4 | The same, window 30 s, page 2: three calls | `X:3` not delivered | same |
| 5 | The same, window 500 ms: calls past it | `X:3` on the 2nd call | same |
| 6 | Row 4 with the synchronous local observer | `StackOverflowError` | same |

Row 2 sets the clock instead of using threads. Row 6 failed after about 1,400 nested
delivery runs.

- In the first two rows, the target `X` receives its second message first. Its first
  message is skipped by the cursor and delivered by the next run of the same
  `deliverMessagesFrom()` call. It is late and out of order, not lost.
- In the equal-stamp rows, the delivered messages are kept for the deduplication window
  and fill the pages. The page boundary falls at the same place on every run, so
  the message after it waits until the kept ones expire and are cleaned up.
- With the synchronous observer, `deliverMessagesFrom()` ends by finding the waiting
  message with `newestMessageToDeliver()` and notifies the observer, which starts
  the delivery again in the same thread — which finds the same message. The recursion
  ends with a `StackOverflowError` thrown from `Inbox.send()`. `Delivery.local()` is set
  up this way: a synchronous observer and a 30 s window, with pages of 500 messages.

### What it means for this change

- The cursor defect predates this change and does not depend on the monitor: five of
  the six scenarios behave the same with the original class.
- The monitor did close one way in, within one JVM: a slow write held back the writes
  that came after it. Removing the monitor opens it. With the monitor, a writer could
  still fall behind between being stamped and entering the monitor — the monitor is
  not fair, and the thread can be preempted — but that was not reproduced.
- The earlier note in this file that the change "should narrow" the window was wrong.
  It counted only the time a writer waited for the monitor.
- How often either way occurs in production is not known.

## Fix of the page cursor

Decided on 2026-10-05: fix the cursor first, then ship it together with the monitor removal.

### Design: deliver what the pages left behind

After the next page is read, and before it is delivered, `Delivery` reads the messages
still `TO_DELIVER` that were received no later than the last message of the page delivered
last, and delivers them first, in stages of at most a page (`Delivery.deliverLeftBehind()`).
This step is called the *look-back* below.

- **After the read.** A message of the next page, stored after another message of
  the shard, can only be read once that message is readable too. So the look-back finds
  every message stored before a message of that page. Done before the read, it would
  miss one.
- **`TO_DELIVER` only.** After a stage, no message stays `TO_DELIVER`: it is delivered,
  removed, or turned `TO_CATCH_UP` by `CatchUpStation`. So the look-back never returns
  a message twice, and each round makes progress. `TO_CATCH_UP` messages are held on
  purpose while a catch-up is `FINALIZING`.
- **Equal stamps** pass the `<=` test, so they are found within the run. With that,
  the recursion of the synchronous observer loses its trigger.
- **Stage size** stays within the page size, as `DeliveryBuilder.setPageSize()` documents
  and `DeliveryTest` checks.
- The look-back is in `Delivery`, not in `InboxPage`: the `delivery` repository pages with
  its own copy of `InboxPage`, which `Delivery` drives the same way.

The new SPI method `InboxStorage.readToDeliver(ShardIndex, Timestamp)` runs the query:
the shard, `TO_DELIVER`, `received_at <=` the given time, sorted by `received_at`
descending, then sorted in memory, older first and ties by version. The descending sort is
the one of
`newestMessageToDeliver()`, so a Datastore deployment can serve both from one composite
index — inferred from Datastore's index rules, not run against Datastore.
`NoOpInboxStorage` returns an empty list.

### Tests

- `DeliverySpec` (Kotlin, new, 9 cases) — a real `Delivery` over an `InboxStorage` stub,
  which can hold a write inside the record storage and run an action after the next
  look-back:
  - a message stored after a later one has been read; a slow write overtaken by a later
    one; messages stored right after a look-back;
  - a full page ending among equal stamps, also with the synchronous observer;
  - five messages left behind, delivered in stages of 2, 2, 1; the monitor stopping
    the delivery after the first of those stages;
  - a message left behind for a target under a catch-up: dropped while it is
    `IN_PROGRESS`, turned `TO_CATCH_UP` while it is `FINALIZING`.
- `InboxStorageTest` (Java, the contract the storages of the vendors run) — one case for
  `readToDeliver()`, ties by version included. Added to the existing suite, as that is
  the one place the Datastore and JDBC builds run.
- After merging `master`, both specs use the shared `Gate` and `Worker` of
  `io.spine.server.entity.given.concurrency`, which now hold a thread for twice as long
  as a test waits. The copies of the specs are removed.

### Verification

Repeated after the reviews, on the final sources.

- `DeliverySpec` 9 of 9, `InboxStorageSpec` 3 of 3, `InMemoryInboxStorageTest` 12 of 12.
- In the scratch copy, each break of the fix fails the cases meant to catch it:

  | Variant | Failing cases |
  |---|---|
  | The fix as written | none, 38 of 38 pass |
  | The monitor put back | the threaded case of `DeliverySpec`; the 3 of `InboxStorageSpec` |
  | No look-back | 5 cases of `DeliverySpec`, one with a `StackOverflowError` |
  | Look-back before the next page is read | the case of the messages stored after a look-back |
  | `<` instead of `<=` | both equal-stamp cases; the contract case |
  | No ascending re-sort | both cases of the stages; the contract case |
  | No status filter | the contract case |
  | Stop of the monitor ignored in the look-back | the case of the monitor stopping |

  The second row backs a split into two commits: the cursor fix passes with the monitor
  still in place, apart from the cases of the monitor removal itself.
- `io.spine.server.delivery.*`, 3 runs before the reviews: 134 of 134 each. The 32 suites
  shared with the runs before the fix took 10.1 s in each run, against 10.5–13.1 s before:
  no measurable cost with in-memory storage. The cost on a real storage — one more query
  per page, normally returning nothing — is not measured.
- `./gradlew build dokkaGenerate` is green on the final sources: 2,058 tests in `server`
  (0 failed, 1 skipped), all the other modules as before; Checkstyle, PMD, detekt and both
  Dokka publications ran for `server`.

### Follow-ups in other repositories

- **`delivery`:** `RemoteInboxStorage` inherits the default `readToDeliver()`, which ends in
  `RemoteRecordStorage.readAllRecords()` and throws. Before it moves to this core version,
  it needs a new RPC and an override. It is pinned to core `.523`, so nothing breaks before.
- **`gcloud-jvm`:** its published inbox indexes (`datastore/config/*.yaml`) are already out
  of date: they cover neither `readAll()` nor `newestMessageToDeliver()`. The new query
  should need no index of its own.
- **`jdbc-storage`:** nothing; the contract case runs there on upgrade.

### Still open

- `TO_CATCH_UP` and kept `DELIVERED` messages beyond a page ending among equal stamps are
  still read only by a later run. That delays a catch-up message or a cleanup; it does not
  reorder live messages.
- While a catch-up is `COMPLETED`, `CatchUpStation` drops a live message only if its
  replayed copy is on the same conveyor, before it; `LiveDeliveryStation` then covers
  a replayed copy delivered earlier, through the cache of the delivered messages. A live
  message delivered in one stage and its replayed copy in a later one are both delivered.
  This already happens when the two fall on different pages. The look-back adds a case:
  a live message left behind while its replayed copy is on the next page. Read, not
  reproduced; found by the review of the fix.

### Behavior changes of the fix

1. A message is no longer delivered after the later messages of its target because it
   was stored late, or because a page ended among messages received at the same time.
2. A `DeliveryMonitor` sees more stages: one per portion of the messages left behind.
   Worth a line in the release notes.
3. `InboxStorage` has a new SPI method, `readToDeliver()`. Storages with their own reading,
   like `RemoteInboxStorage` of the `delivery` repository, have to implement it. This is
   why the version is `.570`.

## Other repositories

Read on the default branches at GitHub on 2026-10-05.

- **`SpineEventEngine/delivery`, `RemoteInboxStorage`** overrides `write()` and
  `writeBatch()` *without* `synchronized`, suppressing the inspection with "Delegating
  to gRPC client". So the nodes using it already wrote concurrently, and only
  `removeBatch()` — package-private, not overridable — still held the monitor there,
  during a gRPC call. The fix releases it. The two suppressions become redundant.
- **`SpineEventEngine/delivery`, `ExtendedInboxStorage`** has `synchronized` overrides
  of its own: `writeBatch()`, `delete()`, `deleteAll()`, `read()` and the paged
  `readAll()`. `InboxService` calls these and the two-argument `write(id, message)`, which
  is `synchronized` in `MessageStorage`. It calls neither `write(InboxMessage)` nor
  `removeBatch()`. So the fix changes nothing for the Delivery server, and that server
  keeps serializing nearly all its inbox operations by its own code. The new Javadoc
  of `InboxStorage` now advises against exactly that.
- **`SpineEventEngine/gcloud-jvm`** has no subclass of `InboxStorage`. The stock
  class is used, with `InboxStorageLayout` for the Datastore entity groups.

## Considered and not done

- **A lock per shard or per message.** There is nothing to protect: see "Writers do
  not share records". A lock per shard would still make every inbox write to a shard
  wait for the flush of that shard.
- **`synchronized` on the two-argument `write(id, message)`** of `MessageStorage`,
  `EntityRecordStorage`, `EntityEventStorage`, `EntityStateHistoryStorage` and
  `NoOpInboxStorage`. It is the same kind of monitor. Inside this repository it is
  cold: the production code calls the one-argument or the `RecordWithColumns`
  overloads. It is hot in `InboxService.writeOne()` of the `delivery` repository,
  next to the synchronized methods of `ExtendedInboxStorage`. Whether that server
  relies on the exclusion is not known, so this goes with a change in that repository.
- **A concurrency case in `InboxStorageTest`**, the contract the vendors run against
  their storages. It would turn the inference about vendor storages into a check
  in their builds. A separate change, with its own PRs downstream.

## Noticed along the way — out of scope

- `NoOpInboxStorage` overrides the two-argument `write()` to do nothing, but
  `NotifyingWriter` calls the one-argument one, which reaches the in-memory storage
  of the singleton. A probe in the scratch copy wrote 3 messages and found 3 records.
  So `Delivery.direct()` appears to retain every dispatched signal. The path
  through `Delivery` was read, not run.
- `Gate.pass()` and `Worker.result()` of the issue #1678 helpers shared one limit, with
  the effect described under "Tests". Fixed with the merge: the shared `Gate` holds for
  twice the wait limit.
- Found by the independent reviewer:
  - The page cursor and the stamping of messages — investigated and reproduced, see
    "Delivery order".
  - `InMemoryShardedWorkRegistry.release()` clears whichever session is recorded for
    the shard, not necessarily the caller's. If two workers ever end up on one shard,
    they flush the same messages. With the monitor each of their batches landed whole;
    now the records of two such batches may interleave. Nothing was found that needs
    them whole, and a second node was never bound by the monitor.
  - `AbstractStorage.open` is a plain field, and `close()` takes no lock.
  - `TenantRecords.index()` returns a live key iterator, which a concurrent write can
    break. Its only caller found is a test.

[issue-1678]: https://github.com/SpineEventEngine/core-jvm/issues/1678
