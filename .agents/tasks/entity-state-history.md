# Entity state history for Aggregates (`EntityStateHistoryStorage`)

**Status:** in progress on `entity-state-history` (2026-07-10) — implements
Phase E of [`de-event-sourcing-plan.md`](de-event-sourcing-plan.md)
(items 1, 2, 5; item 3 shipped with PR #1649; item 4 is a design constraint
honored throughout).
**Effort:** part of the "migrate Aggregates off event sourcing" line of work.

> **Decisions locked (product owner, 2026-07-10):**
> the storage lives in **`io.spine.server.entity.storage`**, beside its
> sibling `EntityEventStorage` (amends the plan's original
> `entity/history/` path, which predates the Phase D landing spot);
> the columns are **`entity_id` / `created` / `version`** — the identical
> column set as `EntityEventColumns`, with `created` sourced from the
> record's `Version.timestamp`; state history is **opt-in per repository,
> off by default**, and reading it while disabled **fails fast** (never
> silently empty).

## Context

After the event-sourcing cutover (PR-B2), an aggregate's storage keeps only
the *latest* `EntityRecord`. The event journal (`EntityEventStorage`,
PR #1649) preserves emitted events, but there is no way to see the *states*
an entity went through. Phase E adds an opt-in, bounded history of recent
`EntityRecord` versions per entity for debugging and analysis, including the
"state at time T" query. The contract is entity-generic: Phase F wires the
same storage to `ProcessManager`s without touching it.

## Design

- **`HistoryStorage<I, M>`** (2026-07-10, review) — the abstract base of
  both `EntityEventStorage` and `EntityStateHistoryStorage`: owns the
  `entity_id`/`created`/`version` column contract, the `historyBackward`
  window reads, the per-entity `trim`, and the count/date `truncate`.
  The subclasses keep only their write validation and type-specific reads
  (`stateAt`). Constructed from a single **`HistorySpec`** (2026-07-11) —
  the types, the id extraction, and the **`HistoryColumns`** trio in one
  object; the `RecordSpec` is derived inside it, so the column set can
  never drift from the trio. The column holders implement `HistoryColumns`
  and are passed as the objects themselves (`@JvmField` dropped from their
  constants — overrides cannot carry it; Java callers use getters).
- **Record key** — new proto `EntityStateKey {Any entity_id; int32 version}`
  in `server/src/main/proto/spine/server/entity/state_key.proto` (one message
  per file). The composite key makes re-writing the same version an
  idempotent overwrite. Records are stored **as-is** (`EntityRecord`, no
  wrapper) — the #1649 pattern. *(Renamed from `EntityStateId` on
  2026-07-11: that name read like the `I` type parameter of `EntityState`.)*
- **`EntityStateHistoryStorage`** —
  `MessageStorage<EntityStateKey, EntityRecord>` over
  `factory.createRecordStorage(context, spec)`; final. API:
  - `write(EntityRecord)` — requires entity id, state, version, and version
    timestamp present (no proto validation options on `EntityRecord`).
  - `historyBackward(entityId, batchSize): Iterator<EntityRecord>` — newest
    first; sorts by `version` DESC only (unique per entity — no time
    tie-break needed, unlike the journal).
  - `stateAt(entityId, at: Timestamp): EntityRecord?` — the retained record
    with the highest version whose `created` time is `<= at` (inclusive);
    `null` when no retained record qualifies — the honest answer for both
    "T precedes the retained window" and "T predates the entity".
    Time comparison via `Timestamps.compare` in memory, not a storage-level
    Timestamp filter (portability posture of the journal).
  - `trim(entityId, keepMostRecent)` — per-entity retention maintenance,
    skip-based (version arithmetic breaks on retention gaps).
    **Identifier-only since the ultra review, item 3 (2026-07-11):**
    the record keys carry the version, so the override ranks
    `index()`-read ids in memory and deletes — no record payloads read.
    The generic base `trim` (used by the journal, whose `EventId` keys
    carry no order) stays record-based.
    *(A `FieldMask`-narrowed read was tried and dropped even earlier:
    for `EntityRecord` payloads the storage masking applies to the packed
    `state` — see `FieldMaskApplier` — not to the record fields.)*
  - `truncate(keepMostRecent[, olderThan])` — global maintenance, mirroring
    `EntityEventStorage.truncate`.
  - Public `readAll`/`delete`/`deleteAll` overrides (maintenance parity).
