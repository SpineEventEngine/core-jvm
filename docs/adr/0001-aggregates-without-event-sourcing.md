# ADR 0001 — Aggregates without event sourcing

- **Status:** Accepted (product owner, 2026-07-03; amended with D9–D10, 2026-07-04; D9 extended with `ValidatingBuilder.validate()` and D1 revised — event import dropped, 2026-07-05) — unblocks Phase B
- **Date:** 2026-07-03
- **Deciders:** Alexander Yevsyukov (product owner)
- **Feature branch:** `de-event-sourcing`
- **Supersedes:** the event-sourced aggregate loading model in Spine ≤ 2.0.0-SNAPSHOT
- **Related:** [de-event-sourcing-brief.md](../../.agents/tasks/de-event-sourcing-brief.md),
  [de-event-sourcing-plan.md](../../.agents/tasks/de-event-sourcing-plan.md)

This ADR is the design authority for Phase B of the delivery plan. It resolves
the eight open design points (A1–A8) and fixes the public-API surface
(signatures only) so the implementation PRs have a single source of truth.
Decision **Dn** below resolves plan point **An**. **D9** and **D10** were
added on 2026-07-04 in response to the
[PR #1642 review](https://github.com/SpineEventEngine/core-jvm/pull/1642#pullrequestreview-4629642991)
(D9 — developer control over validation failures, review point 1; D10 — the
explicit recent-history window, review point 4); neither has an A-counterpart.
On 2026-07-05, D9 was extended with the `ValidatingBuilder.validate()`
companion, and **D1 was revised: event import is dropped** — the usage
research that drove the reversal is recorded inside D1 (both review
follow-ups).

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
2026-07-03; D9 facts 2026-07-04) are cited inline as `File.java:line`.

---

## Decision summary

| #   | Decision                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
|-----|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| D1  | **Revised 2026-07-05: event import is dropped.** No `@Import` receptor; `ImportBus`, the import endpoint/routing, and `BlackBox.importsEvent` are removed in PR-B2; `InboxLabel.IMPORT_EVENT` and the `EventImported` system event are deprecated for wire compatibility. External facts enter via reactions to `@External` events or context gateways.                                                                                                                                      |
| D2  | `@Apply` on any aggregate (incl. `AggregatePart`) is a **`ModelError` at model-building time** after cutover — fail fast, never a silent no-op.                                                                                                                                                                                                                                                                                                                                            |
| D3  | Aggregate version advances **+1 per command dispatch**, not per event — `ProcessManager` semantics via `CommandDispatchingPhase` + `VersionIncrement.sequentially`.                                                                                                                                                                                                                                                                                                                        |
| D4  | State update is never forced in any handler. Only `@Assign` must emit ≥1 event (or reject); `@React` emission is optional. Persistence must trigger on a business-state change, not only on events/lifecycle.                                                                                                                                                                                                                                                                              |
| D5  | Dedup is the delivery layer's job; the aggregate `IdempotencyGuard` is **opt-in, off by default**. Recent history is loaded **lazily** from the journal tail, bounded by `historyDepth` (default 100).                                                                                                                                                                                                                                                                                     |
| D6  | A handler mutates the open transaction's `builder()`; validation happens **once, at commit**; any failure rolls the whole transaction back. This structurally eliminates partial validity (brief problem #2).                                                                                                                                                                                                                                                                              |
| D7  | Snapshot-index journal trimming is dropped; count/date-based trimming is deferred to Phase D. The journal is append-only until then.                                                                                                                                                                                                                                                                                                                                                       |
| D8  | Handlers read the **pre-transaction** `state()` and mutate via `builder()`; the applier-only access guard is relaxed to allow `@Assign`/`@React` to touch the builder.                                                                                                                                                                                                                                                                                                                     |
| D9  | *(added 2026-07-04, from PR review)* Preventive validation: `tryAlter {}` runs a mutation on a scratch builder, validates it via the commit path, and merges only when clean — otherwise it returns the violations and leaves live state untouched. `@Assign` rejects; `@React` skips the update (returning `NoReaction` speaks only about emission — D4). The D6 commit-time safety net is unchanged. Companion (2026-07-05): `ValidatingBuilder.validate()` probes any builder in place. |
| D10 | *(added 2026-07-04, from PR review)* Business history access states its window explicitly: `historyBackward(depth)` / `historyContains(depth, predicate)` read up to `depth` latest journal events. The parameterless forms are deprecated, delegating with `depth = historyDepth`; `historyDepth` is demoted to the guard's window plus that default.                                                                                                                                     |

