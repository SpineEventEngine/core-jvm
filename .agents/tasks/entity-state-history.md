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
> column set as `EntityEventColumn`, with `created` sourced from the
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

- **Record key** — new proto `EntityStateId {Any entity_id; int32 version}`
  in `server/src/main/proto/spine/server/entity/state_id.proto` (one message
  per file). The composite key makes re-writing the same version an
  idempotent overwrite. Records are stored **as-is** (`EntityRecord`, no
  wrapper) — the #1649 pattern.
- **`EntityStateHistoryStorage`** —
  `MessageStorage<EntityStateId, EntityRecord>` over
  `factory.createRecordStorage(context, spec)`; final. API:
  - `write(EntityRecord)` — requires entity id, version, and version
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
  - `trim(entityId, keepMostRecent)` — per-entity window enforcement for
    the write hook: `readAll` of the entity's records newest-first,
    skip-based counting (version arithmetic breaks on retention gaps),
    `deleteAll`. *(A `FieldMask`-narrowed read was tried and dropped:
    for `EntityRecord` payloads the storage masking applies to the packed
    `state` — see `FieldMaskApplier` — not to the record fields, so the
    mask only mangled the state copies and saved nothing.)*
  - `truncate(keepMostRecent[, olderThan])` — global maintenance, mirroring
    `EntityEventStorage.truncate`.
  - Public `readAll`/`delete`/`deleteAll` overrides (maintenance parity).
- **SPI** — `StorageFactory.createEntityStateHistoryStorage(ContextSpec)`
  default method; vendors customize via `createRecordStorage` as usual.
- **Repository wiring** (`AggregateRepository`, Java):
  `recordStateHistory(int depth)` opt-in + `stateHistoryEnabled()` +
  `stateHistoryDepth()` (the `useIdempotencyGuard()`/`historyDepth`
  precedent); `stateHistory()` accessor **fails fast** with
  `IllegalStateException` while disabled; `private synchronized` lazy
  creation (first touch is on concurrent dispatch); write + trim in
  `store(A)` — per dispatch, ahead of the cache write-through, so a batched
  delivery records its intermediate versions too (moved out of `doStore()`
  after the PR #1650 Codex finding); the storage is closed in `close()`.
  `AggregatePart` repositories inherit everything.

## Verification

- `./gradlew clean build` (new proto) + `dokkaGenerate`.
- New Kotlin suites: `EntityStateHistoryStorageSpec` (storage semantics:
  window, `stateAt` incl. honest-empty and same-instant tie-break, trim,
  truncate) and `AggregateRepositoryStateHistorySpec` (off by default,
  fail-fast accessor, one record per successful dispatch, depth honored),
  plus the `StateHistoryTestRepository` widening fixture.
- `pre-pr`: version gate (`.430 → .431`, committed), `kotlin-engineer`,
  `spine-code-review`, `review-docs`.

Delete this file when the phase merges to master (per `.agents/tasks/README.md`).
