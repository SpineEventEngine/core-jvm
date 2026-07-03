# ADR 0001 — Aggregates without event sourcing

- **Status:** Proposed (awaiting product-owner approval)
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

The decision (brief, "Suggested solution"; delivery plan, "Locked decisions")
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
| D1 | Event import survives via a new Kotlin `@Import` receptor that mutates `builder()`; the imported event is recorded as the fact. `ImportBus`/routing unchanged. |
| D2 | `@Apply` on any aggregate (incl. `AggregatePart`) is a **`ModelError` at model-building time** after cutover — fail fast, never a silent no-op. |
| D3 | Aggregate version advances **+1 per command dispatch**, not per event — `ProcessManager` semantics via `CommandDispatchingPhase` + `VersionIncrement.sequentially`. |
| D4 | The "must produce an event" guard is **endpoint-scoped**: `@Assign` must emit ≥1 event; `@React` may be a no-op or a lifecycle-only change; `@Import` must mutate state and needs no emitted event. |
| D5 | Recent history for idempotency is read from the **journal tail**, bounded by a new `historyDepth` (default 100). |
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
- mutates aggregate state via `builder()`;
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
2. invoke the matching `@Import` receptor so it mutates `builder()`;
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

**Consequences.** The existing import contract that "the aggregate must be
modified during import" is preserved and strengthened: today
`EventImportEndpoint.onEmptyResult` logs an error when nothing changed; under
D4 an import that mutates nothing is an error by construction.

**Rejected alternatives.** Overloading `@React` for import (conflates two
distinct routing paths and dedup rules); deprecating import entirely and
forcing conversion to commands (product owner chose to keep import — plan
decision 4).

---

## D2 — Fate of `@Apply` after cutover (resolves A2)