---

## D1 — Event import is dropped (resolves A1; revised 2026-07-05)

*Originally resolved as "import survives via a new `@Import` receptor"
(approved 2026-07-03; recorded below). Revised after the PR #1642 review
questioned the concept post-cutover and requested usage research
([comment](https://github.com/SpineEventEngine/core-jvm/pull/1642#issuecomment-4885996205)).*

**Decision.** Event import is **removed** together with event-sourced loading.
No `@Import` receptor is introduced. Deleted in PR-B2: `ImportBus`,
`EventImportEndpoint`, `EventImportDispatcher`,
`UnsupportedImportEventException`, `AggregateRepository.eventImportRouting` /
`setupImportRouting` (`AggregateRepository.java:120,306,485`),
`AggregateClass.importableEvents()` / `importsEvents()`, and the testing API
`BlackBox.importsEvent` (`server-testlib`, `BlackBox.java:507`). Deprecated
and retained for wire compatibility only: the `IMPORT_EVENT` inbox label
(`spine/server/delivery/inbox.proto`) and the `EventImported` system event
with its emitter `EntityLifecycle.onEventImported`
(`EntityLifecycle.java:251`).

**Research record (2026-07-05).** Import exists since August 2018 for two
scenarios (`ImportBus` Javadoc, `ImportBus.java:49-78`): registering facts
from a legacy or third-party system as the context's own history, and
recording facts inside a context *without intermediate commands or events*.
Its defining mechanics: unicast delivery to exactly one target aggregate
(`ImportBus` is a `UnicastBus`), and **adoption** — the imported event is
journaled as the aggregate's *own* fact, re-stamped with its version, then
posted to `EventBus` like any emitted event.

Usage evidence (org-wide code search, 2026-07-05):

- **Production: one class, ever** — `GoogleGroupPart` in
  `SpineEventEngine/auth`, importing Google Workspace Directory facts
  (`GoogleGroupCreated`, `GoogleGroupAliasesChanged`); that repository is
  dormant (last push 2020-03).
- Nothing in `spine-examples`, nothing in the documentation samples, no
  downstream callers of `setupImportRouting` or `BlackBox.importsEvent`.
- Everything else is this repository's own machinery, fixtures, and tests.

Functional-gap analysis against `@React`: the replay-era value — imported
events *rebuilding state* — dies with event sourcing; that was the concept's
substance. What remains is journal provenance (adopting a foreign fact as the
aggregate's own journal entry) and the no-intermediate-message entry point —
conveniences with standard replacements:

- facts from another Bounded Context — `@React` methods accepting `@External` events;
- facts from a third-party or legacy system — a gateway (adapter, process
  manager, or a plain command) turns them into the context's own commands or
  events;
- bulk seeding and migration — storage-level state writes (the record is the
  source of truth after the cutover);
- test arrangement — real commands and events through `BlackBox`
  (`importsEvent` is removed; the search found no external callers).

**Consequences.** A whole unicast bus, an endpoint, a routing schema, and a
receptor kind vanish — D2, D4, D8, and D9 no longer carry an import case. The
sole known production usage (`auth`) is dormant and outside the rollout; it
breaks on upgrade and migrates to a gateway only if reactivated (delivery
plan, Phase E). `AggregatePart` needs no import coverage either.

**Rejected alternatives (this revision).** Keeping `@Import` per the original
decision — one dormant user in six years does not justify a receptor kind,
its signature/map wiring, and a parallel bus. Renaming (e.g. `@Adopt`) —
churn without removing any complexity.

> **Approved** by product owner, 2026-07-03 (original): separate `@Import`
> annotation, name `@Import`, receptor returns nothing.

> **Revised** by product owner, 2026-07-05: event import is dropped entirely —
> fewer moving parts outweigh the narrow conveniences. The research above is
> the standing answer to "why do we have it and who used it".

---

## D2 — Fate of `@Apply` after cutover (resolves A2)

**Decision.** After cutover, an aggregate (or `AggregatePart`) that declares a
`@Apply` method is **rejected at model-building time** with a `ModelError`. The
error message is **self-contained migration guidance** — it does not reference
this ADR or any design document. Suggested text:

> *"Event appliers (`@Apply`) are no longer supported. Move the state update
> from this method into the `@Assign` or `@React` handler that emits the event,
> applying the change via `builder()`, then delete this method. If this applier
> used `allowImport`, note that event import was removed as well — convert the
> flow into a command or an event reaction."*

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
reaction) dispatch**, regardless of how many events the handler emits —
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

| Receptor            | Contract                                                                                                                                                                                                                                                        |
|---------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `@Assign` (command) | **May** update state via `builder()` and/or set lifecycle flags via `setArchived()` / `setDeleted()` — state update is optional. **Must emit ≥1 event or reject** — empty success stays illegal.                                                                |
| `@React` (reaction) | Same as `@Assign`, **except emitting an event is optional** — a reactor may emit zero events. May update state and set lifecycle flags, or neither. `AggregateEventReactionEndpoint.onEmptyResult` stays a no-op (`AggregateEventReactionEndpoint.java:60-67`). |

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
emit nothing. The only hard emission requirement is `@Assign`'s "emit ≥1 event
or reject".

> **Approved** by product owner, 2026-07-03: command handlers and reactors may
> update state or only emit events (state update is never forced); `@React` may
> emit zero events; `setArchived()`/`setDeleted()` are allowed in `@Assign` and
> `@React`; `@Import` may update state or not — both OK.

> **Amended** by the D1 revision (2026-07-05): event import is dropped; the
> `@Import` clauses above no longer apply.

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
(`HANDLE_COMMAND`) and reacted events (`REACT_UPON_EVENT`) alike, and because
the key includes the target, one event fanned
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

> **Amended** by D10 (2026-07-04): business-logic history access now states its
> window explicitly — `historyBackward(depth)` / `historyContains(depth,
> predicate)`. `historyDepth` remains the guard's window and the default of the
> deprecated parameterless forms.

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
is **relaxed**: `@Assign` and `@React` bodies may read `state()`
(the committed value at dispatch start) and mutate `builder()`. `ApplierWatcher`
and the applier-scoped guard are removed with the appliers (PR-B2 step 3).

> **Approved** by product owner, 2026-07-03: relax the guard; handlers may read
> `state()` (pre-dispatch value) and write `builder()`.

---

## D9 — Preventive state validation in receptors (`tryAlter`)

*Added 2026-07-04 in response to the PR #1642 review, point 1 (how developers
control the state becoming invalid). The mechanism half of review point 2
(reactors) follows by construction. No A-counterpart in the plan.*

**Decision.** Introduce one new Kotlin extension for `TransactionalEntity`,
next to `update {}` / `alter {}` in
`server/src/main/kotlin/io/spine/server/entity/TransactionalEntityExts.kt`:

```kotlin
public fun <I : Any, E : TransactionalEntity<I, S, B>, S : EntityState<I>, B : ValidatingBuilder<S>>
        E.tryAlter(block: B.() -> Unit): List<ConstraintViolation>
```

`tryAlter` is **validate-before-apply**:

1. clone the live transaction builder (`builder()`);
2. run `block` on the clone — the live builder is not touched;
3. `buildPartial()` the candidate and validate it via the same
   `checkEntityState` the commit uses (`AbstractEntity.java:328-349`);
4. no violations → merge the candidate into the live builder, return an empty
   list;
5. violations → leave the live builder **untouched**, return them.

A setter-throwing option (`(set_once)` today; any future one) raises
`io.spine.validation.ValidationException` inside `block` — against the
scratch, not the live builder. `tryAlter` catches it and returns its
violations, so both constraint families (setter-time and build-time) surface
uniformly as the returned list. Any other exception from `block` propagates
unchanged; in every failure mode the live builder stays untouched.

**Why probing the live builder is not enough.** `update {}` / `alter {}`
mutate the transaction's builder directly (`TransactionalEntityExts.kt:63-98`),
and there is no way to un-mutate. Returning `NoReaction` does not help: it
means only "this reaction emits no events" (D4) and says nothing about state —
the dispatch still succeeds, the transaction commits, and per D4 the expanded
store gate fires on a business-state change even with zero events, so an
invalid state already in the live builder reaches commit validation and kills
the dispatch. The only way to withhold an invalid change is to never put it
into the live builder; hence scratch-copy semantics, not a probe of the live
builder.

**Receptor idioms.** Each receptor kind keeps its natural failure vocabulary
(samples in the API sketch below): `@Assign` **rejects** — a business NACK to
the client; `@React` **skips the state update** — the violations came back, so
the reactor does not apply the change, and since the fact it would have
announced did not happen, it typically returns `NoReaction`, alone or as an
`EitherOf…` alternative (an event is a fact and cannot be rejected).
Note (D4): `NoReaction` speaks only about *emission* — "this reaction emits no
events" — and implies nothing about state; whether state changed is the
entity's internal business. In the failure path above the state is untouched
because `tryAlter` withheld the mutation, not because `NoReaction` was
returned.

**Interaction with D3–D6.**

- A mutation withheld by `tryAlter` never reaches the live builder, so
  `changed()` stays `false`: the D4 store gate stays silent and the persisted
  state and version are unchanged (composes with D3/D4).
- The probe and the commit share one validation path (`checkEntityState`), so
  a clean `tryAlter` verdict cannot be contradicted at commit for those
  changes — no verdict drift.
- Consecutive `tryAlter` calls compose: each validates the cumulative
  candidate (the scratch is a clone of the live builder, including previously
  applied changes).
- **The D6 safety net is unchanged.** `tryAlter` is opt-in control; a receptor
  that skips it and commits invalid state gets today's semantics — single
  validate-at-commit, rollback, failed dispatch (`Transaction.java:369-373`).

**Implementation notes for PR-B1** (additive — `ProcessManager`s benefit
immediately, having had exactly this gap all along):

- The same-package placement that already lets `update {}` call the protected
  `builder()` (see the file's API note) also covers `checkEntityState`
  (`AbstractEntity.java:328`).
- Merge mechanics — `clear()` + `mergeFrom(candidate)` on the live builder, or
  swapping the transaction's builder as `incrementStateAndVersion` already
  does (`Transaction.java:343`) — fixed in PR-B1. *Fixed: `clear()` +
  `mergeFrom` — the `Transaction.initAll` pattern; the transaction keeps its
  builder reference, and no new `Transaction` mutator is needed.*
- Java callers: via the existing
  `@file:JvmName("TransactionalEntityExtensions")` facade or a thin protected
  method — decided in PR-B1. *Decided: the facade. A same-name `protected`
  member taking a `Consumer<B>` would shadow the extension in Kotlin
  subclasses (a member beats an extension in overload resolution), breaking
  the receiver-lambda idiom `tryAlter { … }` at every Kotlin call site.*
- KDoc must warn that `block` operates on its receiver (the scratch) only;
  nesting `alter {}` / `update {}` inside `block` would bypass the scratch and
  dirty the live builder.
- Double validation (probe + commit) is accepted; skipping commit validation
  when nothing changed after a clean probe is an optional later optimization,
  not a promise.
- Kotlin test suite (`kotlin-jvm-tester` conventions): clean apply, violation
  return, `(set_once)` folding, consecutive-call composition, and a failed
  `tryAlter` leaving no trace (no store; persisted state/version unchanged).

**Rejected alternatives.**

- *Post-hoc repair callback* — `onValidationError(builder, signal, context,
  wouldBeEvents)` invoked when commit validation fails: it repairs state
  *after* the events describing the transition were produced, risking
  state/event divergence, and needs a new context data structure plus
  re-validation semantics. Prevention keeps the knowledge where it already
  lives — inside the receptor (product owner, 2026-07-04).
- *Probe-the-live-builder API* — cannot serve reactors: there is no un-mutate
  (see above).
- *Making `update {}` / `alter {}` validate eagerly* — changes the semantics
  of released API, breaks legal multi-step mutation sequences whose
  intermediate states are transiently invalid, and pays validation cost on
  every call.

> **Approved** by product owner, 2026-07-04: prevention over repair; name
> `tryAlter`; validate-before-apply (scratch-copy) semantics; setter-thrown
> `ValidationException`s folded into the returned violations.

**Amendment (2026-07-05) — the `validate()` in-place probe.** Review
follow-up
([comment](https://github.com/SpineEventEngine/core-jvm/pull/1642#issuecomment-4885985767)):
in addition to `tryAlter`, `io.spine.validation.ValidatingBuilder` gains a
`default` method probing the builder's *current* content without building:

```java
// In the `validation` repository (io.spine.validation.ValidatingBuilder):
default List<ConstraintViolation> validate() {
    // buildPartial() + ValidatableMessage.validate(); empty list = valid
}
```

- Built from parts already at hand: `buildPartial()` and
  `ValidatableMessage.validate()` (which returns `Optional<ValidationError>`);
  the default method unwraps the error into `List<ConstraintViolation>` — the
  same currency as `tryAlter` and `checkEntityState`. The asymmetry with
  `ValidatableMessage.validate()` is deliberate: builder-side callers consume
  violations directly. A build result that is not a `ValidatableMessage` (not
  produced by Spine codegen) yields an empty list.
- Division of labor: `validate()` **probes in place** — including a builder
  that may already hold invalid content; `tryAlter` **protects** the live
  builder. A command handler about to reject may mutate live and probe — the
  rejection rolls the whole transaction back anyway (D6). A reactor that must
  stay committable prefers `tryAlter` (no un-mutate — see above).
- Works on *any* generated builder — entity states, commands, events — since
  the method lives in the validation library, not in `core-jvm`.
- Cross-repo sequencing: lands in the `validation` repository; `core-jvm`
  picks it up via a `Validation` dependency bump. `tryAlter` (PR-B1) does not
  block on it — it validates via `checkEntityState`.

> **Approved** by product owner, 2026-07-05: add default
> `ValidatingBuilder.validate(): List<ConstraintViolation>`; home — the
> `validation` repository; `tryAlter` unaffected.

---

## D10 — Explicit recent-history window (`historyBackward(depth)`)

*Added 2026-07-04 in response to the PR #1642 review, point 4 (`recentHistory`
"will need to have a parameter now"). Amends the business-access clause of D5.
No A-counterpart in the plan.*

**Decision.** The business-facing history API on `Aggregate` states its
window explicitly, in events, at the call site (edited code stays Java —
plan decision 7):

```java
protected final Iterator<Event> historyBackward(int depth);
protected final boolean historyContains(int depth, Predicate<Event> predicate);
```

Contract:

- up to `depth` most recent journal events, newest first; fewer if the journal
  holds fewer (or, after Phase D, has been trimmed); `depth` must be positive;
- the count is **events**, not commands — after D3 one command may emit
  several events sharing one version;
- the current dispatch's own uncommitted events are excluded (as today);
- the first call triggers the D5 lazy journal-tail read, sized to satisfy the
  request; the result is cached in `RecentHistory`, which becomes a
  framework-internal cache (`Aggregate.recentHistory()` is already
  `@VisibleForTesting`, `Aggregate.java:518-522`); a deeper later call
  re-reads.

**`historyDepth` is demoted to one job.** It remains the per-repository window
of the opt-in `IdempotencyGuard` (D5) — and the default of the deprecated
parameterless forms — an operational knob, no longer a hidden business
parameter. A business call deeper than `historyDepth` simply reads deeper: the
journal is append-only until Phase D (D7).

**Deprecation path (parameterless forms).** `historyBackward()`
(`Aggregate.java:534`) and `historyContains(Predicate)` (`Aggregate.java:541`)
are deprecated, **not** fail-fast: they keep working, delegating to the
explicit forms with `depth = historyDepth`. Old code compiles and runs; its
window changes from "everything since the last snapshot" to "the last
`historyDepth` events" — called out in the migration guide (plan, PR-B3).
Removal at v2.0.0 (final), with the other deprecations. `IdempotencyGuard`
itself calls the parameterized form with the repository's `historyDepth`.

**Why not fail-fast like D2's `@Apply`?** An un-migrated `@Apply` class would
*silently lose behavior* — fail-fast is the only safe answer there. An
un-migrated `historyBackward()` call keeps a well-defined, documented window
close to the old default (100 = the old `DEFAULT_SNAPSHOT_TRIGGER`); breaking
every caller at once buys little.

**Rejected alternatives.**

- *Parameterless as the only API, window = `historyDepth`* (D5 as originally
  approved) — the window is invisible at the call site and couples business
  behavior to an operational knob: tuning the guard would silently change
  domain logic (review point 4).
- *Fail-fast parameterless forms* — rejected by the product owner in favor of
  the `historyDepth`-defaulted delegation (see above).
- *Time-based window* (`historyBackward(since: Timestamp)`) — composable by
  the caller on top of the count-bounded iterator; the count is the
  storage-native bound of a journal-tail read. Possible future sugar, not
  core.

> **Approved** by product owner, 2026-07-04: explicit `depth` parameter on
> `historyBackward` / `historyContains`; parameterless forms deprecated and
> `historyDepth`-defaulted (not fail-fast); `historyDepth` remains the guard's
> window.

---

## Public API sketch (signatures only)

Illustrative shapes for review; final names/packages are fixed here, bodies are
Phase B. New types are Kotlin (delivery plan, decision 7).

**Removed — event import** (revised D1, 2026-07-05). No `@Import` receptor is
introduced; the import path is deleted with the cutover: `ImportBus`,
`EventImportEndpoint`, `EventImportDispatcher`,
`UnsupportedImportEventException`, `AggregateRepository.eventImportRouting` /
`setupImportRouting`, `AggregateClass.importableEvents()` / `importsEvents()`,
and the testing API `BlackBox.importsEvent` (no callers outside this
repository). Wire-compatibility deprecations are listed below.

**Changed — command/reaction handlers** keep their signatures (return events)
and now mutate the builder in-body:

```java
@Assign
ItemAdded handle(AddItem c) {
    builder().addItem(c.getItem());        // NEW: state change in the handler
    return ItemAdded.newBuilder()./* … */.build();
}
```

**New — preventive state validation in receptors** (D9; in
`TransactionalEntityExts.kt`, so `ProcessManager`s get it too). Runs the
mutation on a scratch copy of the builder, validates the candidate, merges
only when clean — a failed attempt never dirties the live builder:

```kotlin
public fun <I : Any, E : TransactionalEntity<I, S, B>, S : EntityState<I>, B : ValidatingBuilder<S>>
        E.tryAlter(block: B.() -> Unit): List<ConstraintViolation>
```

Usage across the receptor kinds. The declared constraint is *reused*,
never duplicated in handler code. Given a state field

```proto
int32 in_stock = 2 [(min).value = "0"];    // invariant: stock never negative
```

a command handler turns violations into a **rejection** — the business NACK
the client can act on:

```kotlin
@Assign
@Throws(OutOfStock::class)
fun handle(cmd: ReserveItems): ItemsReserved {
    val violations = tryAlter { inStock = state.inStock - cmd.quantity }
    if (violations.isNotEmpty()) {                  // would go negative
        throw OutOfStock.newBuilder()
                .setStock(id())
                .setRequested(cmd.quantity)
                .build()
    }
    return itemsReserved { stock = id(); quantity = cmd.quantity }
}
```

a reactor **skips the state update** — an event is a fact and cannot be
rejected. `NoReaction` here says only "this reaction emits no events"; the
state stays untouched because `tryAlter` withheld the change, not because
`NoReaction` was returned (emission and state are orthogonal — D4):

```kotlin
@React
fun on(event: BulkOrderPlaced): EitherOf2<ItemsReserved, NoReaction> {
    val violations = tryAlter { inStock = state.inStock - event.quantity }
    return if (violations.isEmpty())
        EitherOf2.withA(itemsReserved { stock = id(); quantity = event.quantity })
    else
        EitherOf2.withB(noReaction())      // no events; `tryAlter` withheld the change
}
```

Its in-place companion (D9 amendment, 2026-07-05) is
`ValidatingBuilder.validate()` from the `validation` repository — probe any
builder's current content, including one already holding invalid data:

```kotlin
// Command handler: a live probe is fine — a rejection rolls the tx back anyway.
alter { inStock = state.inStock - cmd.quantity }
val violations = builder().validate()
if (violations.isNotEmpty()) { /* throw the rejection */ }
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

**Changed — aggregate history access states its window** (D10). The explicit
forms read up to `depth` latest journal events (newest first), lazily loaded
per D5; the parameterless forms are deprecated and delegate with
`depth = historyDepth`:

```java
protected final Iterator<Event> historyBackward(int depth);                      // NEW
protected final boolean historyContains(int depth, Predicate<Event> predicate);  // NEW
@Deprecated protected final Iterator<Event> historyBackward();                   // → historyBackward(historyDepth)
@Deprecated protected final boolean historyContains(Predicate<Event> predicate); // → historyContains(historyDepth, p)
```

**Deprecated (removed at v2.0.0 (final)):**

```java
io.spine.server.aggregate.Apply                       // annotation
io.spine.server.aggregate.Apply#allowImport
AggregateRepository#setSnapshotTrigger(int)           // no-op → historyDepth
AggregateRepository#snapshotTrigger()
Aggregate#historyBackward(), Aggregate#historyContains(Predicate)  // → depth forms (D10)
AggregateStorage#read(I, int), AggregateStorage#read(I)  // journal reads; load uses readState(I)
AggregateStorage#writeSnapshot(I, Snapshot)
AggregateStorage#truncateOlderThan(int[, Timestamp])  // → Phase D trimming
Aggregate#toSnapshot()
// proto (kept for wire compat, marked deprecated):
//   spine.server.aggregate.Snapshot, AggregateHistory
//   spine.system.server.AggregateHistoryCorrupted   + EntityLifecycle#onCorruptedState
//   spine.system.server.EventImported               + EntityLifecycle#onEventImported (revised D1)
//   spine.server.delivery.InboxLabel.IMPORT_EVENT   (revised D1)
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
`AggregateStorage.readStates`); `BlackBox` / `EventSubject` / `CommandSubject`
(except `BlackBox.importsEvent`, removed with event import — revised D1);
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
- Receptors gain a uniform preventive control point (`tryAlter`, D9): a
  would-be-invalid state is caught **before** the live builder changes, so
  commands reject and reactions skip the update — validation failures become
  business outcomes instead of dispatch errors. Process managers benefit
  immediately.
- Event import is gone (revised D1): a whole unicast bus, an endpoint, a
  routing schema, and a receptor kind vanish from the runtime; external facts
  flow through the standard `@External` event reactions and gateway idioms.
- History reads state their window at the call site (`historyBackward(depth)`,
  D10), decoupling domain logic from the `historyDepth` operational knob.

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
- `BlackBox.importsEvent` is removed with event import (revised D1) — a public
  testing-API break. Org-wide search found no external callers; the migration
  guide covers the rewrite onto real commands/events.

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
- [x] Product owner sign-off on D9 (`tryAlter` preventive validation),
  2026-07-04 — designed in the PR #1642 review follow-up; prevention chosen
  over a post-hoc repair callback; name `tryAlter` confirmed.
- [x] Product owner sign-off on D10 (explicit history window), 2026-07-04 —
  depth parameter on `historyBackward`/`historyContains`; parameterless forms
  deprecated with the `historyDepth` default (not fail-fast).
- [x] Product owner sign-off on the D9 amendment
  (`ValidatingBuilder.validate()`), 2026-07-05 — in-place probe as a `default`
  method in the `validation` repository, returning `List<ConstraintViolation>`.
- [x] Product owner sign-off on the D1 revision (event import dropped),
  2026-07-05 — driven by the usage research requested in review: one dormant
  production usage in six years, and the replay-era rationale is gone.
