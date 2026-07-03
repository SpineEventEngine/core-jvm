# ADR 0001 — Aggregates without event sourcing

- **Status:** Accepted (product owner, 2026-07-03) — unblocks Phase B
- **Date:** 2026-07-03
- **Deciders:** Alexander Yevsyukov (product owner)
- **Feature branch:** `de-event-sourcing`
- **Supersedes:** the event-sourced aggregate loading model in Spine ≤ 2.0.0-SNAPSHOT
- **Related:** [de-event-sourcing-brief.md](../../.agents/tasks/de-event-sourcing-brief.md),
  [de-event-sourcing-plan.md](../../.agents/tasks/de-event-sourcing-plan.md)

This ADR is the design authority for Phase B of the delivery plan. It resolves
the eight open design points (A1–A8) and fixes the public-API surface
(signatures only) so the implementation PRs have a single source of truth.
Decision **Dn** below resolves plan point **An**.

---

## Context

Spine `Aggregate`s are event-sourced today: state is reconstructed by replaying
the event journal (optionally from a snapshot), and every state change is
expressed as an event applied by a `@Apply`-annotated *event applier*. Command
handlers (`@Assign`) and reactors (`@React`) are pure — they return events;
appliers mutate state.

Production history shows this is fragile (brief, "Current state"):

1. A change to state-validation logic can make a stored history un-replayable,
   so the aggregate becomes **un-loadable**.
2. If one of several appliers from a single command fails, some events are
   applied and some are not — the aggregate is left **partially valid**,
   violating the core promise that an aggregate protects its invariants.
3. History can never be declared obsolete: old events must remain replayable
   forever.

The decision (brief, "Suggested solution in brief"; delivery plan, "Locked decisions")
is a **hard cutover**: load aggregates from their latest persisted state,
mutate state inside the command/reaction handler, keep events only as a
traceability journal, and remove event-sourced loading in one delivery train.
`@Apply` is deprecated now and removed at v2.0.0 (final).

The runtime facts this ADR is grounded in (verified against the tree
2026-07-03) are cited inline as `File.java:line`.

---

## Decision summary

| # | Decision |
|---|----------|
| D1 | Event import survives via a new Kotlin `@Import` receptor that *may* mutate `builder()`; the imported event is recorded as the fact. `ImportBus`/routing unchanged. |
| D2 | `@Apply` on any aggregate (incl. `AggregatePart`) is a **`ModelError` at model-building time** after cutover — fail fast, never a silent no-op. |
| D3 | Aggregate version advances **+1 per command dispatch**, not per event — `ProcessManager` semantics via `CommandDispatchingPhase` + `VersionIncrement.sequentially`. |
| D4 | State update is never forced in any handler. Only `@Assign` must emit ≥1 event (or reject); `@React` emission is optional; `@Import` returns nothing and may leave state unchanged. Persistence must trigger on a business-state change, not only on events/lifecycle. |
| D5 | Dedup is the delivery layer's job; the aggregate `IdempotencyGuard` is **opt-in, off by default**. Recent history is loaded **lazily** from the journal tail, bounded by `historyDepth` (default 100). |
| D6 | A handler mutates the open transaction's `builder()`; validation happens **once, at commit**; any failure rolls the whole transaction back. This structurally eliminates partial validity (brief problem #2). |
| D7 | Snapshot-index journal trimming is dropped; count/date-based trimming is deferred to Phase D. The journal is append-only until then. |
| D8 | Handlers read the **pre-transaction** `state()` and mutate via `builder()`; the applier-only access guard is relaxed to allow `@Assign`/`@React`/`@Import` to touch the builder. |

---

## D1 — Event import (resolves A1)

**Decision.** Introduce a new receptor annotation `@Import` in
`io.spine.server.aggregate` (Kotlin, `@Retention(RUNTIME)`,
`@Target(FUNCTION)`). An import receptor:

- accepts the imported event message (deriving from `io.spine.base.EventMessage`)
  as its first parameter, and optionally an `EventContext` as the second;
- **may** mutate aggregate state via `builder()` — updating state is optional
  (see D4); an import that changes nothing is valid;
- returns **nothing** — the imported event *is* the recorded fact.

