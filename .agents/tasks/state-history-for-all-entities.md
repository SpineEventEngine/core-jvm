# Task: State history for all entity types

## Origin

Phase F reshaping (product owner, 2026-07-17): **all entity types obtain
optional access to the state history**, not only the Phase F list of
Process Managers and Projections. Consequently, the machinery generalizes
structurally instead of being wired per repository class:

- the storage-management code now in `AggregateRepository` migrates to
  **`AbstractEntityRepository`** — the base of every entity repository;
- the entity-side read API migrates to **`AbstractEntity`**, mirroring what
  `Aggregate` has today.

Scope split (same decision): **state history only.** The event-journal
unification for PMs (former Phase F items 1, 2, 5 — now Phase G of the
plan) is deferred to a separate session and is untouched here.

This supersedes the *placement* of the former Phase F items 3 and 7 (their write hook,
config surface, and read API now arrive by inheritance) and turns items 3,
4, and 7 into wiring-free verification work. The follow-ups doc's
"journal on `SignalDispatchingRepository`, state history higher" reasoning
is confirmed by this decision — state history lands even higher than
`EventDispatchingRepository`, at the top of the entity-repository tree.

## Why `AbstractEntityRepository` is the right home (verified 2026-07-17)

```
Repository
 └─ RecordBasedRepository<I, E extends Entity, S>          (bound: Entity)
     └─ AbstractEntityRepository<I, E extends AbstractEntity, S>   ← lands here
         └─ EventDispatchingRepository
             └─ SignalDispatchingRepository
                 ├─ ProcessManagerRepository
                 └─ AggregateRepository
```

