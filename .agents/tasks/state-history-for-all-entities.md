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

## Prerequisite: `AbstractEntity` → Kotlin (decided 2026-07-17)

The entity-side landing class converts to Kotlin **before** the move
(owner, 2026-07-17): the codebase is heading to Kotlin anyway — take the
step incrementally, instead of rewriting the moved Kotlin members back
into Java. Precedent: `TransactionalEntity` (#1644) and `Transaction`
(#1645) were converted as their own PRs right before PR-B2 needed them.
Ship the conversion the same way — **its own PR, `java-to-kotlin` skill
governs** — so the state-history diff stays a pure member move.

Conversion facts (verified 2026-07-17):

- `AbstractEntity.java` is 644 lines; its only direct subclass in `main`
  is the already-Kotlin `TransactionalEntity.kt`, so the inheritance chain
  becomes Kotlin-extends-Kotlin.
- Package-private members (`updateState`, `updateVersion`,
  `incrementVersion`, …) are called from same-package Java
  (`DefaultConverter`, `StorageConverter`, `Migration`). Kotlin has no
  package-private — these map per the team-memory recipe
  (`java-to-kotlin-visibility-traps.md`: `internal` + `@JvmName` care for
  Java callers; the `HasVersionColumn.getVersion()` clash; and the
  `internal`+`@JvmName` inherited-member ICE, whose cure is `protected`
  where it bites).

## What moves — entity side

To `AbstractEntity.kt` (after the prerequisite conversion — the
`TransactionalEntity.kt` members move Kotlin-to-Kotlin, near-verbatim;
the `Aggregate.java` read API converts to Kotlin while moving):

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
- **Visibility:** `setStateHistoryLoader` keeps its `public @Internal`
  shape, matching the event twin `setEventHistoryLoader`. *(The Java-plan
  idea of narrowing it to package-private is void with the Kotlin
  conversion; Kotlin `internal` was considered and rejected — its caller
  `AbstractEntityRepository.java` would need the mangled name or
  `@JvmName`, and the `internal`+`@JvmName` combination has the documented
  inherited-member ICE. `@Internal` already marks it non-API.)*
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

1. **`afterStore` override discipline — DECIDED (owner, 2026-07-17):
   the `AbstractEntityRepository.afterStore` override is `final`.**
   With the recording logic inside it, a subclass override that forgot
   `super.afterStore(entity)` would silently disable recording — the
   worst failure mode (enabled flag, passing fail-fast reads, nothing
   written). Sealing the override makes the compiler enforce what a
   Javadoc `@implSpec` could only request, and matches the inbox/cache
   pull-up precedent ("all six subclass overrides are `final`" — same
   hazard, same cure). No replacement user hook is added: nothing in-repo
   or downstream overrides `afterStore` today (it is two days old,
   pre-GA); a deliberate `onStored`-style callback can be introduced
   later if a real need appears. `Repository.afterStore` itself stays the
   no-op default for the (empty) set of non-record-based repositories;
   its Javadoc is rewritten from "a callback you may override" to "the
   per-store hook, implemented by `AbstractEntityRepository` to record
   the state history". *(A package-private narrowing was considered and
   rejected: a Java-only trick that becomes a trap when the class
   converts to Kotlin — no package-private there.)*
2. **Version treatment.** The member moves are source- and binary-compatible
   for subclasses, and the visibility narrowing is off the table (see the
   `setStateHistoryLoader` note above). Recommendation: normal bump per
   branch — one for the conversion PR, one for the move PR (the
   `version-bumped` guard handles both).

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

- The prerequisite conversion PR verifies per the `java-to-kotlin` skill
  (build, `dokkaGenerate`, reference updates, its own version bump).
- The move PR: `./gradlew build` (no proto changes expected → `build`, not
  `clean build`) + `dokkaGenerate` (moved KDoc/Javadoc links).
- Reviewers via `pre-pr`: `spine-code-review`, `kotlin-engineer`
  (`AbstractEntity.kt`, the `TransactionalEntity.kt` edits, new Kotlin
  tests), `review-docs`.
- Version gate (`version-bumped`).

## Out of scope

- **Event-journal unification for PMs** (Phase G of the plan) and moving
  the aggregate journal machinery anywhere — separate session. The
  `SignalDispatchingRepository` placement reasoning for the journal (see the
  archived unification task) remains the standing recommendation.
- `IdempotencyGuard` for anything but aggregates (A5/D5 unchanged).
- Kotlin conversion of the touched *repository* classes
  (`AbstractEntityRepository`, `AggregateRepository`) — tracked in
  [`de-event-sourcing-followups.md`](de-event-sourcing-followups.md)
  item 1. (`AbstractEntity` itself is no longer out of scope — its
  conversion is the prerequisite PR above, decided 2026-07-17.)
- Vendor-facing storage changes: none — the
  `createEntityStateHistoryStorage`/`createHistoryStorage` seams already
  take the entity class and are unaffected by who calls them; the vendor
  obligation recorded in the plan's Phase H stands as written.