Routing is unchanged: `ImportBus`, `AggregateRepository.setupImportRouting`
(`AggregateRepository.java:306`), `eventImportRouting`
(`AggregateRepository.java:120,485`), and the `IMPORT_EVENT` inbox label
(`AggregateRepository.java:214`) all stay.

**Why the endpoint must change.** Today import invokes *no* handler:
`EventImportEndpoint.invokeDispatcher` (`EventImportEndpoint.java`) merely wraps
the incoming event as a "produced event", and state is mutated later by the
`@Apply(allowImport = true)` applier during `Aggregate.apply(...)`. With
appliers gone, the endpoint must instead:

1. open the transaction (as for command/reaction — see D6);
2. invoke the matching `@Import` receptor (which *may* mutate `builder()`);
3. **inject the incoming event as the produced fact** — as
   `EventImportEndpoint.invokeDispatcher` already does today
   (`EventImportEndpoint.java:67-78`, wrapping the event into
   `Success.producedEvents`), re-stamped with the aggregate's version. This is
   *required*, not incidental: the store/post path gates on
   `successfulOutcome.hasEvents()` (`AggregateEndpoint.java:117-121`), so a void
   receptor alone yields zero produced events and `applyProducedEvents`, the
   post branch of `storeAndPost`, and the journal write are all skipped —
   `onEmptyResult` would merely log. The imported event must be placed into the
   outcome's `producedEvents` for storage + posting to occur;
4. commit, then store + post as usual.

So the new import endpoint keeps today's event-injection *and* adds the receptor
invocation for the state mutation. `AggregateClass.importableEvents()` /
`importsEvents()` now read the `@Import` receptor map instead of scanning
appliers for `allowImport`; `importsEvents()` still gates registration of the
import dispatcher (`AggregateRepository.java:169`).

**Consequences.** Per D4, an `@Import` receptor **may** leave state unchanged —
that is not an error. Because the endpoint always injects the imported event as
the produced fact (step 3), the outcome always "has events", so the no-events
branch `EventImportEndpoint.onEmptyResult` (which logs an error today) is never
reached and is removed. The event is journaled and the version advances +1
whether or not state changed.

**Rejected alternatives.** Overloading `@React` for import (conflates two
distinct routing paths and dedup rules); deprecating import entirely and
forcing conversion to commands (product owner chose to keep import — plan
decision 4).

> **Approved** by product owner, 2026-07-03: separate `@Import` annotation,
> name `@Import`, receptor returns nothing.

---

## D2 — Fate of `@Apply` after cutover (resolves A2)

**Decision.** After cutover, an aggregate (or `AggregatePart`) that declares a
`@Apply` method is **rejected at model-building time** with a `ModelError`. The
error message is **self-contained migration guidance** — it does not reference
this ADR or any design document. Suggested text:

> *"Event appliers (`@Apply`) are no longer supported. Move the state update
> from this method into the `@Assign` or `@React` handler that emits the event,
> applying the change via `builder()`, then delete this method. For imported
> events, use an `@Import` method instead."*

Raising happens in `AggregateClass` and `AggregatePartClass`
(`server/.../aggregate/model/AggregateClass.java`,
`.../AggregatePartClass.java`), which already scan for appliers via
`EventApplierSignature` — that scanning machinery is retained **only** to
detect the annotation and fail, not to invoke anything.

`@Apply` itself (`server/.../aggregate/Apply.java`) is marked `@Deprecated` with
migration Javadoc; it is removed at v2.0.0 (final).

**`@Apply` on a `ProcessManager` is invalid** and unsupported — a PM has no
appliers, so the annotation was always dead there. It is not a case core
accommodates. The one downstream fixture that does this (`model-tools`'
`RenameProcMan`) is fixed in that repo (delivery plan, Phase E).

**`AggregatePart` coverage is broader than the `ModelError`.** Parts are
event-sourced too and load through the same path
(`AggregateRoot.partState` → `repository.loadOrCreate`,
`AggregateRoot.java:87-91`) via `PartFactory`. Both D2's fail-fast **and** the
state-read load path of D6/D8 apply to `AggregatePart` / `AggregatePartClass` /
`PartFactory`, not only to top-level aggregates (plan PR-B2 step 11).

**Why fail-fast, not silent.** A `@Apply` method that is silently never invoked
would leave state changes un-applied with no signal — the worst failure mode. A
model error at context-build time surfaces the omission before a single message
is dispatched.

