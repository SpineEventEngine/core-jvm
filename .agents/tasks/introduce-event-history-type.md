# Introduce `EntityEventHistory` and `EntityEventStorage`

**Status:** planned — opens Phase D as its own PR (decided 2026-07-08);
follows PR-B2 (the event-sourcing cutover, merged in #1647).
**Effort:** part of the "migrate Aggregates off event sourcing" line of work
(see [`de-event-sourcing-plan.md`](de-event-sourcing-plan.md)).

> **Naming locked (product owner, 2026-07-08):** the new history type is named
> **`EntityEventHistory`** (not `EventHistory`, as earlier drafts said), and
> the journal storage moves to the entity level as **`EntityEventStorage`**,
> replacing `AggregateEventStorage`. Events are emitted by entities, not by
> aggregates alone: the initial rollout covers aggregates, with
> `ProcessManager` journaling planned for a near-future PR — so nothing in the
> new types may be aggregate-specific.

## Context

`AggregateHistory` predates the cutover. Its proto still carries event-sourcing
structure (`server/src/main/proto/spine/server/aggregate/aggregate.proto`):

```proto
message AggregateHistory {
    // If populated, contains the last snapshot of the aggregate state.
    server.aggregate.Snapshot snapshot = 1;

    // Events of the aggregate (the tail after the snapshot, or the full history).
    repeated core.Event event = 2;
}
```

Since PR-B2 an aggregate loads from its latest `EntityRecord`, not by replaying
the journal, and no snapshots are written. So the `snapshot` field is dead
weight, and the "history = snapshot + tail" semantics no longer hold — the
journal is now a plain append-only **event** log kept for traceability and the
opt-in `IdempotencyGuard`. The type name and shape mislead readers about how
aggregates are stored and loaded.

The same applies one level down. The journal is persisted by
`AggregateEventStorage` as `AggregateEventRecord`s — both aggregate-named, and
the record still carries the `oneof {event, snapshot}` arm. Nothing in
"a journal of events emitted by an entity" is aggregate-specific; process
managers emit events too and will journal them next.

## Goal

Introduce the entity-level journal pair:

- **`EntityEventHistory`** — a snapshot-free message (events only) used as the
  journal representation across the runtime;
- **`EntityEventStorage`** — the append-only journal storing the events
  emitted by an entity, over the standard `RecordStorage` SPI.

Migrate the aggregate runtime to them, and **deprecate the aggregate-specific
pair** — `AggregateHistory`, `AggregateEventRecord` (with its id type and
columns), and `AggregateEventStorage` — retaining the protos and the storage
class for wire/SPI compatibility, not removing them.

## Scope

Current `AggregateHistory` users in main source (all in the `server` module):

- `server/src/main/java/io/spine/server/aggregate/AggregateStorage.java` —
  `extends AbstractStorage<I, AggregateHistory>`; `read(I, int)` returns
  `Optional<AggregateHistory>`; `write(I, AggregateHistory)` and
  `writeAll(...)` consume it. This is the primary seam. Also owns the journal
  reads/writes: `writeEvent`, `historyBackward(...)`,
  `readHistoryBackward(...)` against `AggregateEventStorage`.
- `server/src/main/java/io/spine/server/aggregate/UncommittedHistory.java` —
  `get()` returns a single-segment `AggregateHistory` (already collapsed to a
  plain event list internally by PR-B2; it just re-wraps into
  `AggregateHistory` for `writeAll`).
- `server/src/main/java/io/spine/server/aggregate/ReadOperation.java` —
  reconstructs an `AggregateHistory` (still threads a `Snapshot` field).

Journal-storage users to migrate to `EntityEventStorage`:

- `server/src/main/java/io/spine/server/aggregate/AggregateEventStorage.java`
  — the storage itself (deprecate).
- `server/src/main/java/io/spine/server/aggregate/AggregateRecords.java` —
  the `newEventRecord(...)` factories.
- `server/src/main/java/io/spine/server/aggregate/AggregateEventRecordColumn.java`
  — `aggregate_id` / `created` / `version` / `snapshot` columns; `public` and
  used by downstream storage libraries, so deprecate rather than remove.
- `server/src/main/java/io/spine/server/aggregate/HistoryBackwardOperation.java`
  and `server/src/main/java/io/spine/server/aggregate/TruncateOperation.java`.
- `StorageFactory.createAggregateEventStorage(...)` — the SPI default method
  (`server/src/main/java/io/spine/server/storage/StorageFactory.java`).

*(`EntityLifecycle.java`, listed in earlier drafts, references only the
already-deprecated `AggregateHistoryCorrupted` diagnostic event — not the
history type. No change there.)*

## Sketch of the steps

1. **Define the protos** in a new, separate file
   `server/src/main/proto/spine/server/entity/event_history.proto` (Java
   package `io.spine.server.entity`): `EntityEventHistory` — essentially
   `repeated core.Event event = 1;` with room for journal metadata if needed;
   plus the record pair `EntityEventRecord` / `EntityEventRecordId` —
   `entity_id`, `timestamp`, `event`. **No snapshot arm anywhere.**
2. **New Kotlin `EntityEventStorage`** (suggested package:
   `io.spine.server.entity.storage`, beside `EntityRecordStorage`; new code is
   Kotlin per locked decision 7) over `StorageFactory.createRecordStorage`,
   with columns `entity_id`, `created`, `version` — no `snapshot` column. Add
   `StorageFactory.createEntityEventStorage(context)`; deprecate
   `createAggregateEventStorage`.
3. **Re-point the `AggregateStorage` journal** to `EntityEventStorage`:
   `writeEvent`, `historyBackward`, `readHistoryBackward`, and
   `HistoryBackwardOperation` (or an entity-level replacement for the latter).
4. **Migrate the history type**: re-type `AggregateStorage` to
   `AbstractStorage<I, EntityEventHistory>`; `UncommittedHistory.get()`
   returns `EntityEventHistory`; `write`/`writeAll`/`read` produce/consume it;
   `ReadOperation` stops threading a `Snapshot` and builds an
   `EntityEventHistory`.
5. **Deprecate `AggregateHistory`** and the record protos — mark them
   deprecated with migration notes pointing to the new types; keep them (and
   `Snapshot`) for wire compatibility, mirroring how the cutover kept the
   snapshot protos. The snapshot-index truncation (`TruncateOperation`, the
   deprecated `truncateOlderThan` overloads) stays wired to the deprecated
   `AggregateEventStorage`: it is already inert on post-cutover journals — a
   journal without snapshot records never satisfies the snapshot-index
   condition — while the D3 count/date trimming is designed against
   `EntityEventStorage`.
6. **Migrate tests/fixtures** that reference the old types
   (`AggregateStorageTest`, `AggregateHistoryTruncationTest`,
   `ReadOperationTest`, `TestAggregateStorage`, and friends).

## Data caveat

`EntityEventRecord` is a new record kind: storage backends persist it
separately from the legacy `AggregateEventRecord`s, so pre-existing journal
entries are invisible to `EntityEventStorage` reads — the `IdempotencyGuard`
window and `historyBackward(depth)` start empty after the upgrade. Spine 2.x
is pre-GA and ships no data-migration tooling (locked decision 2); state this
in the migration notes the way the `NONE`-visibility caveat is stated.

## Design questions (all settled 2026-07-08)

- ~~Does `EventHistory` live in `aggregate.proto`, or a storage-neutral
  proto?~~ **Settled 2026-07-08:** the type is entity-level
  (`EntityEventHistory`) and lives in the `spine.server.entity` proto package,
  in a new, separate `event_history.proto` — not appended to `entity.proto`.
  Rationale (product owner): the Protobuf authors recommend one message per
  file — not yet followed in this codebase — and separate files keep future
  refactoring of proto types easier.
- ~~Relationship to `AggregateEventRecord` — wrap those, or plain
  `core.Event`s?~~ **Settled:** plain `core.Event`s; the storage-level record
  is the new `EntityEventRecord`.
- ~~Transform vs. replace at the record level~~ **Settled 2026-07-08 (product
  owner): clean replacement.** A new `EntityEventRecord` supersedes
  `AggregateEventRecord` (deprecated, kept for wire compatibility), accepting
  the data caveat above. The rejected alternative — keeping
  `AggregateEventRecord` as the record type inside `EntityEventStorage` —
  would have preserved reads of pre-upgrade journals at the cost of dragging
  the snapshot arm and aggregate naming into the entity-level API.
- ~~Naming of the Phase D state-history contract next to
  `EntityEventStorage`~~ **Settled 2026-07-08:** renamed to
  `EntityStateHistoryStorage` — "state history" vs. the event journal.
- ~~Whether this rides with, or precedes, Phase D items D1–D3~~ **Settled
  2026-07-08 (product owner): this task opens Phase D as its own PR.** D3
  trimming is then built directly against `EntityEventStorage`; the
  state-history items (D1/D2) are independent and may land in parallel or
  after.

## Verification

- `./gradlew clean build` green (proto change ⇒ clean build).
- Wire compatibility preserved: `AggregateHistory`, `AggregateEventRecord`,
  and `Snapshot` still serializable; no removed fields/messages.
- No main-source references to the deprecated types remain except the
  deprecated shim paths kept for compatibility.
- Storage vendors smoke-build against the new core snapshot (the Phase C
  list): the `AggregateStorage` type-parameter change and the new SPI method
  affect them.