- It is the **sole** direct subclass of `RecordBasedRepository`, and its
  `E extends AbstractEntity<I, S>` bound is exactly what the loader
  installation needs once the entity seam moves to `AbstractEntity`.
  (`RecordBasedRepository`'s weaker `Entity` bound cannot call the seam.)
- `ProcessManagerRepository` and `ProjectionRepository` have **no**
  `store`/`doStore`/`afterStore` overrides left (verified) — they inherit
  the whole write path from the base, so the hook reaches them with zero
  code in either class.
- `Repository.store(E)` is `public final`, routing cache → `afterStore(E)`
  per call (`be55da92c5`) — the per-dispatch hook Phase E item 2 requires
  (never the cache-flushed `doStore`) already fires for every repository.

## What moves — repository side

From `AggregateRepository` to `AbstractEntityRepository`, names and
visibility unchanged (all `protected`; Java, per the language policy —
edited code stays Java):

| Member                                                                           | Today (`AggregateRepository.java`) |
|----------------------------------------------------------------------------------|------------------------------------|
| `stateHistoryEnabled` flag (volatile)                                            | `:128`                             |
| `stateHistory` storage field                                                     | `:131`                             |
| `recordStateHistory()` / `stopRecordingStateHistory()` / `stateHistoryEnabled()` | `:483`, `:514`, `:493`             |
| `stateHistory()` fail-fast accessor                                              | `:530`                             |
| `createStateHistoryStorage()` overridable seam                                   | `:553`                             |
| `stateHistoryStorage()` lazy synchronized accessor                               | `:574`                             |
| `afterStore(E)` override → `appendStateHistory(E)`                               | `:249`, `:311`                     |
| `stateHistoryLoaderFor(id)`                                                      | `:323`                             |
| `closeStateHistory()` + its `attemptClose` participation in the close chain      | `:720`, `:696`                     |

Generalization deltas while moving:

1. **`appendStateHistory` writes through the converter.**
   Today: `history.write(aggregate.toRecord())`. `Aggregate.toRecord()`
   (package-private, `Aggregate.java:330`) has exactly this one caller, and
   the unification review established the converter builds a field-for-field
   identical record. The generalized form uses the
   `RecordBasedRepository.toRecord(E)` converter path; **`Aggregate.toRecord()`
   is deleted.**
2. **Loader installation moves to the base instantiation points.**
   `AbstractEntityRepository` overrides `create(I)` and `toEntity(EntityRecord)`
   to install the `StateHistoryLoader` (unconditionally, per the Phase E
   locked decision — recording gates only the fail-fast inside the loader).
   `AggregateRepository.setUpHistoryReading(A, I)` keeps only the **event**
   loader + idempotency guard; its `create`/`toEntity` overrides call
   `super` first, which now handles the state side.
3. **The bulk-store hole is fixed at the base** (correctness, not polish).
   `RecordBasedRepository.store(Collection<E>)` (`:233-238`) maps entities →
   `writeAll(records)`, bypassing `afterStore` — and batch
   `applyMigration(Set, Migration)` (`:195-217`) terminates in exactly this
   call, so without the fix a batch migration on a recording repository
   silently skips history entries. Recommended fix: keep the batched
   `writeAll`, then invoke `afterStore(e)` per stored entity — preserves the
   bulk write while firing the hook. (Note: `AggregateRepository`'s own
   `public final store(Collection<A>)` override looping per-entity `store()`
   **stays** — its per-entity `doStore` journaling still depends on it until
   the event-journal session moves the journal up.)

Not moving (stays in `AggregateRepository`, next session's scope):
`eventStorage` + `createEventStorage()` + journal reads/writes/truncation,
`eventHistoryDepth`, `IdempotencyGuard` wiring, the single-dispatch
`doStore` journaling, the bulk-store override.

## What moves — entity side

To `AbstractEntity.java` (written in Java; the Kotlin originals are removed
from `TransactionalEntity.kt`):

| Member                                                       | Today                              |
|--------------------------------------------------------------|------------------------------------|
| `recentStateHistory` field + `recentStateHistory()` accessor | `TransactionalEntity.kt:57`, `:94` |
| `setStateHistoryLoader(StateHistoryLoader)`                  | `TransactionalEntity.kt:119`       |
| `stateAt(Timestamp): Optional<S>`                            | `Aggregate.java:478`               |
| `stateHistoryBackward(int): Iterator<S>`                     | `Aggregate.java:506`               |

- `Aggregate` inherits `stateAt`/`stateHistoryBackward` — **no source or
  binary break** for aggregate authors (a member moved to a superclass stays
  reachable). PMs, Projections, and plain `AbstractEntity` subclasses gain
  the same `protected final` API with no per-class code.
- **Visibility win:** `setStateHistoryLoader` is `public @Internal` today
  only because Kotlin lacks package-private and the caller
  (`AggregateRepository`) sits in another package. After the move both the
  seam (`AbstractEntity`) and its only caller (`AbstractEntityRepository`)
  live in `io.spine.server.entity` — narrow it to package-private. The event
  twin `setEventHistoryLoader` stays as-is (next session).
- The event half (`recentEventHistory`, `EventHistoryLoader`,
  `setEventHistoryLoader`) does **not** move.
- `RecentStateHistory`, `StateHistoryLoader`, `RecentHistory`,
  `HistoryLoader` (all `io.spine.server.entity`, Kotlin) are untouched —
  they were entity-generic from day one (Phase E item 4).

## Semantics preserved (Phase E locked decisions — re-verify by test, do not redesign)

- Opt-in per repository, **off by default**; enable/disable only, no depth
  knob; retention is the application's duty (`truncate`/`trim`).
- Reading while disabled **fails fast** (`stateHistory()` accessor and the
  installed loader); a bare instance outside a repository reads empty.
- Loader installed unconditionally at instantiation; the flag gates only
  behavior.
- Write hook is per-`store()` call (`afterStore`), never `doStore` — the
  batched-delivery cache must not swallow intermediate versions.
- Records keyed by `EntityStateKey` (entity + version): a same-version
  re-write is an idempotent overwrite.

## Behavioral notes for the generalized audience (document, don't block)

1. **The version axis is only as good as the entity's version discipline.**
   Transactional entities (Aggregate, PM, Projection) advance +1 per
   dispatch, so `created` (the `Version` timestamp) and the "state at time T"
   query work as designed. A plain `AbstractEntity` advances its version only
   through `updateState(S, Version)`/migrations; an entity stored repeatedly
   at the same version overwrites one history row. State the discipline on
   `recordStateHistory()`.
2. **Migrations record history**: single `applyMigration` ends in `store()`
   (→ `afterStore`), batch in `store(Collection)` (→ fixed per item 3 above).
   This is desirable — a migration IS a state transition — and deserves a test.
3. **Projection catch-up** replays and re-stores; versions re-advance along
   the same numbers, so history rows are overwritten idempotently rather
   than duplicated. Verify with a test; no code expected.

## Design points needing an explicit call (recommendations inline)

1. **`afterStore` override discipline.** With
   `AbstractEntityRepository.afterStore` carrying the recording logic, a
   subclass override that forgets `super.afterStore(entity)` silently
   disables recording. Recommendation: keep it `protected` non-final with an
   `@implSpec` requiring the `super` call — the exact precedent set for
   `doLoadOrCreate`/`doStore` during the store() unification. (Alternative —
   `final` + a fresh empty hook — churns the just-published `Repository`
   contract for a hazard no in-repo subclass exhibits.)
2. **Version treatment.** The member moves are source- and binary-compatible;
   the only breaking-flavored bit is the `@Internal` `setStateHistoryLoader`
   narrowing. Recommendation: normal bump (the branch guard handles it);
   round-up only if the owner counts the narrowing as breaking.

## Tests (Kotlin, JUnit 5 + Kotest, `Spec` suffix)

- Generalize/relocate `AggregateRepositoryStateHistorySpec` → a base
  behavioral suite in `io.spine.server.entity` exercising a plain
  `AbstractEntityRepository`: off-by-default, fail-fast accessor and loader,
  one record per `store()`, unbounded retention, `stateAt` honest-empty
  cases. Keep a slim aggregate-specific spec proving inheritance (dispatch →
  record appended; `stateAt` from a receptor).
- New: PM repository state history (Phase F item 3 acceptance — per-dispatch
  recording under batched delivery, i.e. intermediate versions retained) and
  "state at time T" against a PM (item 4: verify, don't build).
- New: Projection state history incl. the catch-up idempotent-overwrite case
  (item 7).
- New: bulk-path recording — `store(Collection)` and batch `applyMigration`
  append entries on a recording repository (the hole in item 3 of the
  repository-side deltas).
- Existing `EntityStateHistoryStorageSpec`, `RecentStateHistorySpec`
  unchanged; `StateHistoryTestRepository` fixture likely retargets from
  aggregate to a plain-entity repository (or gains a sibling).

## Plan updates

**Done (2026-07-17, alongside this doc):** the Phase F section of
[`de-event-sourcing-plan.md`](de-event-sourcing-plan.md) is rewritten for
the reshaping and links here; the event-journal items became the new
**Phase G — Entity event history for Process Managers and Aggregates**;
the downstream phases were re-lettered (rollout → H, verification
gate → I).

## Verification

- `./gradlew build` (no proto changes expected → `build`, not `clean build`)
  + `dokkaGenerate` (moved KDoc/Javadoc links).
- Reviewers via `pre-pr`: `spine-code-review`, `kotlin-engineer` (the
  `TransactionalEntity.kt` edits + new Kotlin tests), `review-docs`.
- Version gate (`version-bumped`).

## Out of scope

- **Event-journal unification for PMs** (Phase G of the plan) and moving
  the aggregate journal machinery anywhere — separate session. The
  `SignalDispatchingRepository` placement reasoning for the journal (see the
  archived unification task) remains the standing recommendation.
- `IdempotencyGuard` for anything but aggregates (A5/D5 unchanged).
- Kotlin conversion of the touched Java classes (`AbstractEntity`,
  `AbstractEntityRepository`, `AggregateRepository`) — tracked in
  [`de-event-sourcing-followups.md`](de-event-sourcing-followups.md) item 1.
- Vendor-facing storage changes: none — the
  `createEntityStateHistoryStorage`/`createHistoryStorage` seams already
  take the entity class and are unaffected by who calls them; the vendor
  obligation recorded in the plan's Phase H stands as written.