> **Approved** by product owner, 2026-07-03: fail-fast `ModelError`; the message
> carries self-contained migration instructions, not a document reference.

---

## D3 — Version advancement (resolves A3)

**Decision.** The aggregate's own `Version` advances **by +1 per command (or
reaction/import) dispatch**, regardless of how many events the handler emits —
the same rule `ProcessManager` already uses. Reuse the PM mechanism directly: a
single `CommandDispatchingPhase` driven by `VersionIncrement.sequentially(tx)`,
whose `AutoIncrement.nextVersion()` is `Versions.increment(current)` = `current
+ 1` (`VersionIncrement.java:76,102-117`; `PmTransaction.perform`,
`PmTransaction.java:79-81`).

`Phase.propagate` runs the handler first, then increments the version
(`Phase.java:73-83`).

**Event version stamping needs no new machinery.** Produced events are already
stamped at creation by `EventEmitter.toSuccessfulOutcome`, which assembles
*every* event of a dispatch with the single `target.version()`
(`EventEmitter.java:64-79`). ProcessManagers rely on exactly this and do no
per-event correction — so PM-emitted events from one dispatch already share one
version in production today. Aggregates diverge only because
`AggregateEndpoint.correctProducedEvents` (`AggregateEndpoint.java:160-179`)
back-fills per-event versions after the applier/`VersionSequence` replay
(`Aggregate.apply`; `VersionSequence.java:60-70`). Removing appliers,
`VersionSequence`, and `correctProducedEvents` leaves `EventEmitter`'s natural
single-version stamping in place — aggregates then behave **exactly like
ProcessManagers**: all events of a dispatch carry the aggregate's pre-dispatch
version, and the aggregate advances to +1. No successor stamping API is needed.

**Consequences / observable change.** Previously, aggregate events from one
command bore distinct versions (v+1..v+N) and the aggregate ended at v+N; now
all such events share the aggregate's pre-dispatch version and the aggregate
ends at v+1 — identical to how PM-produced events are versioned today. Call this
out in the migration guide (PR-B3).

**Ordering is carried by per-event timestamps, not by the version.**
`EventComparator.chronological()` sorts by timestamp, then version number, then
event id (`EventComparator.java:74-85`; the sort key for `DefaultEventStore`
reads and catch-up). Crucially, the framework already stamps **each** event of a
dispatch with its own `Time.currentTime()` — `EventEmitter` calls it once per
event in the emission loop (`EventEmitter.java:72-75`), and the default
`SystemTimeProvider` advances its sub-millisecond counter by **+1 µs per call**
(`io.spine.base.Time`, `IncrementalNanos`). So same-command events already
receive distinct, increasing, microsecond-spaced timestamps in emission order,
and `EventComparator` orders them correctly on the timestamp alone — the version
tiebreaker is effectively never reached. Sharing one version per dispatch
therefore does **not** regress event ordering under the default time source.

**Decision (Option A):** rely on this existing per-event timestamp stamping;
do **not** synthesize explicit per-event timestamp deltas. Storage preserves the
ordering across backends: the in-memory store keeps full nanoseconds and sorts
the deserialized proto in memory; `jdbc-storage` stores timestamps as `BIGINT`
nanoseconds and `ORDER BY`s on them; Google Datastore (`gcloud-jvm`) orders on a
native **microsecond**-precision column — still finer than the +1 µs per-event
spacing, so emission order survives all three. The only fallbacks to event-id
order are a custom coarse `Time.Provider` that hands out identical timestamps, or
a dispatch emitting >1000 events in one millisecond (counter wrap) — both are
deterministic across reads, just not emission order. Note this in the migration
guide (PR-B3).

> **Approved** by product owner, 2026-07-03: version +1 per dispatch; events
> adopt the process-manager single-version stamping; ordering relies on the
> existing per-event microsecond timestamps (Option A), no synthetic deltas.

---

## D4 — State mutation without emitted events (resolves A4)

**Decision.** The "did the handler do something?" contract is **scoped per
receptor kind**. Command handlers and reactors work the same way — read
`state()`, *optionally* mutate `builder()` and/or set lifecycle flags — and
differ only in whether an emitted event is required. **Updating state is never
forced**: a handler may emit events without touching state (e.g. a command that
only records a request), so we do not constrain the author's business logic:

