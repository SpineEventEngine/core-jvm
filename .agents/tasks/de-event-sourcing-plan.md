# Delivery plan: migrating Aggregates off event sourcing

Brief: [de-event-sourcing-brief.md](de-event-sourcing-brief.md).

This is the execution plan for agents. Each phase lists concrete tasks with
file-level pointers into `core-jvm` and per-repo checklists for the rollout.
Keep this file updated as phases complete; archive it (with the brief) when
the org-wide verification gate passes.

Code claims here were fact-checked against the tree (2026-07-03). The two
design points that a naive reading gets wrong — **state persistence is gated
by entity visibility today**, and **the aggregate version is advanced only as
a side effect of playing events** — are called out inline; do not lose them.

## Locked decisions (product owner, 2026-07-03)

1. **Hard cutover.** Event-sourced loading is removed in one delivery train.
   `@Apply` remains only as a deprecated annotation until its removal in
   v2.0.0 (final). No dual-mode runtime.
2. **No data-migration tooling.** Spine 2.x is pre-GA. See "Data assumptions"
   below for the precise recoverability caveat (it is narrower than "state
   querying users are fine").
3. **Mutation API: `builder()` inside handlers.** `@Assign` / `@React`
   receptors keep their signatures (return events) and mutate state via the
   existing `builder()` (Kotlin: `update {}` / `alter {}` — since 2026-07-06
   protected members of
   `server/src/main/kotlin/io/spine/server/entity/TransactionalEntity.kt`).
   The framework opens the transaction *before* invoking the receptor and
   validates + commits after it returns — structurally like the
   `ProcessManager` dispatch path (`PmEndpoint.runTransactionFor()`),
   **including the once-per-dispatch version increment; see A3**.
   A receptor that must guard against invalid transitions uses `tryAlter {}`
   (ADR D9, added 2026-07-04 from PR review): validate-before-apply on a
   scratch builder — reject or skip the update *before* the live builder is
   dirtied. `builder().validate()` (D9 amendment, 2026-07-05) probes a builder
   in place — e.g., right before throwing a rejection.
4. **Event import is dropped** *(revised 2026-07-05; originally "survives via
   a new receptor annotation" — see ADR D1, revised)*. `ImportBus`, the import
   endpoint/routing, and `BlackBox.importsEvent` are removed in PR-B2;
   `InboxLabel.IMPORT_EVENT` and the `EventImported` system event are
   deprecated for wire compatibility. External facts enter via reactions to
   `@External` events or context gateways.
5. **State history (`EntityHistoryStorage`; renamed
   `EntityStateHistoryStorage`, 2026-07-08)** is in this plan as a later,
   decoupled phase (Phase D). The cutover must not depend on it.
6. **Plan scope:** deep detail for `core-jvm`; dependency-ordered rollout
   checklists for downstream repos; org-wide `@Apply` verification gate.
7. **Languages:** edited code stays Java; new types are Kotlin; new test
   suites are Kotlin (existing Java suites stay Java when updated).

## Target architecture

```
Command / Event(@React)
        │
        ▼
AggregateEndpoint ──► open AggregateTransaction
        │                    │  (tx active; handler reads pre-tx state())
        ▼                    │
receptor body: mutates builder(), returns event message(s)
        │
        ▼
validate built state → advance aggregate version by +1 (once per command)
        → stamp emitted events with the new version → commit tx
        │
        ▼
AggregateStorage.writeAll:
    • EntityRecordStorage  ← latest state  (SOURCE OF TRUTH, ALWAYS incl. state)
    • AggregateEventStorage ← emitted events (traceability journal, append-only)
        │
        ▼
EventBus.post(events)   (unchanged — projections, PMs, catch-up unaffected)
```

Load path: `AggregateRepository.load(id)` reads the latest `EntityRecord`
from the state storage and restores state, version, and lifecycle flags. No
snapshot, no replay. Deduplication is primarily the delivery layer's job; the
aggregate `IdempotencyGuard` is an **opt-in, off-by-default** backstop. Recent
history is loaded **lazily on demand** from the tail of the event journal,
sized to the request: business calls state their own window via
`historyBackward(depth)` / `historyContains(depth, …)` (ADR D10), while the
new `historyDepth` repository setting (default 100) is the guard's window and
the default of the deprecated parameterless forms.

### Two load-critical invariants the cutover MUST establish

These are the sharp edges; both are behavioral changes, not refactors.

- **State is stored unconditionally.** Today
  `AggregateRecords.newStateRecord(aggregate, includeState)`
  (`server/.../aggregate/AggregateRecords.java:129-145`) writes the packed
  business `state` only when `includeState` is true, and its sole caller
  `AggregateStorage.writeState` (`AggregateStorage.java:387-391`) passes
  `queryingEnabled`, which `AggregateRepository.configureQuerying()`
  (`AggregateRepository.java:557-575`) sets true **only when
  `aggregateClass().visibility().isNotNone()`**. So an aggregate whose state
  message is `(entity).visibility = NONE` persists id/version/lifecycle but
  **no state**. Once state is the source of truth, such an aggregate could
  never be reconstructed. The cutover must **decouple state persistence from
  visibility**: always pack the business state into the record for loading;
  keep `queryingEnabled` gating only the read-side query exposure
  (`readStates`). This is the single most important correctness change.

- **Version must be advanced explicitly, once per command.** Today the
  aggregate's own `Version` advances as a side effect of *playing* each event
  (`AggregateTransaction.createVersionIncrement` → `VersionIncrement.fromEvent`,
  `AggregateTransaction.java:91-94`), so a command emitting N events moved the
  version by N. Remove replay and **nothing advances `version()`** at all —
  the next command would re-stamp from a stale version and the state record's
  version would never move. The new rule (product decision): the aggregate
  version advances **by +1 per command handler, not per event** — exactly the
  `ProcessManager` semantics. Reuse the PM mechanism directly: a single
  `CommandDispatchingPhase` with `VersionIncrement.sequentially(tx)` (see
  `PmTransaction.perform`; `AutoIncrement.nextVersion()` is `current + 1`),
  which advances the entity version by one per dispatch. Emitted events carry
  the resulting aggregate version. The per-event `VersionSequence` becomes
  dead code for aggregates — remove or deprecate it. Observable change to
  note in the migration guide: events from one command that previously bore
  distinct versions v+1..v+N now share the single resulting version. Specify
  and test this (A3, PR-B2).

### What stays unchanged

- Events are still first-class: emitted, versioned, stored in the journal,
  posted to `EventBus`, stored in `EventStore`. Projections, process
  managers, catch-up (`CatchUpStorage`), delivery/`Inbox` are unaffected.
- `EventPlayer` / `EventPlayingTransaction` / `TransactionalEventPlayer` /
  `EventDispatchingPhase` **remain** — `Projection` implements `EventPlayer`
  and legitimately keeps playing events (`Projection.java`,
  `ProjectionTransaction.java`). Only `Aggregate`'s *implementation* of
  `EventPlayer` is dropped, never the shared type.
- `StorageFactory` SPI shape: `createAggregateStorage`,
  `createAggregateEventStorage`, `createEntityRecordStorage` all remain
  (the journal is still written). Storage vendors recompile, not rewrite.
  *(Phase D's journal cleanup — the opener of that phase — replaces
  `createAggregateEventStorage` with `createEntityEventStorage`; the legacy
  method and storage class are removed, not deprecated — reversal decided
  2026-07-09 in the PR #1649 review.)*
- Client query path (`QueryService` → `Stand` → `EntityQueryProcessor` →
  `AggregateRepository.findRecords` → `AggregateStorage.readStates`).
- `BlackBox` / `EventSubject` / `CommandSubject` public testing APIs.

### Data assumptions (decision 2, precise form)

Only aggregates that were **visible for querying** (`visibility != NONE`) in
the prior version have a materialized state record (directly, or via
`MirrorToEntityRecord.apply`, `server/.../migration/mirror/MirrorToEntityRecord.java:80-89`,
which only ever ran for querying-visible aggregates). **`NONE`-visibility
aggregates that relied on event replay have no materialized state and are a
hard break** — no Mirror, no pre-cutover state record. State this explicitly
in the migration notes (PR-B3). No tooling is shipped to close the gap.

## Phase A — Design finalization (ADR)

> **✅ COMPLETE — landed in #1642.** The ADR
> `docs/adr/0001-aggregates-without-event-sourcing.md` is **Accepted**,
> resolving A1–A8 plus the review follow-ups D9, D10, and the revised D1
> (event import dropped). The open-point table below is retained for context.

**Deliverable:** a short ADR under `docs/` in `core-jvm` resolving the open
design points below, plus the agreed public-API sketch (signatures only).
Get the ADR reviewed and approved by the product owner before Phase B.

Open points with recommendations:

| #  | Question                                                  | Recommendation                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
|----|-----------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| A1 | Name and shape of the import receptor annotation          | **Revised 2026-07-05 (ADR D1): event import is dropped entirely** — usage research found one dormant production usage in six years. Superseded original recommendation: an `@Import` receptor mirroring `@Assign` with routing via `setupImportRouting`                                                                                                                                                                                                                                                                                                        |
| A2 | Fate of classes that still declare `@Apply` after cutover | Fail fast: `AggregateClass` (**and `AggregatePartClass`**) raises a `ModelError` at model-building time with a migration message. Silent non-invocation is unacceptable. (`@Apply` on a `ProcessManager` is invalid and unsupported — the one such downstream fixture is fixed in `model-tools`; see Phase E)                                                                                                                                                                                                                                                  |
| A3 | Version advancement                                       | Aggregate version advances **+1 per command handler, not per event** (product decision) — the `ProcessManager` semantics. Reuse the PM path: one `CommandDispatchingPhase` + `VersionIncrement.sequentially`. Emitted events carry the resulting version; the per-event `VersionSequence` is removed. Confirm/adjust emitted-event version stamping and document the change from prior per-event versions. Own test                                                                                                                                            |
| A4 | State mutation without emitted events                     | Command handlers and reactors work the same way (**may** update state via `builder()`, may call `setArchived()`/`setDeleted()` — state update never forced), differing only in emission: `@Assign` **must emit ≥1 event or reject**; `@React` **may emit zero events**. *(The `@Import` clause originally here is void — event import is dropped; ADR D1, revised 2026-07-05.)* No blanket "builder touched but no event → reject" and no new "must change state" guard. Lifecycle-flag flips that lived in appliers migrate into the handler body. See ADR D4 |
| A5 | Deduplication + recent-history window                     | Delivery layer owns dedup; the aggregate `IdempotencyGuard` is **opt-in per repository, off by default** (`useIdempotencyGuard()`), kept so it can be removed later. Recent history loaded **lazily on demand** from the journal tail, bounded by `historyDepth` (default 100, = old `DEFAULT_SNAPSHOT_TRIGGER`; per-repository = per-aggregate-type). Guard-off dispatch does only the state read; guard-on pays the bounded journal read. Delivery durable dedup needs a configured `deduplicationWindow` in production. See ADR D5                          |
| A6 | Rejection/exception semantics                             | Transaction rollback discards builder mutations; nothing is stored or posted. Verify `Transaction` rollback covers this; add tests                                                                                                                                                                                                                                                                                                                                                                                                                             |
| A7 | Journal trimming without snapshots                        | Snapshot-index `truncateOlderThan` dies with snapshots. Phase D introduces count/date-based trimming; until then journal grows append-only                                                                                                                                                                                                                                                                                                                                                                                                                     |
| A8 | `state()` visibility inside an open transaction           | Handlers read pre-transaction state, mutate via `builder()` (same shape as `ProcessManager`)                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |

## Phase B — Core runtime cutover (`server` module)

Landing strategy: **PR-B1 (additive) and PR-B3 (docs) build green on their
own. PR-B2 does NOT decompose into green sub-commits** — see the atomicity
note in PR-B2. Each PR bumps the version once per branch and runs
`./gradlew build` + `dokkaGenerate` (proto changes: `clean build`).

### PR-B1 — Preventive validation (additive)

> **✅ COMPLETE — landed in #1643** (`tryAlter`), with its test suite
> consolidated by the `TransactionalEntity` → Kotlin conversion in **#1644**.
> Since #1644 `tryAlter` is a `public @VisibleForTesting` member of
> `server/src/main/kotlin/io/spine/server/entity/TransactionalEntity.kt`
> (no longer in `TransactionalEntityExts.kt`), beside the `protected`
> `update {}` / `alter {}`. **#1645** then moved the `Transaction` base class to
> Kotlin (`entity/Transaction.kt`) — a prerequisite for PR-B2 step 2.

*(2026-07-05: the import-receptor work that used to open this PR is gone —
event import is dropped; see ADR D1, revised. The import-machinery removal
happens in PR-B2, step 12.)*

1. `tryAlter` in
   `server/src/main/kotlin/io/spine/server/entity/TransactionalEntityExts.kt`
   per ADR D9: scratch-copy validate-before-apply returning
   `List<ConstraintViolation>`; fold setter-thrown `ValidationException`s into
   the returned list; fix the merge mechanics and the Java-caller access
   (facade vs. protected method) here. Benefits `ProcessManager` immediately.
2. Kotlin test suite for `tryAlter`: clean apply, violation return,
   `(set_once)` folding, consecutive-call composition, and a failed
   `tryAlter` leaving no trace (no store; persisted state/version unchanged).
3. Cross-repo item (the `validation` repository): default
   `ValidatingBuilder.validate(): List<ConstraintViolation>` built from
   `buildPartial()` + `ValidatableMessage.validate()` (ADR D9 amendment,
   2026-07-05). Consumed here via a `Validation` dependency bump once
   published. `tryAlter` does not block on it — it validates via
   `checkEntityState`.
   **Status (2026-07-07):** the local `Validation` dependency was advanced
   `.448 → .449` in this train (`buildSrc/.../dependency/local/Validation.kt`);
   confirm `.449` is the build shipping the default
   `ValidatingBuilder.validate()` before consuming it here.

### PR-B2 — The cutover (large; ATOMIC build-breaking change)

> **Prerequisite refactors already landed — adjust the file pointers below.**
> `Transaction` is now Kotlin (`entity/Transaction.kt`, #1645, replacing
> `Transaction.java`); `TransactionalEntity` is now Kotlin
> (`entity/TransactionalEntity.kt`, #1644) and carries the `builder()`-mutation
> API (`update` / `alter` / `tryAlter`). `AggregateTransaction`,
> `PmTransaction`, and `EventPlayingTransaction` are **still Java**, so step 2's
> "re-base `AggregateTransaction` onto `Transaction`" now re-bases a Java class
> onto the Kotlin `Transaction.kt` base — exactly as the still-Java
> `PmTransaction` already extends it.

**Atomicity note.** Runtime steps 1–7 are a single non-decomposable
compilation unit: `AggregateEndpoint.applyProducedEvents` → `Aggregate.apply`
→ `play` → `AggregateTransaction.dispatch` → `invokeApplier`, and
`AggregateRepository.restore` → `replay`, are mutually dependent. Removing
`invokeApplier`/`play`/`replay`/`apply` breaks the endpoint, the transaction,
and the repository at once. Furthermore, the A2 `ModelError` means the instant
the model change lands, **every** `@Apply` fixture (steps 13–15) must already
be migrated in the same commit or the module will not build. Treat runtime
rewrite + full fixture/test migration as **one commit**; the reviewer reviews
it as a unit (aided by the mechanical nature of fixture edits), not as a
green-per-commit sequence.

**Dispatch, transaction & version (steps 1–4):**

1. `AggregateEndpoint.handleAndApplyEvents()`
   (`server/.../aggregate/AggregateEndpoint.java`): open
   `AggregateTransaction` before `invokeDispatcher()`; run the command
   dispatch as a single `CommandDispatchingPhase` with
   `VersionIncrement.sequentially`, so the aggregate version advances **+1 per
   command (A3)**; validate built state; commit. Mirror
   `PmEndpoint.runTransactionFor()` / `PmTransaction.perform` directly,
   including the version increment.
2. `AggregateTransaction` (`server/.../aggregate/AggregateTransaction.java`):
   re-base from `EventPlayingTransaction` onto `Transaction` (as
   `PmTransaction` does). Remove event-play dispatch. Use the single
   per-dispatch `VersionIncrement.sequentially` increment (+1), not the old
   per-event `fromEvent`.
3. `Aggregate` (`server/.../aggregate/Aggregate.java`): drop `implements
   EventPlayer`; remove `invokeApplier`, `play`, `replay`, `apply`,
   `ApplierWatcher` gating; relax `ensureAccessToState()` per A8. Leave the
   shared `EventPlayer` type intact for `Projection`. Add
   `historyBackward(int depth)` / `historyContains(int depth, Predicate)` per
   ADR D10; deprecate the parameterless forms (delegating with
   `depth = historyDepth()`) — document the changed window and cover the
   depth forms with Kotlin tests.
4. `@React` path (`AggregateEventReactionEndpoint.java`): same transaction
   pattern; enforce A4 (reactor emission optional; migrate applier-set
   lifecycle flags into the handler body).

**Load path (steps 5–7):**

5. `AggregateStorage` (`server/.../aggregate/AggregateStorage.java`): add
   `readState(I id)` against `stateStorage`. **Make `newStateRecord` always
   pack the business state** (decouple from `queryingEnabled`); keep
   `queryingEnabled` gating only `readStates`/query exposure. Deprecate
   `read(id, batchSize)` → `AggregateHistory`.
6. `AggregateRepository` (`server/.../aggregate/AggregateRepository.java`):
   `load(id)` = state read + instance restore (state, version, lifecycle
   flags — reuse the restore-shape from `Aggregate.restore(Snapshot)` but
   sourced from `EntityRecord`). **Do not** load `RecentHistory` eagerly — make
   the journal-tail read **lazy on demand** (triggered by the opt-in guard on
   dispatch, or by `historyBackward(depth)`/`historyContains(depth, …)` in
   business logic), via `HistoryBackwardOperation` sized to the requested
   depth — `historyDepth` for the guard and the deprecated parameterless
   forms (A5, ADR D10). Keep
   `extends Repository` — do **not** re-parent onto `RecordBasedRepository`
   (minimal-diff constraint). `restore(...)` no longer calls
   `onCorruptedState(...)` (there is no replay to corrupt).
7. `IdempotencyGuard` (`server/.../aggregate/IdempotencyGuard.java`): make it
   **opt-in per repository, off by default** (`AggregateRepository.useIdempotencyGuard()`
   / `enabled` flag) — delivery owns dedup; keep the guard so it can be removed
   later. When enabled, its logic is unchanged, but note **it matches on
   previously *emitted* events whose `EventContext.pastMessage` origin equals the
   incoming signal id** (`IdempotencyGuard.java:119-160`), not on the incoming id
   directly. So the lazy journal-tail read must return emitted events **with
   intact `pastMessage`**. Verify `AggregateStorage.writeEvent`'s
   `event.clearEnrichments()` (`AggregateStorage.java:284-290`) does not strip
   `pastMessage`, and add a test proving guard-on dedup works purely from the
   journal tail with no snapshot, plus a test that guard-off dispatch does no
   journal read.

**Save path (steps 8–10):**

8. `UncommittedHistory` (`server/.../aggregate/UncommittedHistory.java`):
   collapse to a plain uncommitted-events list — no snapshot segmentation.
9. `AggregateStorage.writeAll()`: events + **unconditional** state record
   (business state always packed, per step 5). **Expand the store gate:**
   `AggregateEndpoint.storeAndPost` (`AggregateEndpoint.java:83-100`) today calls
   `store()` only on `withEvents || lifecycleFlags changed`. Because a handler
   may now mutate business state with no event and no lifecycle change (a
   zero-event `@React`, D4/A4), also store when the **built state changed** —
   else the change is silently dropped (never persisted, lingers in cache).
   Add a test for a zero-event, state-only reactor.
10. `Aggregate.toSnapshot()`, `AggregateStorage.writeSnapshot()`: deprecate.

**`@Apply` fail-fast & deprecations (steps 11–12):**

11. `@Apply` (`server/.../aggregate/Apply.java`): `@Deprecated` with
    migration Javadoc. `EventApplierSignature`/`Applier` retained only to
    *detect* appliers so `AggregateClass` **and `AggregatePartClass`** raise
    `ModelError` (A2). Cover `AggregatePart`/`AggregatePartClass`/
    `PartFactory` explicitly: parts are equally event-sourced and load via
    the same `loadOrCreate` path (`AggregateRoot.partState`,
    `AggregateRoot.java:87-91`).
12. Deprecate: `AggregateRepository.setSnapshotTrigger()` /
    `snapshotTrigger()` (no-op, superseded by `historyDepth`),
    `AggregateHistoryCorrupted` system event
    (`server/src/main/proto/spine/system/server/diagnostic_events.proto`) and
    its sole emitter `EntityLifecycle.onCorruptedState`,
    `Snapshot`-related proto messages (`spine/server/aggregate/aggregate.proto`
    — kept for wire compat, marked deprecated), snapshot-index
    `truncateOlderThan` overloads (A7). **Also handle
    `server-testlib`'s `DiagnosticLog.on(AggregateHistoryCorrupted)`
    subscriber** (`server-testlib/.../blackbox/probe/DiagnosticLog.java:101-111`)
    — the event will never fire post-cutover; decide remove vs. no-op-retain.
    **Remove the event-import path (revised D1):** `ImportBus`,
    `EventImportEndpoint`, `EventImportDispatcher`,
    `UnsupportedImportEventException`,
    `AggregateRepository.eventImportRouting` / `setupImportRouting`,
    `AggregateClass.importableEvents()` / `importsEvents()`, and
    `BlackBox.importsEvent` (`server-testlib`). Deprecate for wire
    compatibility: `InboxLabel.IMPORT_EVENT` (`spine/server/delivery/inbox.proto`),
    the `EventImported` system event and its emitter
    `EntityLifecycle.onEventImported`.

**Fixture and test migration (steps 13–15 — same commit; build must stay green):**

13. Migrate every `server/src/testFixtures` aggregate declaring `@Apply`:
    **64 files / 127 `@Apply` methods** (≈62 classes extending
    `Aggregate`/`AggregatePart`). Move each applier body into the
    corresponding `@Assign`/`@React` handler. `allowImport = true` cases (e.g.
    `server/src/testFixtures/java/io/spine/server/integration/given/DocumentAggregate.java`)
    convert to command or reaction dispatch — import is dropped (revised D1);
    this includes the delivery fixtures exercising the import path
    (`CalcAggregate`, `NastyClient`). Java fixtures stay Java.
14. Update `server/src/test` suites (**~7 direct `@Apply` occurrences**, plus
    the behavioral rewrites): `ApplierTest` becomes the model-error test for
    A2; `AggregateTest` rewrites replay cases into load-from-state cases. New
    suites in Kotlin.
15. `server-testlib`: migrate the 2 fixtures (`BbProjectAggregate`,
    `BbReportAggregate`); verify `BlackBox` behavior unchanged apart from
    removing `importsEvent` (revised D1); resolve the `DiagnosticLog`
    subscription (step 12).

### PR-B3 — Docs & polish

16. Rewrite `Aggregate` class-level Javadoc ("Adding event appliers"
    section, lines ~97–122), `AggregateRepository`, `Apply` deprecation
    text, package-info. Run `review-docs`.
17. Migration guide: `docs/` note covering the handler migration recipe,
    the preventive-validation recipe (`tryAlter` and `builder().validate()`,
    ADR D9), the removal of event import with its replacement idioms
    (`@External` event reactions, gateways, storage-level seeding —
    revised D1), removed snapshot config,
    the idempotency-window semantics change (A5),
    the history-window change (`historyBackward(depth)` explicit; the
    deprecated parameterless forms now read the last `historyDepth` events —
    ADR D10), and the **precise data caveat** — only querying-visible
    aggregates survive; `NONE`-visibility replay-only aggregates are a hard
    break (decision 2 / Data assumptions).

## Phase C — Storage & SPI verification

1. Audit in-repo `StorageFactory` implementations (in-memory, system
   context) for snapshot assumptions; simplify `ReadOperation` /
   `HistoryBackwardOperation` to journal-tail reads.
2. Verify `io.spine.server.migration.mirror.MirrorStorage` /
   `MirrorToEntityRecord` still materializes `EntityRecord`s correctly — it
   becomes *more* important as the record is now the source of truth. Note
   its `NONE`-visibility limitation (Data assumptions).
3. Smoke-build **all** storage backends against the new core snapshot. They
   implement `RecordStorage`-level SPI, so expect recompile-only; fix fallout
   where tests assumed snapshots/replay:
   - `jdbc-storage`, `gcloud-jvm`, `firebase-storage`
   - **`delivery-server`** — ships `RedisStorageFactory`
     (`delivery-server/storage/redis/.../RedisStorageFactory.java`) and
     `HazelcastStorageFactory`
     (`delivery-server/storage/hazelcast/.../HazelcastStorageFactory.java`),
     both implementing `StorageFactory.createRecordStorage`. It is a storage
     vendor, not only a service.

## Phase D — entity history: journal cleanup & state history to depth

Decoupled; starts after Phase B is merged and stable. Delivery order within
the phase (decided 2026-07-08): the journal cleanup (see the phase opener
below) lands first as its own PR, so that the journal trimming (item 3) is
built against `EntityEventStorage` from the start; the state-history items
(items 1 and 2) are
independent and may land in parallel or after.

### Phase opener: journal cleanup (`EntityEventHistory` / `EntityEventStorage`)

> **Implemented on `de-event-sourcing-phase-D` (2026-07-09), PR #1649** — see
> the "Implementation notes" in the detailed task file for the deliberate
> deltas (`ReadOperation`/`HistoryBackwardOperation` deleted in favor of
> journal-tail reads; `writeSnapshot` removed; `UncommittedHistory.get()`
> returns a single `EntityEventHistory`). **Reversal (product owner,
> 2026-07-09, in review):** the legacy storage machinery is REMOVED, not
> deprecated — `AggregateEventStorage`, `AggregateEventRecordColumn`,
> `createAggregateEventStorage`, `TruncateOperation`, and the snapshot-index
> `truncateOlderThan` are gone; only the proto messages remain (marked
> `deprecated`) for wire parseability. Pending review and merge.

Naming locked 2026-07-08 (product owner): the journal types move to the
**entity** level — events are emitted by entities, not by aggregates alone.

- Introduce a snapshot-free **`EntityEventHistory`** message and deprecate
  `AggregateHistory` — its `snapshot` field is dead weight after the cutover,
  and the "history = snapshot + tail" semantics no longer hold.
- Introduce **`EntityEventStorage`** — the journal of events emitted by an
  entity — and deprecate `AggregateEventStorage`, with
  `StorageFactory.createAggregateEventStorage` superseded by a new
  `createEntityEventStorage`. Initial rollout covers aggregates;
  `ProcessManager` journaling is planned for a near-future PR, so nothing in
  the new types may be aggregate-specific (the same constraint as item 4
  above).
  Record level settled 2026-07-08: **clean replacement** — a new
  `EntityEventRecord` supersedes the deprecated `AggregateEventRecord`;
  pre-upgrade journal rows stay in the legacy record kind, invisible to the
  new reads (see the data caveat in the detailed task).

Detailed task:
[`introduce-event-history-type.md`](introduce-event-history-type.md).
The count/date journal trimming (item 3 above) shipped together with this
opener in PR #1649, built against `EntityEventStorage`; the snapshot-index
truncation and the legacy `AggregateEventStorage` were removed outright
(see the reversal note above).
          
## Phase E — Entity state history for Aggregates

1. New Kotlin storage contract `EntityStateHistoryStorage` (renamed from the
   brief's `EntityHistoryStorage`, 2026-07-08 — "state history" stays
   unambiguous next to the `EntityEventStorage` event journal below) in
   `server/src/main/kotlin/io/spine/server/entity/history/` storing recent
   `EntityRecord` versions per entity to a configured depth, over the
   standard `RecordStorage` SPI (works on all backends without vendor code).
   **Requirement (product owner, 2026-07-10):** each stored record carries
   the time its state became current — the timestamp of the record's
   `Version`, stamped once per dispatch since A3 — as a queryable column
   beside the entity id and the version number. This is the temporal axis
   for the "state at time T" query (item 5).
2. Repository-level config (depth, enable/disable); write hook in
   `AggregateRepository.doStore()`. Note for the "state at time T" query:
   count-based retention bounds how far back `T` can be answered — an entity
   updated often forgets quickly. Consider an optional duration-based
   retention ("keep states for the last N days") mirroring the count/date
   shape of `EntityEventStorage.truncate`.
3. Count/date-based journal trimming replacing the deprecated
   snapshot-index truncation (A7). **✅ Shipped with the phase opener in
   PR #1649** (pulled in when the snapshot-index truncation was removed
   rather than deprecated): `EntityEventStorage.truncate(keepMostRecent)` /
   `truncate(keepMostRecent, olderThan)` — per-entity recent-window
   protection — with delegating `AggregateStorage.truncate(...)` maintenance
   methods.
4. Design for future `ProcessManager` reuse (brief item 4) — the contract
   must not be aggregate-specific; actual PM wiring is out of scope.
5. Read API for debugging/analysis (state history alongside the journal).
   **Must support the query "state at time T" (product owner, 2026-07-10):**
   given an entity id and a timestamp `T`, return the state the entity had
   at `T` — the retained record with the highest version among those whose
   effectiveness time (item 1) is not later than `T` (inclusive; the version
   number breaks same-instant ties). The answer is honest about retention:
   when `T` precedes the oldest *retained* record, the query returns empty —
   "not answerable from the retained window" — rather than guessing with the
   oldest record; a `T` predating the entity is likewise empty. Baseline
   implementation: read the per-entity window (bounded by the configured
   depth) and select in memory — portable over the `RecordStorage` SPI on
   all backends; a backend may push the `time <= T` comparison down once
   `Timestamp` columns are comparable in filters (cf. the orderable-types
   work, issue #1217).

## Phase F — entity event history and state history for Process Managers

TBD

## Phase G — Downstream rollout (dependency order)

Publish a `core-jvm` snapshot after Phase B; then migrate consumers in
order. Per-repo checklist: bump core version → migrate any real aggregates
(applier bodies into handlers) → build green → repo-local grep
gate (`grep -rn "@Apply" --include=*.java --include=*.kt`, excluding
`build/` and `generated/` → zero hits).

**Reality check (verified 2026-07-03):** most listed repos have **no
production aggregate migration** — their `@Apply` is confined to test
fixtures or vendored doc copies. Do not over-scope.

| Order | Repos                           | Actual `@Apply` work                                                                                                                                                                                                                                                                                                                    |
|-------|---------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1     | `core-jvm-compiler`             | **Test fixtures only.** No codegen/validation reference to appliers. Has a `routing-tests` fixture `.../given/home/HomeAutomationContext.kt` (`DeviceAggregate`, 2 methods)                                                                                                                                                             |
| 2     | `jdbc-storage`, `gcloud-jvm`    | **None** — zero `@Apply`. Recompile + test-fallout only (already smoke-built in Phase C)                                                                                                                                                                                                                                                |
| 2     | `firebase-storage`              | **Test env only** — one aggregate in `firebase-mirror/src/test/.../given/FirebaseMirrorTestEnv.java`; no product aggregates                                                                                                                                                                                                             |
| 3     | `delivery-server`               | Storage vendor (see Phase C) **and** has aggregate fixtures (`SessionRegistry`, `GreatGreeter`). Keep the SPI recompile separate from the fixture migration                                                                                                                                                                             |
| 4     | `chat-bot`                      | Product/domain repo — verify whether `@Apply` is production or fixture before scoping                                                                                                                                                                                                                                                   |
| 4     | `model-tools`                   | **Test fixtures only** — `EditAggregate` (aggregate) plus `RenameProcMan`, a `ProcessManager` carrying a stray `@Apply`. `@Apply` on a `ProcessManager` is invalid; `model-tools` removes it (repo owner will update). Confirm `ModelCheckTest` still passes afterward                                                                  |
| 5     | `examples` (spine-examples org) | brief item 6 — real sample aggregates                                                                                                                                                                                                                                                                                                   |
| 6     | `documentation`, `spine.io`     | **12** files with `@Apply`: 10 Java sample aggregates under `docs/_code/examples/{airport,blog,kanban,todo-list}` + `docs/_code/samples/.../TaskAggregate.java`, and 1 prose file `docs/content/docs/introduction/_index.md`. **`docs/_code/examples/*` are vendored copies of the `examples` repo** — migrate in lockstep with Order 5 |

Excluded from the rollout:
- **Retired/archived** — `core-java-1x` (the 1.x line keeps event sourcing),
  `mc-java`, `web`. Do not touch; excluded from the Phase F gate.
- **Dormant** — `users`, `roles`, `organizations`, and `auth`. Not scheduled
  here; migrate only if/when a repo is reactivated. Out of the Phase F blocking
  set for now (they may still contain `@Apply`). `auth` holds the sole known
  production usage of event import (`GoogleGroupPart`, last push 2020); on
  reactivation it migrates to a gateway or reaction (revised D1).

Confirm no other **active** org repo implements
`AggregateStorage`/`StorageFactory` or declares `@Apply` before declaring
Phase F clean.

## Phase F — Org-wide verification gate

The brief's completion criterion: **no `@Apply` in the active SpineEventEngine
repos except the deprecated annotation type itself.**

1. Script an org-wide check (`gh search code` or local clone sweep) for
   `@Apply` / `io.spine.server.aggregate.Apply`; allowed hits: the
   annotation source, its tests for the `ModelError` path, migration docs.
   Exclude retired/archived repos (`mc-java`, `web`, `core-java-1x`) and the
   dormant repos (`users`, `roles`, `organizations`, `auth`) from the sweep.
2. Run the check; file issues for stragglers; re-run until clean.
3. Archive this plan and the brief to `.agents/tasks/archive/`.

## Per-phase verification (every PR)

- `JAVA_HOME` → Corretto 17; `./gradlew build` (proto changes: `clean build`).
- `./gradlew dokkaGenerate` for any `.kt`/`.java` doc-bearing change.
- Reviews: `spine-code-review`, `kotlin-engineer` (Kotlin), `review-docs`
  (docs/Javadoc), via `pre-pr`.
- Version bumped once per branch (`version-bumped` guard).
- New tests: Kotlin, JUnit 5 + Kotest assertions, backticked test names,
  `Spec` suffix.

## Risks

| Risk                                                                                                                                                     | Mitigation                                                                                                                                                                                              |
|----------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **`NONE`-visibility aggregates persist no state** and become unloadable                                                                                  | Decouple state write from `queryingEnabled` (PR-B2 step 5/9) — the single most important correctness change; test load of a `NONE`-visibility aggregate                                                 |
| **Aggregate version stops advancing** once replay is gone                                                                                                | A3: reuse the PM per-dispatch `VersionIncrement.sequentially` (+1 per command); dedicated test asserting version increments by exactly 1 per command, regardless of event count                         |
| Dropping the eager aggregate dedup guard could let duplicates through after a JVM restart / cache eviction when no delivery `deduplicationWindow` is set | Guard kept as opt-in backstop (A5/D5); document that production sets a `deduplicationWindow`; when guard on, verify `clearEnrichments` keeps `pastMessage` and test journal-tail dedup with no snapshot |
| Accidentally removing the shared `EventPlayer` type breaks `Projection`                                                                                  | Drop only `Aggregate`'s `implements`; keep the entity-layer type                                                                                                                                        |
| A4: over-strict emission guard rejects a legal empty `@React`                                                                                            | Per-receptor rule (only `@Assign` must emit ≥1 event); tests for an empty reactor and a lifecycle-only handler                                                                                          |
| Removing event import breaks unknown external users of `ImportBus` / `BlackBox.importsEvent`                                                             | Org-wide research (2026-07-05, ADR D1 revision) found none outside the dormant `auth`; replacement idioms documented in the migration guide (PR-B3)                                                     |
| PR-B2 is large and cannot land green-per-commit                                                                                                          | Accept it as one atomic commit; fixture edits are mechanical and reviewable in bulk; keep runtime diff Java-minimal                                                                                     |
| Storage backends (incl. `delivery-server` redis/hazelcast) break on state-write semantics                                                                | Phase C smoke builds all vendors before the rollout wave                                                                                                                                                |
| `AggregatePart` silently breaks                                                                                                                          | PR-B2 step 11 covers `AggregatePart`/`AggregatePartClass`/`PartFactory` in the A2 fail-fast and the state-load path                                                                                     |

## Out of scope

- Data migration tooling (decision 2).
- `ProcessManager` wiring for both state history and event journaling
  (Phase D designs for them only; PM journaling is planned for a
  near-future PR).
- Removing the deprecated `@Apply` annotation itself and the other
  deprecations — happens at v2.0.0 (final), tracked separately.
