# Migrating aggregates off event sourcing

This guide walks you through upgrading application code to the non-event-sourced
`Aggregate`. It is the practical companion to
[ADR 0001 — Aggregates without event sourcing](../adr/0001-aggregates-without-event-sourcing.md),
which records the design decisions (referenced below as **D1**–**D10**).

## What changed, in one paragraph

An aggregate no longer reconstructs its state by replaying events. It loads from its
**latest persisted state** (an `EntityRecord`) and mutates that state **inside the command
handler or event reactor** that emits the events. Events remain first-class — versioned,
stored in an append-only journal, and posted to the `EventBus` — but they are traceability
records, never replayed into state. Event appliers (`@Apply`) are gone: a class that still
declares one now fails fast at model-building time.

## At a glance

| Area | Before (event-sourced) | After (this release) |
|------|------------------------|----------------------|
| State change | `@Apply` applier mutates state from a played event | `@Assign` / `@React` mutates `builder()` in-body |
| Loading | replay the journal (± a snapshot) | read the latest `EntityRecord` |
| `@Apply` | required for every state transition | **`ModelError` at model build** — deprecated, removed in v2.0.0 |
| Version | advanced per emitted event (`v+1..v+N`) | **+1 per command**, like a `ProcessManager` |
| Event import | `@Apply(allowImport = true)` + `ImportBus` | **removed** — use external reactions / gateways |
| Dedup | always-on aggregate `IdempotencyGuard` | guard **opt-in, off** (performance); delivery has its own time-window dedup |
| Recent history | `historyBackward()` (window = last snapshot) | `eventHistoryBackward(depth)` — explicit window |
| Snapshots | `setSnapshotTrigger()`, `toSnapshot()` | **removed** — use `setEventHistoryDepth()` |

## 1. Move applier bodies into handlers

This is the core migration. For every `@Apply` method, move its body into the `@Assign`
command handler or `@React` reactor that emits the corresponding event, mutating state
through `builder()`. Then delete the `@Apply` method.

**Before** — the handler returns the event; a separate applier mutates state:

```java
@Assign
ItemAdded handle(AddItem c) {
    return ItemAdded.newBuilder()
                    .setItem(c.getItem())
                    .build();
}

@Apply
private void on(ItemAdded e) {
    builder().addItem(e.getItem());   // state change lived here
}
```

**After** — the handler mutates state and returns the event:

```java
@Assign
ItemAdded handle(AddItem c) {
    builder().addItem(c.getItem());   // moved into the handler
    return ItemAdded.newBuilder()
                    .setItem(c.getItem())
                    .build();
}
```

In Kotlin, prefer the builder DSL inherited from `TransactionalEntity` over calling
`builder()` directly — `alter { }` applies changes, while `update { }` applies and
returns the builder:

```kotlin
@Assign
fun handle(c: AddItem): ItemAdded {
    alter { addItem(c.item) }
    return itemAdded { item = c.item }
}
```

### Lifecycle flags move too

If an applier flipped a lifecycle flag with `setArchived(true)` / `setDeleted(true)`, that
call moves into the command handler or reactor body (D4). Both `@Assign` and `@React` may
set lifecycle flags directly:

```java
@Assign
ProjectArchived handle(ArchiveProject c) {
    setArchived(true);   // was in the @Apply applier
    return ProjectArchived.newBuilder()
                          .setProject(id())
                          .build();
}
```

A reactor that only flips a flag emits nothing (`return noReaction()`); if it must also notify
other aggregates, emit a *distinct* event rather than re-emitting the one it reacted to (which
would route straight back into the same reactor).

### What the framework does for you

The framework opens an `AggregateTransaction` **before** invoking the receptor (exactly as
for a `ProcessManager`), validates the built state **once** when the receptor returns, and
commits. A handler exception, a rejection, or an invalid built state rolls the whole
dispatch back — nothing is stored, nothing is posted. Because all mutations of a dispatch
accumulate in one builder and are validated atomically, an aggregate can no longer be left
"partially valid" (the historical event-sourcing hazard this release removes).

### Emission rules (unchanged in spirit, D4)

- `@Assign` **must emit at least one event, or reject.** Updating state is optional.
- `@React` **may emit zero events** (`NoReaction`). It may update state, set lifecycle
  flags, both, or neither.

A zero-event reactor that only changes business state **is** persisted — the store gate was
widened to fire on a state change, not only on emitted events or lifecycle changes.

### `@Apply` now fails fast

Declaring any `@Apply` method (on an `Aggregate` **or** an `AggregatePart`) raises a
`ModelError` when the context is built:

