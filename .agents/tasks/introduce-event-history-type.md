# Introduce `EventHistory` and deprecate `AggregateHistory`

**Status:** planned — a follow-up phase after PR-B2 (the event-sourcing cutover) merges.
**Effort:** part of the "migrate Aggregates off event sourcing" line of work
(see [`de-event-sourcing-plan.md`](de-event-sourcing-plan.md)).

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

## Goal

Introduce a clean **`EventHistory`** type (events only) as the journal
representation used across the runtime, migrate the code to it, and **deprecate
`AggregateHistory`** — retaining the proto for wire compatibility, not removing
it.

## Scope

Current `AggregateHistory` users in main source (all in the `server` module):

- `server/src/main/java/io/spine/server/aggregate/AggregateStorage.java` —
  `read(I, int)` returns `Optional<AggregateHistory>`; `writeAll(...)` consumes
  one. This is the primary seam.
- `server/src/main/java/io/spine/server/aggregate/UncommittedHistory.java` —
  `get()` returns a single-segment `AggregateHistory` (already collapsed to a
  plain event list internally by PR-B2; it just re-wraps into `AggregateHistory`
  for `writeAll`).
- `server/src/main/java/io/spine/server/aggregate/ReadOperation.java` —
  reconstructs an `AggregateHistory` (holds a `Snapshot snapshot` field).
- `server/src/main/java/io/spine/server/entity/EntityLifecycle.java`.

## Sketch of the steps

1. **Define `EventHistory`** in `aggregate.proto` (or a new proto) — essentially
   `repeated core.Event event = 1;`, with room for journal metadata if needed.
   No `Snapshot` field.
2. **Migrate `UncommittedHistory.get()`** to return `EventHistory`, and
   **`AggregateStorage.writeAll`/`read`** to produce/consume `EventHistory`.
   The storage-level snapshot/truncation methods (still exercised by the
   `Truncate` tests, retained for wire compat) can keep dealing with
   `AggregateEventRecord`s; only the aggregate-facing history type changes.
3. **Update `ReadOperation`** to stop threading a `Snapshot` and to build an
   `EventHistory`.
4. **Deprecate `AggregateHistory`** — mark the proto message deprecated with a
   migration note pointing to `EventHistory`; keep it (and `Snapshot`) for wire
   compatibility, mirroring how the cutover kept the snapshot protos.
5. **Migrate tests/fixtures** that reference `AggregateHistory`
   (`AggregateStorageTest`, `AggregateHistoryTruncationTest`, etc.).

## Design questions to settle first

- Does `EventHistory` live in `aggregate.proto`, or should the journal type move
  to a storage-neutral proto (it is no longer aggregate-specific in spirit)?
- Relationship to `AggregateEventRecord` (the per-record storage type) — does
  `EventHistory` wrap those, or plain `core.Event`s?
- Whether this rides with, or precedes, Phase D
  (`EntityHistoryStorage` / journal trimming) in the delivery plan — they both
  touch the journal representation and should not conflict.

## Verification

- `./gradlew clean build` green (proto change ⇒ clean build).
- Wire compatibility preserved: `AggregateHistory` and `Snapshot` still
  serializable; no removed fields/messages.
- No main-source references to `AggregateHistory` remain except the deprecated
  shim path kept for compatibility.