| Receptor | Contract |
|----------|----------|
| `@Assign` (command) | **May** update state via `builder()` and/or set lifecycle flags via `setArchived()` / `setDeleted()` — state update is optional. **Must emit ≥1 event or reject** — empty success stays illegal. |
| `@React` (reaction) | Same as `@Assign`, **except emitting an event is optional** — a reactor may emit zero events. May update state and set lifecycle flags, or neither. `AggregateEventReactionEndpoint.onEmptyResult` stays a no-op (`AggregateEventReactionEndpoint.java:60-67`). |
| `@Import` | Consumes the imported event and **may** update state via `builder()`, or leave it unchanged — **both are OK, neither is an error.** The imported event is always recorded as the fact and the version advances +1 (D1). |

`setArchived()` / `setDeleted()` are explicitly allowed in `@Assign` and `@React`
bodies.

**The store condition must be expanded.** Today `AggregateEndpoint.storeAndPost`
persists only when `withEvents || lifecycleFlags changed`
(`AggregateEndpoint.java:83-100`). That was sufficient while state changed only
through appliers driven by events. Now a handler may mutate **business state**
with *no* emitted event and *no* lifecycle change — a zero-event `@React` (D4)
that updates state — and the current gate would **silently drop** that change
(it never calls `store()`, so the committed state is not persisted and only
lingers in the cache until eviction). The commit/store path must therefore also
persist when the **built business state changed**, e.g. store when
`withEvents || lifecycleFlags changed || state changed`. PR-B2 must add this and
test a zero-event, state-only reactor.

**Migration note — lifecycle flags move from appliers into handlers.** Today an
aggregate never flips its own lifecycle flags inside a reactor/handler; the flip
lives in an *applier*. E.g. `ProjectAggregate` reacts to `AggProjectArchived` by
**returning** that event (`ProjectAggregate.java:103-110`), and a separate
`@Apply apply(AggProjectArchived)` calls `setArchived(true)`
(`ProjectAggregate.java:116-118`). After D2 removes appliers, that `setArchived`
call moves into the `@React` / `@Assign` handler that emits the event — the event
is still emitted (e.g. to reach child aggregates) and the flag is now flipped in
the same handler body. Every aggregate whose appliers set lifecycle flags must be
migrated this way, or the flag change is silently lost when the applier becomes a
`ModelError`.

**Explicitly rejected:** a blanket "builder was touched but no event was produced
→ reject" rule, and any new "must change state" guard. A reactor may legitimately
emit nothing; an import may legitimately change nothing. The only hard emission
requirement is `@Assign`'s "emit ≥1 event or reject".

> **Approved** by product owner, 2026-07-03: command handlers and reactors may
> update state or only emit events (state update is never forced); `@React` may
> emit zero events; `setArchived()`/`setDeleted()` are allowed in `@Assign` and
> `@React`; `@Import` may update state or not — both OK.

---

## D5 — Deduplication and the recent-history window (resolves A5)

**Decision.** Deduplication is primarily the **delivery layer's** responsibility.
The aggregate-level `IdempotencyGuard` becomes an **opt-in, per-repository
backstop, off by default**. Recent history — both for the guard when enabled and
for business-logic access via `historyBackward()`/`historyContains()` — is loaded
**lazily from the tail of the event journal, only on demand**, bounded by a new
per-repository `historyDepth` setting that **defaults to 100** (the old
`DEFAULT_SNAPSHOT_TRIGGER` value).

**Why delivery can own dedup.** The delivery layer deduplicates on
`DispatchingId = (signalId, inboxId)` — effectively *(incoming signal id, target
entity + type)* (`DispatchingId.java`, `InboxIds.java:94-103`). It covers commands
(`HANDLE_COMMAND`), reacted events (`REACT_UPON_EVENT`), and imported events
(`IMPORT_EVENT`) alike, and because the key includes the target, one event fanned
out to N aggregates yields N distinct keys — **no false dedup on fanout**
(`AggregateRepository.java:439-445`; `LiveDeliveryStation.java`).