```
The aggregate class `<Class>` declares `@Apply`-annotated event applier(s) for <events>.
Event sourcing has been removed: move each applier's body into the `@Assign` / `@React`
receptor that emits the event (mutating the state via `builder()`), and delete the
`@Apply` method(s).
```

`AggregatePart`s were event-sourced no differently and migrate the same way. The `@Apply`
annotation is retained only so the model can detect it and fail; it is removed in v2.0.0.

## 2. Preventive validation (optional): `tryAlter` and `validate()`

When a state transition might violate a declared constraint, guard it **before** the change
touches the live builder, instead of relying on the commit-time rollback (D9). `tryAlter { }`
runs the mutation on a scratch copy of the builder, validates the candidate, and merges it
only when clean — otherwise it leaves the live state untouched and returns the constraint
violations. It is available on any `TransactionalEntity`, so process managers get it too.

A command handler turns violations into a **rejection**:

```kotlin
@Assign
@Throws(OutOfStock::class)
fun handle(cmd: ReserveItems): ItemsReserved {
    val violations = tryAlter { inStock = state.inStock - cmd.quantity }
    if (violations.isNotEmpty()) {            // would go negative
        throw OutOfStock.newBuilder()
            .setStock(id())
            .setRequested(cmd.quantity)
            .build()
    }
    return itemsReserved { stock = id(); quantity = cmd.quantity }
}
```

A reactor **skips the update** — an event is a fact and cannot be rejected. Returning
`NoReaction` speaks only about emission; the state stays untouched because `tryAlter`
withheld the change:

```kotlin
@React
fun on(event: BulkOrderPlaced): EitherOf2<ItemsReserved, NoReaction> {
    val violations = tryAlter { inStock = state.inStock - event.quantity }
    return if (violations.isEmpty())
        EitherOf2.withA(itemsReserved { stock = id(); quantity = event.quantity })
    else
        EitherOf2.withB(noReaction())         // no events; the change was withheld
}
```

> ⚠️ The `block` of `tryAlter` operates on the scratch builder (its receiver). Do not nest
> `alter { }` / `update { }` inside it — those mutate the **live** builder and defeat
> the guard.

Its in-place companion, `builder().validate()`, probes the builder's *current* content
(including content that is already invalid) and returns the violations without building. A
command handler about to reject can mutate live and probe, since the rejection rolls the
transaction back anyway:

```kotlin
alter { inStock = state.inStock - cmd.quantity }
val violations = builder().validate()
if (violations.isNotEmpty()) { /* throw the rejection */ }
```

Prefer `tryAlter` in a reactor (there is no way to un-mutate the live builder); prefer
`validate()` when you are going to reject anyway.

## 3. Event import is removed

Event import is gone with event sourcing (D1, revised). Its replay-era purpose —
rebuilding state from imported events — no longer exists. Removed from the runtime:

- `@Apply(allowImport = true)` (the attribute is ignored),
- `ImportBus`, `EventImportEndpoint`, `EventImportDispatcher`,
  `UnsupportedImportEventException`,
- `AggregateRepository.setupImportRouting` / `eventImportRouting`,
- `AggregateClass.importableEvents()` / `importsEvents()`,
- the testing API `BlackBox.importsEvent`.

Route external facts through standard idioms instead:

| Old use of import | Replacement |
|-------------------|-------------|
| Facts from another Bounded Context | a `@React` reactor whose event parameter is marked `@External` |
| Facts from a third-party / legacy system | a gateway (adapter, process manager, or command) that converts them into this context's commands or events |
| Bulk seeding / data migration | storage-level state writes (the record is the source of truth) |
| Test arrangement | real commands and events through `BlackBox` |

**Retained for wire compatibility** (proto/journal readers keep working): the
`InboxLabel.IMPORT_EVENT` label, the `EventImported` system event and its emitter
`EntityLifecycle.onEventImported`. New code should not use them.

## 4. Version advances +1 per command, not per event

An aggregate's version now advances **by one per command (or reaction) dispatch**,
regardless of how many events the handler emits — the same rule a `ProcessManager` already
uses (D3). Every event of one dispatch carries the aggregate's **pre-dispatch** version.
A dispatch that changes only the lifecycle flags — e.g. a reaction archiving the
aggregate without emitting events — advances the version too: the persisted record
changes, and consumers keyed by the version (such as the entity state history) must
never observe two distinct records under one version. Only a dispatch that changes
nothing at all leaves the version as is.

**Observable change.** Previously, the events of one command bore distinct versions
`v+1..v+N` and the aggregate ended at `v+N`. Now those events share one version and the
aggregate ends at `v+1`. If any consumer relied on distinct per-event aggregate versions,
revisit it.