- **SPI** — `StorageFactory.createEntityStateHistoryStorage(ContextSpec,
  Class<? extends EntityState<?>>)` default method; vendors customize via
  `createRecordStorage` as usual. **Storage identity (2026-07-11, ultra
  review):** the entity state class flows into the `RecordSpec.sourceType`
  via an `internal` `HistorySpec` constructor (the public constructor keeps
  the item type as the identity) — vendors allocate physical storage by
  `sourceType` (JDBC table, Datastore kind), so without it all recording
  repositories of a context would share one table and `(entity_id,
  version)` keys could collide across ID-sharing entity types. **The event
  journal is identified the same way** (`createEntityEventStorage(context,
  entityStateClass)`; `AggregateStorage` derives the class via
  `EntityClass.stateClassOf`): `EventId` keys prevented overwrites, but a
  shared table still bled `historyBackward`/guard reads across ID-sharing
  types and made one repository's `truncate` trim every type's journal.
  Journal rows written by the `.430` shared-identity layout become
  invisible to per-type reads — the accepted #1649-style pre-GA caveat.
- **Repository wiring** (`AggregateRepository`, Java):
  `recordStateHistory()` opt-in + `stateHistoryEnabled()` (the
  `useIdempotencyGuard()` precedent) + `stopRecordingStateHistory()`
  (2026-07-11, stop-only: retained records stay and re-enabling resumes
  over them; purging is the explicit `stateHistory().truncate(0)` *before*
  stopping — an automatic purge could exceed the repository scope on
  backends mapping equal record specs to one per-context table).
  **No automatic trimming (product owner, 2026-07-11):** the original
  `recordStateHistory(int depth)` trimmed after every write, but even the
  identifier-only trim is a query per dispatch — too costly for the hot
  path. Retention is delegated to the application; the `depth` knob and
  the flag `stateHistoryDepth` are gone, and the `recordStateHistory()`
  Javadoc points at `truncate(keepMostRecent[, olderThan])` / `trim` as
  the scheduled-maintenance recipe. Runtime-toggle safety (ultra review
  item 2, 2026-07-11): the flag is `volatile`, and the recording
  decision is made ONCE per dispatch in `store(A)` — the write path
  obtains the storage directly instead of re-checking through the
  fail-fast accessor, so a concurrent stop cannot fail a dispatch whose
  state is already persisted (at most one trailing record lands); `stateHistory()` accessor **fails fast** with
  `IllegalStateException` while disabled; `private synchronized` lazy
  creation (first touch is on concurrent dispatch); the write happens in
  `store(A)` — per dispatch, ahead of the cache write-through, so a batched
  delivery records its intermediate versions too (moved out of `doStore()`
  after the PR #1650 Codex finding); the storage is closed in `close()`.
  `AggregatePart` repositories inherit everything.
- **Aggregate read API** (added during the PR #1650 review — retrieval by
  aggregates is the point of the feature): `Aggregate.stateAt(Timestamp):
  Optional<S>` and `stateHistoryBackward(depth): Iterator<S>`, delegating
  to the `RecentStateHistory<S>` of `TransactionalEntity`. The recent
  histories are unified (product owner, 2026-07-10): a generic
  `RecentHistory<T>` base with `RecentEventHistory` (the renamed event
  view; loader renamed `EventHistoryLoader`) and `RecentStateHistory<S>`
  (fed by `StateHistoryLoader`, unpacking records to the state type) —
  so Phase F wires only the PM repository side. The repository installs
  a loader delegating to the fail-fast `stateHistory()` accessor in
  `create(id)`; a bare instance outside a repository reads empty.

## Verification

- `./gradlew clean build` (new proto) + `dokkaGenerate`.
- New Kotlin suites: `EntityStateHistoryStorageSpec` (storage semantics:
  window, `stateAt` incl. honest-empty and same-instant tie-break, trim,
  truncate) and `AggregateRepositoryStateHistorySpec` (off by default,
  fail-fast accessor, one record per successful dispatch, unbounded
  retention), plus the `StateHistoryTestRepository` widening fixture.
- `pre-pr`: version gate (`.430 → .431`, committed), `kotlin-engineer`,
  `spine-code-review`, `review-docs`.

Delete this file when the phase merges to master (per `.agents/tasks/README.md`).