**Why the guard stays available (opt-in).** Delivery's *durable* dedup is
configuration-dependent. The always-on layer is an in-memory
`DeliveredMessagesCache` capped at 1000 entries per JVM and lost on restart
(`DeliveredMessagesCache.java:44-65`); durable cross-restart/cross-node dedup
exists only when a `Delivery` `deduplicationWindow` is configured, and it
**defaults to zero/off** (`DeliveryBuilder.java:429-431`; `Delivery.local()` uses
30 s). So there are cases delivery alone can miss that a journal-backed guard
still catches: a redelivery after a JVM restart with no window set, cache
eviction on a hot shard (>1000 in-flight keys), or a late retry past a
*time*-based delivery window but still within the aggregate's *count*-based
history. Because state now changes directly in the handler (D4/D6), a missed
duplicate means a **double state mutation** — so the durable, journal-backed
guard is worth keeping as an option. Enable it per repository (working API
`AggregateRepository.useIdempotencyGuard()` / an `enabled` flag, default off).
Kept deliberately so it can be **removed entirely later** if delivery-layer dedup
proves sufficient in practice.

**Cost model.** Guard **off** (default): a normal dispatch does only the
state-record read — no journal read. Guard **on**: each dispatch loads the bounded
(`historyDepth`) journal tail to scan for a duplicate.
`historyBackward()`/`historyContains()` in business logic trigger the same lazy,
bounded load on first access, independent of the guard.

**Implementation constraint** (applies when the guard is on, or when business
logic uses history for causality). `IdempotencyGuard` matches the incoming signal
id against the `EventContext.pastMessage` origin of previously *emitted* events in
recent history (`IdempotencyGuard.java:119-160`), so the lazy journal-tail read
**must return emitted events with `EventContext.pastMessage` intact.** Verify in
PR-B2 that `AggregateStorage.writeEvent`'s `clearEnrichments()`
(`AggregateStorage.java:284-290`) strips enrichments only and not `pastMessage`,
and add a test that the guard dedups from the journal tail with no snapshot.

**Consequences / semantic change.** With the guard **enabled**, its window is the
last `historyDepth` journal events rather than "everything since the last
snapshot" — at the default of 100, comparable to before; high-throughput
aggregates that enable it should tune `historyDepth`. With the guard **disabled**
(default), durable dedup is the delivery layer's responsibility and a
`deduplicationWindow` should be configured in production, sized to the realistic
redelivery/retry horizon.

> **Approved** by product owner, 2026-07-03: delivery owns dedup; the aggregate
> `IdempotencyGuard` is opt-in per repository, **off by default** (kept so it can
> be removed later if unneeded); recent history is loaded **lazily on demand**,
> bounded by `historyDepth` (default 100, name approved).

---

## D6 — Mutation model, validation, and rollback (resolves A6)

**Decision.** The framework opens an `AggregateTransaction` **before** invoking
the handler; the handler mutates the transaction's `builder()` directly (Kotlin:
`update { }` / `alter { }` from
`server/src/main/kotlin/io/spine/server/entity/TransactionalEntityExts.kt`) and
returns its events; the framework then **validates the built state once** and
commits. This reorders today's flow, where `AggregateEndpoint.handleAndApplyEvents`
runs the handler first and opens the transaction afterward in
`applyProducedEvents` (`AggregateEndpoint.java:115-146`).

Failure semantics reuse the existing transaction machinery: a handler exception,
a rejection, or an invalid built state triggers `Transaction.rollback` /
`rollbackStateAndVersion` (`Transaction.java:313-318,470,499-501`); nothing is
stored and nothing is posted. On a non-committed transaction the aggregate's
**committed** state and version are left untouched — the builder holds
uncommitted changes and is discarded.

**This resolves the brief's problem #2 (partial validity).** Because all
mutations for a dispatch accumulate in one builder and are validated atomically
at a single commit, there is no longer any way for "some events applied, some
not" to leave an aggregate half-updated: either the whole dispatch commits, or
none of it does.

**Implementation notes for PR-B2.**
- The instance is obtained via `AggregateRepository.loadOrCreate` through a
  `RepositoryCache`; ensure a rolled-back dispatch does not leave a mutated
  instance cached (the committed state is intact, but confirm the cache holds
  no dangling builder state).
- The applier-only state-access guard (`Aggregate.ensureAccessToState`,
  `ApplierWatcher`) is relaxed under D8 so handlers may call `builder()`.