**Ordering is unaffected under the default time source.** Same-command events are ordered by
their per-event timestamps, which the framework stamps at microsecond spacing in emission
order; the version is only a tiebreaker and is effectively never reached. Ordering can fall
back to event-id order only under a custom coarse `Time.Provider` that hands out identical
timestamps, or a dispatch emitting more than 1000 events in a single millisecond — both
deterministic across reads, just not necessarily emission order.

## 5. Deduplication and the idempotency guard

Two independent deduplication mechanisms exist, and they are not interchangeable:

- The **delivery layer** deduplicates within a short *time* window, keyed on the incoming
  signal and the target entity (so one event fanned out to many aggregates is not falsely
  deduplicated — D5). Size its `deduplicationWindow` in production to your realistic
  redelivery/retry horizon.
- The aggregate-level **`IdempotencyGuard`** deduplicates against the last
  `eventHistoryDepth()` *signals* (default 100), however long ago they were dispatched —
  a count-based window, not a time-based one.

The guard is **opt-in, per repository, and off by default**, and the reason is performance:
enabling it adds a bounded journal read to every dispatch. It is *not* off because the delivery
layer makes it redundant — the two cover different windows (time vs. signal count).

```java
final class OrdersRepository extends AggregateRepository<OrderId, Order, OrderState> {
    OrdersRepository() {
        useIdempotencyGuard();   // enable the journal-backed guard for this repository
    }
}
```

When enabled, each dispatch scans the last `eventHistoryDepth()` journal events (default 100)
rather than "everything since the last snapshot"; high-throughput aggregates that enable it
should tune `eventHistoryDepth`. With the guard off (the default), a redelivery after a JVM restart
or cache eviction outside the delivery `deduplicationWindow` could apply a state mutation
twice, since state now changes directly in the handler — so size that window accordingly.

## 6. Recent-history access states its window

Business logic that reads recent history now states how far back it looks, in events (D10):

```java
protected final Iterator<Event> eventHistoryBackward(int depth);
protected final boolean eventHistoryContains(int depth, Predicate<Event> predicate);
```

The `event` qualifier tells the journal reads apart from the state history reads
(`stateAt(Timestamp)`, `stateHistoryBackward(depth)`) available when the repository
records the state history.

The count is **events**, not commands — after the version change above, one command may emit
several events sharing one version. History is loaded lazily from the journal tail, sized to
the request.

The parameterless `historyBackward()` and `historyContains(Predicate)` are **deprecated but
still work**, delegating with a fixed `depth` of 100 — regardless of any
`setEventHistoryDepth(n)` you configure. Their effective window changes from "everything since the last snapshot" to
"the last 100 events." Move to the explicit forms and state the window your logic needs.

## 7. Snapshot configuration is removed

Snapshots no longer exist, so their configuration is gone (not merely deprecated):

| Removed | Replacement |
|---------|-------------|
| `AggregateRepository.setSnapshotTrigger(int)` | `AggregateRepository.setEventHistoryDepth(int)` |
| `AggregateRepository.snapshotTrigger()` | `AggregateRepository.eventHistoryDepth()` |
| `Aggregate.toSnapshot()` | — (loading reads the state record) |

The `Snapshot` and `AggregateHistory` proto messages are **retained** for journal wire
compatibility (`AggregateHistory` is superseded by the plain `core.Event` journal — §9).
Snapshot-index journal trimming (`AggregateStorage.truncateOlderThan`) is **removed**:
it could only ever trim by snapshot positions, and the current journal contains no
snapshots. Its replacement is **count/date-based truncation**:

```java
storage.truncate(keepMostRecent);            // keep the N most recent events per aggregate
storage.truncate(keepMostRecent, olderThan); // drop events older than the time,
                                             // but never cut into the N most recent
```

The journal is append-only on the write path, so production systems run the truncation
as periodic maintenance. When the opt-in `IdempotencyGuard` is enabled (§5), keep at
least the repository's `eventHistoryDepth` events, so the deduplication window stays intact.

## 8. Data caveat — `NONE`-visibility aggregates are a hard break

Read this before upgrading a running system.

Because the persisted state record is now the source of truth, an aggregate can only be
loaded if it **has** a materialized state record. Before this release, state was persisted
only for aggregates that were **visible for querying** (`(entity).visibility != NONE`). An
aggregate whose state message is `(entity).visibility = NONE` and that relied on event replay
has **no materialized state** — no snapshot, no pre-cutover state record, and no Mirror to
recover from. Such aggregates are a **hard break** on upgrade, and **no migration tooling is
shipped** to close the gap (product decision — Spine 2.x is pre-GA).

