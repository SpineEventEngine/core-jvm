# Move the event journal from `AggregateStorage` to `AggregateRepository`

## Goal

Remove the unnecessary delegation level: today `AggregateStorage` extends
`EntityRecordStorage` *and* wraps an `EntityEventStorage` (the event journal).
Since aggregates are no longer event-sourced, the journal is not part of the
state storage's job. Make the `EntityEventStorage` a property of
`AggregateRepository` instead, so the repository serves events to its
aggregates — mirroring how it already owns and serves the *state history*
(`EntityStateHistoryStorage`).

This sets up the follow-up (Phase F of the de-event-sourcing plan): the same
"repository owns the journal + state history" shape is then propagated to
`ProcessManagerRepository`.

## Why this is safe / small

- `IdempotencyGuard` and `Aggregate.eventHistoryBackward(int)` read the journal
  **through the installed `EventHistoryLoader`**, never through storage — so
  they are unaffected.
- Only **two** production call sites touch the storage's journal methods, both
  inside `AggregateRepository`:
  - `setUpHistoryReading()` — installs the loader:
    `aggregate.setEventHistoryLoader(depth -> aggregateStorage().eventHistoryBackward(id, depth))`
  - `doStore()` — `aggregateStorage().writeAll(aggregate, history.get())`
- The journal's full behavior (write/clear-enrichments/read-window/truncate/
  delete) is already covered directly by `EntityEventStorageSpec` (in-memory),
  so the journal tests currently routed through `AggregateStorageTest` are
  redundant duplicates.

## Design decisions

1. **Repository owns the journal, created lazily via a synchronized accessor**,
   mirroring `stateHistoryStorage()`. The journal is first touched on the first
   dispatch (a delivery-worker thread), the same reason `stateHistoryStorage()`
   is `synchronized`. Unlike state history it is *always on* (no opt-in gate),
   so a single `protected final synchronized EntityEventStorage<I> eventStorage()`
   both creates and exposes it — `protected` preserves the `truncate(...)`
   maintenance reach that the public `AggregateStorage.truncate` gave (parity
   with the `protected stateHistory()` accessor).
2. **Journal write stays in `doStore()`** (cache-deferred), not `store()`:
   under batched delivery the aggregate accumulates *all* uncommitted events
   until `commitEvents()`, so the journal drops nothing. (State history stays in
   `store()` because it snapshots per dispatch.)
3. **`AggregateStorage` loses its `close()` override** — it reverts to the
   inherited `EntityRecordStorage.close()`. The journal is closed by the
   repository, alongside `closeStateHistory()`. The prior "preserve the journal
   close exception as primary" logic dissolves: the two storages are no longer
   both owned by one `AggregateStorage`.

## Changes

### 1. `server/.../aggregate/AggregateStorage.java` — strip the journal
- Remove field `eventStorage` and its construction in the constructor.
- Remove `readEvents(I,int)`, `readEvents(I)`, `writeEvent(I,Event)`,
  `writeAll(Aggregate,List<Event>)`, `eventHistoryBackward(I,int)`,
  `eventHistoryBackward(I,int,Version)`, `truncate(Timestamp)`,
  `close()` override, `checkNotClosedAndArguments(...)`.
- Keep: `extends EntityRecordStorage`, `enableStateQuerying()`,
  `readStates(...)` ×3, `ensureStatesQueryable()`, `writeState(Aggregate)`.
- Prune now-unused imports (`ImmutableList`, `Timestamp`, `Event`, `Version`,
  `EntityEventStorage`, jspecify `Nullable`, `List`, `checkNotNull`,
  `DEFAULT_HISTORY_DEPTH`, `checkPositive`).
- Rewrite the class Javadoc to describe only the latest-state storage (drop the
  "Journal of the emitted events" and "Legacy journal" sections).

### 2. `server/.../aggregate/AggregateRepository.java` — own & serve the journal
- Import `io.spine.server.entity.storage.EntityEventStorage`.
- Add field `private @MonotonicNonNull EntityEventStorage<I> eventStorage;`
- Add `protected final synchronized EntityEventStorage<I> eventStorage()`
  (lazy create via `defaultStorageFactory().createEntityEventStorage(...)`).
- `setUpHistoryReading()`: loader now reads
  `eventStorage().historyBackward(id, depth)`.
- `doStore()`: replace `writeAll(...)` with
  `events.forEach(eventStorage()::write); aggregateStorage().writeState(aggregate);`
  (events first, then state — same order as the old `writeAll`).
- `close()`: add `closeEventStorage()` next to `closeStateHistory()`.
- Add `private synchronized void closeEventStorage()` (null- and open-guarded).
- Touch up class Javadoc: the repository owns the event journal.

### 3. Tests
- `testFixtures/.../aggregate/AggregateStorageTest.java`: remove all
  journal-specific tests/helpers (`emptyHistory`, `emptyEvents`,
  `NotAcceptNull`, `WriteAndReadEvent`, `throwOnReadEventsWhenClosed`,
  `WriteRecordsAndReturn`, `WriteRecordsAndLoadHistory`, `TruncateJournal`,
  `NotStoreEnrichment`, and the `writeEvent/readEvents/eventHistoryBackward/
  eventFactoryFor/newEventId` helpers + `TestAggregateWithId{String,Long,Integer}`).
  Keep the state-storage contract (`AbstractStorageTest` overrides,
  `absentRecord`, `immutableIndex`, `indexCountingAllIds`, `givenAggregate`,
  `TestAggregate`). Journal behavior remains covered by `EntityEventStorageSpec`.
- `test/.../entity/storage/HistoryStorageIdentitySpec.kt`: the two tests that
  used `createAggregateStorage` as a proxy for "create the event journal" now
  call `createEntityEventStorage` (and, for the mixed test, add an explicit
  `createAggregateStorage` for the group-less latest-state spec). Identity
  assertions are unchanged.
- `testFixtures/.../aggregate/given/repo/ProjectAggregateRepository.java`:
  **left untouched.** Its `customStorage`/`injectStorage(...)`/`aggregateStorage()`
  override is dead (zero callers) but lives in a *published* testFixtures class,
  and the refactor does not break it. Removing published test-fixture API is out
  of scope here — deferred as a separate cleanup.

## Verification
- `./gradlew :server:build` green (proto unchanged → `build`, not `clean build`).
- `dokkaGenerate` for the KDoc/Javadoc link changes.
- Reviewers: `kotlin-engineer`, `spine-code-review`, `review-docs`.
- Version gate (`version-bumped`) — branch already at `.470`, so idempotent.

## Out of scope
- `ProcessManagerRepository` (Phase F, later).
- A shared `EntityEventStorage` test *fixture* for storage vendors (vendor repos
  previously got journal coverage via `AggregateStorageTest`; that migration is
  a Phase-G concern, tracked separately).