> **Approved** by product owner, 2026-07-03: transaction opened before the
> handler; single validate-at-commit; rollback discards the whole dispatch
> (resolves the brief's partial-validity problem).

---

## D7 — Journal trimming without snapshots (resolves A7)

**Decision.** Snapshot-index trimming
(`AggregateStorage.truncateOlderThan(int)` and its dated overload,
`TruncateOperation`) is meaningless without snapshots and is **deprecated** in
PR-B2. Count- and date-based journal trimming is introduced in **Phase D**
alongside `EntityHistoryStorage`. Until Phase D ships, the event journal is
**append-only** and grows unbounded; this is acceptable for the pre-GA window
and is noted as a known limitation in the migration guide.

> **Approved** by product owner, 2026-07-03: deprecate snapshot-index trimming
> now; defer count/date-based trimming to Phase D; journal append-only between
> Phase B and Phase D.

---

## D8 — `state()` inside an open transaction (resolves A8)

**Decision.** A handler reads the aggregate's **pre-transaction** state via
`state()` and writes via `builder()` — exactly the `ProcessManager` handler
model. The current rule that forbids `state()` inside an applier and permits
only `builder()` (`Aggregate.ensureAccessToState`, guarded by `ApplierWatcher`)
is **relaxed**: `@Assign`, `@React`, and `@Import` bodies may read `state()`
(the committed value at dispatch start) and mutate `builder()`. `ApplierWatcher`
and the applier-scoped guard are removed with the appliers (PR-B2 step 3).

> **Approved** by product owner, 2026-07-03: relax the guard; handlers may read
> `state()` (pre-dispatch value) and write `builder()`.

---

## Public API sketch (signatures only)

Illustrative shapes for review; final names/packages are fixed here, bodies are
Phase B. New types are Kotlin (delivery plan, decision 7).

**New — import receptor annotation** (`io.spine.server.aggregate.Import`) — the
first Kotlin-declared Spine receptor annotation (`@Apply`/`@React`/`@Assign` are
Java, `@Target(METHOD)`). `AnnotationTarget.FUNCTION` compiles to
`ElementType.METHOD`, so a Java aggregate method is annotatable and `ReceptorScan`
(`getDeclaredMethods()` + `isAnnotationPresent`) discovers it. It needs its
**own** `ReceptorSignature` + `ReceptorMap` wiring in `AggregateClass` (today
only `EventApplierSignature` is scanned, `AggregateClass.java:62-63`):

```kotlin
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
public annotation class Import
```

Usage (Java aggregate, migrated from `@Apply(allowImport = true)`):

```java
@Import
private void on(InventoryAdjusted e) {
    builder().setCount(e.getNewCount());   // mutates state; returns nothing
}
```

**Changed — command/reaction handlers** keep their signatures (return events)
and now mutate the builder in-body:

```java
@Assign
ItemAdded handle(AddItem c) {
    builder().addItem(c.getItem());        // NEW: state change in the handler
    return ItemAdded.newBuilder()./* … */.build();
}
```

**New — state read on the storage** (`AggregateStorage`), delegating to the
internal `EntityRecordStorage.read(I)` (inherited `Optional<EntityRecord>
read(I)`) and **bypassing** the `ensureStatesQueryable()` gate
(`AggregateStorage.java:373-381`) so it works for `NONE`-visibility aggregates
(load-invariant #1). This is a *new* method reading the state substrate — not a
rename of the journal read `read(I, int)`:

```java
Optional<EntityRecord> readState(I id);    // NEW; reads the state record, not the journal
```

**Changed — `AggregateRecords.newStateRecord`** drops its `boolean includeState`
second parameter and always packs the business state; its caller
`AggregateStorage.writeState` (`AggregateStorage.java:388`) must stop passing
`queryingEnabled` (decouple state persistence from visibility; load-invariant #1):

```java
// was: newStateRecord(Aggregate<I,?,?> aggregate, boolean includeState)
static <I> EntityRecord newStateRecord(Aggregate<I, ?, ?> aggregate);  // state always included
```

**New — recent-history depth** (`AggregateRepository`, replacing the snapshot
trigger) and the **opt-in idempotency guard** (off by default, D5):

```java
protected final int  historyDepth();
protected final void setHistoryDepth(int depth);   // default 100
protected final void useIdempotencyGuard();        // enable the journal-backed dedup backstop
protected final boolean idempotencyGuardEnabled(); // default false — delivery owns dedup
```

**Deprecated (removed at v2.0.0 (final)):**

```java
io.spine.server.aggregate.Apply                       // annotation
io.spine.server.aggregate.Apply#allowImport
AggregateRepository#setSnapshotTrigger(int)           // no-op → historyDepth
AggregateRepository#snapshotTrigger()
AggregateStorage#read(I, int), AggregateStorage#read(I)  // journal reads; load uses readState(I)
AggregateStorage#writeSnapshot(I, Snapshot)
AggregateStorage#truncateOlderThan(int[, Timestamp])  // → Phase D trimming
Aggregate#toSnapshot()
// proto (kept for wire compat, marked deprecated):
//   spine.server.aggregate.Snapshot, AggregateHistory
//   spine.system.server.AggregateHistoryCorrupted   + EntityLifecycle#onCorruptedState
```

**Removed internals** (not public API — deleted, not deprecated):
`VersionSequence`, `AggregateEndpoint#correctProducedEvents`, `ApplierWatcher`,
and the invocation side of `EventApplierSignature`/`Applier` (retained only to
*detect* `@Apply` for the D2 `ModelError`).

**Dead subscriber.** `AggregateHistoryCorrupted` is never emitted post-cutover
(no replay to corrupt), so its only subscriber —
`server-testlib`'s `DiagnosticLog.on(AggregateHistoryCorrupted)`
(`server-testlib/.../blackbox/probe/DiagnosticLog.java:101-111`) — becomes dead.
**Decision:** remove that `@Subscribe` method in PR-B2 (testlib, not public API;
a no-op retain buys nothing). Tracked in plan PR-B2 steps 12/15.

**Unchanged (must not regress):** `StorageFactory` SPI
(`createAggregateStorage`, `createAggregateEventStorage`,
`createEntityRecordStorage`); the client query path (`QueryService` → `Stand` →
`EntityQueryProcessor` → `AggregateRepository.findRecords` →
`AggregateStorage.readStates`); `BlackBox` / `EventSubject` / `CommandSubject`;
`EventPlayer` / `EventPlayingTransaction` (retained for `Projection`).

---

## Consequences

**Positive.**
- Un-loadable aggregates (brief problem #1) and partial validity (problem #2)
  are eliminated: load is a state read; a dispatch commits atomically or not at
  all.
- Obsolete history no longer blocks loading (problem #3): events are traceability
  only, never replayed into state.
- The aggregate dispatch path converges on the `ProcessManager` model
  (transaction-open-then-handler, +1 version per dispatch), reducing conceptual
  and code surface.

**Negative / risks (tracked in the delivery plan's risk table).**
- `NONE`-visibility aggregates that never persisted state are a **hard break**
  on upgrade — no tooling ships (plan decision 2, "Data assumptions"). The
  mandatory decoupling of state persistence from visibility (load-invariant #1)
  fixes this going forward but cannot recover pre-cutover data.
- Dedup shifts to the delivery layer; the opt-in guard's window is bounded by
  `historyDepth` and depends on `pastMessage` surviving in the journal tail (D5).
  With the guard off (default), production must configure a delivery
  `deduplicationWindow` — must be tested.
- PR-B2 is a large **atomic** change (runtime rewrite + all fixture migrations
  in one commit); it cannot land as green sub-commits (plan, PR-B2 atomicity
  note).

## Open questions for approval

None blocking. The two load-critical invariants (unconditional state
persistence; explicit +1 version advancement) are settled above, and the
event-version model is resolved to match `ProcessManager` semantics (D3). Items
to confirm *during* implementation, not before: `pastMessage` survives
`clearEnrichments` (D5); the repository cache holds no dangling builder state
after rollback (D6); no consumer relies on distinct per-event aggregate versions
for same-timestamp ordering (D3 / `EventComparator`, Phase E).

## Approval

- [x] Product owner sign-off on D1–D8 and the API sketch, 2026-07-03 — **Phase B
  unblocked.** Each decision was walked through individually; refinements made
  during review: D1 (`@Import` separate annotation, returns nothing), D2 (self-
  contained migration message, no doc ref), D3 (Option A — rely on existing
  per-event µs timestamps, no synthetic deltas), D4 (state update never forced in
  any handler; `@React` emission optional; `@Import` may no-op),
  D5 (delivery owns dedup; guard opt-in/off by default; history lazy).