Going forward the problem cannot recur: state persistence is decoupled from visibility, so
every aggregate's business state is now stored unconditionally. `queryingEnabled` continues
to gate only the read-side query exposure, not whether state is stored. But this fixes new
data only; it cannot reconstruct state that was never persisted before the cutover.

## 9. The journal moves to the entity level

Events are emitted by entities, not by aggregates alone, so the journal types now live at
the entity level. Initially they serve aggregates; `ProcessManager` journaling is planned
as a follow-up.

| Superseded | Replacement |
|------------|-------------|
| `AggregateHistory` | the plain `core.Event` journal — no wrapper type; reads return `List<Event>` |
| `AggregateEventRecord` | the `core.Event` itself — events are journaled as-is, no wrapper |
| `AggregateEventRecordId` | the `core.EventId` of the stored event — no dedicated id type |
| `AggregateEventStorage` | `io.spine.server.entity.storage.EntityEventStorage` |
| `AggregateEventRecordColumn` | `io.spine.server.entity.storage.EntityEventColumns` |
| `StorageFactory.createAggregateEventStorage` | `StorageFactory.createEntityEventStorage` |

The journal stores plain `Event`s; the emitting entity, the event time, and the event
version are exposed for querying as columns derived from the event context.
`AggregateStorage` now extends `EntityRecordStorage`, so its `read`/`write` operate on the
latest state record, while the recent events are read via `readEvents(...)` returning a
`List<Event>`. Storage vendors implement the same `RecordStorage`-level SPI as before — this
is a recompile against the new types, not a rewrite.

Of the superseded API, only the **proto messages remain** (marked `deprecated`, for wire
compatibility). The storage-level classes — `AggregateEventStorage`,
`AggregateEventRecordColumn`, `StorageFactory.createAggregateEventStorage` — and the
snapshot-index `AggregateStorage.truncateOlderThan` trimming are **removed**: org-wide
usage research found no production callers, and the trimming could only operate on
pre-cutover journals. The current journal is trimmed by the count/date-based
`truncate(keepMostRecent[, olderThan])` instead (§7).

**Data caveat — pre-upgrade journals become invisible to reads.** Read this before
upgrading a running system. The journal now stores plain `Event`s — a **record kind
distinct** from the legacy `AggregateEventRecord`s — so journal entries written
before the upgrade are not visible to the new reads. In particular, the `IdempotencyGuard`
window and `eventHistoryBackward(depth)` **start empty right after the upgrade** and refill as
new events are emitted. If the guard is your only deduplication safeguard, configure a
delivery `deduplicationWindow` to cover the upgrade window (§5). As with the
`NONE`-visibility break (§8), **no migration tooling is shipped** (Spine 2.x is pre-GA).
The legacy records stay on disk as inert data; the current runtime does not read them.

## Removed and deprecated API — quick reference

**Removed** (compile errors — migrate the call site):

- `Aggregate` `@Apply` appliers → move bodies into `@Assign` / `@React` (§1)
- `AggregateRepository.setSnapshotTrigger(int)` / `snapshotTrigger()` → `setEventHistoryDepth` /
  `eventHistoryDepth` (§7)
- `Aggregate.toSnapshot()` (§7)
- `ImportBus`, `EventImportEndpoint`, `EventImportDispatcher`,
  `UnsupportedImportEventException`,
  `AggregateRepository.setupImportRouting` / `eventImportRouting`,
  `AggregateClass.importableEvents()` / `importsEvents()`, `BlackBox.importsEvent` (§3)
- `AggregateEventStorage`, `AggregateEventRecordColumn`,
  `StorageFactory.createAggregateEventStorage` → the entity-level journal types (§9)
- `AggregateStorage.truncateOlderThan(int)` / `(int, Timestamp)` → the count/date-based
  `AggregateStorage.truncate(int)` / `(int, Timestamp)` (§7, §9)

**Deprecated** (still compile; removed in v2.0.0):

- `@Apply` and `@Apply#allowImport` — detection-only; a declared applier is a `ModelError`
- `Aggregate.historyBackward()` / `historyContains(Predicate)` →
  `eventHistoryBackward(depth)` / `eventHistoryContains(depth, predicate)` (§6)

**Retained for wire compatibility** (do not use in new code): the `Snapshot`,
`AggregateHistory`, `AggregateEventRecord`, and `AggregateEventRecordId` proto messages
and the `InboxLabel.IMPORT_EVENT` label — all marked `deprecated` in the proto;
the `EventImported` and `AggregateHistoryCorrupted` system events with their emitters.