**Decision.** After cutover, an aggregate (or `AggregatePart`) that declares a
`@Apply` method is **rejected at model-building time** with a `ModelError`
carrying a migration message ("Move the body of this event applier into the
corresponding `@Assign`/`@React` handler; see ADR 0001"). Raising happens in
`AggregateClass` and `AggregatePartClass`
(`server/.../aggregate/model/AggregateClass.java`,
`.../AggregatePartClass.java`), which already scan for appliers via
`EventApplierSignature` — that scanning machinery is retained **only** to
detect the annotation and fail, not to invoke anything.

`@Apply` itself (`server/.../aggregate/Apply.java`) is marked `@Deprecated` with
Javadoc pointing here; it is removed at v2.0.0 (final).

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

`EventComparator.chronological()` orders events by timestamp, then by
`EventContext.version.number`, then by event id (`EventComparator.java:74-85`),
and is the sort key for `DefaultEventStore` reads and catch-up
(`CatchUpMessageComparator`). With shared versions, two *same-timestamp* events
from one dispatch fall back to event-id order on stored reads (live posting
preserves `producedEvents` list order). This is **already true for
ProcessManager events**, so it is existing, tolerated behavior — not a new
hazard introduced here. Because it does change aggregate behavior, the
implementation must confirm no consumer relies on distinct per-event aggregate
versions for same-timestamp ordering (Phase E rollout check).

---

## D4 — State mutation without emitted events (resolves A4)

**Decision.** The "did the handler do something?" contract is **endpoint- and
receptor-scoped**, matching today's per-endpoint behavior:

| Receptor | Contract |
|----------|----------|
| `@Assign` (command) | Must emit ≥1 event **or** reject. Empty success stays illegal. |
| `@React` (reaction) | May legitimately do nothing, or change **only lifecycle flags** (archive/delete) with zero events. `AggregateEventReactionEndpoint.onEmptyResult` stays a no-op (`AggregateEventReactionEndpoint.java:60-67`). |
| `@Import` | Must mutate business state; needs no emitted event (the imported event is the fact). An import that changes nothing stays an error (`EventImportEndpoint.onEmptyResult` logs). |

The store decision is unchanged: `AggregateEndpoint.storeAndPost` persists when
`withEvents || lifecycleFlags changed` (`AggregateEndpoint.java:83-100`).

**Migration note — lifecycle flags move from appliers into handlers.** Today an
aggregate never flips its own lifecycle flags inside a reactor/handler; the flip
lives in an *applier*. E.g. `ProjectAggregate` reacts to `AggProjectArchived` by
**returning** that event (`ProjectAggregate.java:103-110`), and a separate
`@Apply apply(AggProjectArchived)` calls `setArchived(true)`
(`ProjectAggregate.java:116-118`). After D2 removes appliers, that `setArchived`
call must move into the `@React`/`@Assign` handler that emits the event — the
event is still emitted (e.g. to reach child aggregates) and the flag is now
flipped in the same handler body. Every aggregate whose appliers set lifecycle
flags must be migrated this way, or the flag change is silently lost when the
applier becomes a `ModelError`. So the "zero-event but lifecycle-only" `@React`
row above describes a *capability* (`storeAndPost` stores on a flag change with
no events), not a pattern that exists in the current codebase.

**Explicitly rejected:** a blanket "builder was touched but no event was
produced → reject" rule. It would break both a legitimately empty `@React` and
every `@Import` (which by design mutates state and emits no new event). Any
commit-time guard must be scoped per receptor kind (D1/D4 table), not a blanket
"was the builder accessed".

---

## D5 — Recent-history window for idempotency (resolves A5)

**Decision.** `IdempotencyGuard` (`server/.../aggregate/IdempotencyGuard.java`)
keeps its logic but reads `RecentHistory` from the **tail of the event
journal** on load, bounded by a new per-repository `historyDepth` setting that
**defaults to 100** — the value `DEFAULT_SNAPSHOT_TRIGGER` had
(`AggregateRepository.java`), preserving the effective dedup window size.

**Critical implementation constraint.** `IdempotencyGuard` does not match on the
incoming signal id directly; it scans `Aggregate.historyContains(...)` for a
previously *emitted* event whose `EventContext.pastMessage` origin equals the
incoming signal's id (`IdempotencyGuard.java:119-160`). Therefore the
journal-tail read that rebuilds `RecentHistory` **must return emitted events
with `EventContext.pastMessage` intact.** Two things must be verified in PR-B2:

- `AggregateStorage.writeEvent`'s `event.clearEnrichments()`
  (`AggregateStorage.java:284-290`) strips enrichments only — confirm it does
  **not** touch `pastMessage`;
- a test proves dedup works purely from the journal tail with **no snapshot**
  present.

**Consequences / semantic change.** The window shifts from "all events since the
last snapshot" (previously unbounded between snapshots) to "the last
`historyDepth` journal events". For the default trigger of 100 the practical
window is unchanged, but this is a behavioral change to document. Aggregates
with very high command throughput between snapshots should tune `historyDepth`.

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

---

## D7 — Journal trimming without snapshots (resolves A7)

**Decision.** Snapshot-index trimming
(`AggregateStorage.truncateOlderThan(int)` and its dated overload,
`TruncateOperation`) is meaningless without snapshots and is **deprecated** in
PR-B2. Count- and date-based journal trimming is introduced in **Phase D**
alongside `EntityHistoryStorage`. Until Phase D ships, the event journal is
**append-only** and grows unbounded; this is acceptable for the pre-GA window
and is noted as a known limitation in the migration guide.

---

## D8 — `state()` inside an open transaction (resolves A8)

**Decision.** A handler reads the aggregate's **pre-transaction** state via
`state()` and writes via `builder()` — exactly the `ProcessManager` handler
model. The current rule that forbids `state()` inside an applier and permits
only `builder()` (`Aggregate.ensureAccessToState`, guarded by `ApplierWatcher`)
is **relaxed**: `@Assign`, `@React`, and `@Import` bodies may read `state()`
(the committed value at dispatch start) and mutate `builder()`. `ApplierWatcher`
and the applier-scoped guard are removed with the appliers (PR-B2 step 3).

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
trigger):

```java
protected final int  historyDepth();
protected final void setHistoryDepth(int depth);   // default 100
```

**Deprecated (removed at v2.0.0 final):**

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
- The idempotency window is now bounded by `historyDepth` and depends on
  `pastMessage` surviving in the journal tail (D5) — must be tested.
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

- [ ] Product owner sign-off on D1–D8 and the API sketch → unblocks Phase B.
