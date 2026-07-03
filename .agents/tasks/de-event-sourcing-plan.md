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
   existing `builder()` (Kotlin: `update {}` / `alter {}` from
   `server/src/main/kotlin/io/spine/server/entity/TransactionalEntityExts.kt`).
   The framework opens the transaction *before* invoking the receptor and
   validates + commits after it returns — structurally like the
   `ProcessManager` dispatch path (`PmEndpoint.runTransactionFor()`),
   **including the once-per-dispatch version increment; see A3**.
4. **Event import survives** via a new receptor annotation (working name
   `@Import`, final name in Phase A). `ImportBus` and import routing stay.
5. **State history (`EntityHistoryStorage`)** is in this plan as a later,
   decoupled phase (Phase D). The cutover must not depend on it.
6. **Plan scope:** deep detail for `core-jvm`; dependency-ordered rollout
   checklists for downstream repos; org-wide `@Apply` verification gate.
7. **Languages:** edited code stays Java; new types are Kotlin; new test
   suites are Kotlin (existing Java suites stay Java when updated).

## Target architecture

```
Command / Event(@React) / ImportedEvent(@Import)
        │
        ▼
AggregateEndpoint ──► open AggregateTransaction
        │                    │  (tx active; handler reads pre-tx state())
        ▼                    │
receptor body: mutates builder(), returns event message(s)
        │                       (@Import returns nothing — the event IS the fact)
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
snapshot, no replay. `RecentHistory` (for `IdempotencyGuard` and
`historyBackward()`) is loaded from the tail of the event journal, bounded by
a new `historyDepth` repository setting.

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

**Deliverable:** a short ADR under `docs/` in `core-jvm` resolving the open
design points below, plus the agreed public-API sketch (signatures only).
Get the ADR reviewed and approved by the product owner before Phase B.

Open points with recommendations:

| #  | Question                                                                               | Recommendation                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
|----|----------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| A1 | Name and shape of the import receptor annotation                                       | `@Import` in `io.spine.server.aggregate`, Kotlin, method form mirroring `@Assign`: mutates `builder()`, returns nothing (the imported event *is* the fact); routing unchanged via `setupImportRouting`                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| A2 | Fate of classes that still declare `@Apply` after cutover                              | Fail fast: `AggregateClass` (**and `AggregatePartClass`**) raises a `ModelError` at model-building time with a migration message. Silent non-invocation is unacceptable. (`@Apply` on a `ProcessManager` is invalid and unsupported — the one such downstream fixture is fixed in `model-tools`; see Phase E)                                                                                                                                                                                                                                                                                                                                            |
| A3 | Version advancement                                                                    | Aggregate version advances **+1 per command handler, not per event** (product decision) — the `ProcessManager` semantics. Reuse the PM path: one `CommandDispatchingPhase` + `VersionIncrement.sequentially`. Emitted events carry the resulting version; the per-event `VersionSequence` is removed. Confirm/adjust emitted-event version stamping and document the change from prior per-event versions. Own test                                                                                                                                                                                                                                      |
| A4 | State mutation without emitted events                                                  | For `@Assign`: must emit ≥1 event or reject (unchanged contract). For `@React`/`@Import`: **distinguish business-state mutation from lifecycle-flag changes.** A zero-event reaction that only flips archived/deleted is legal today (`AggregateEventReactionEndpoint.onEmptyResult` is a no-op; `AggregateEndpoint.storeAndPost` stores on lifecycle change with no events) and must stay legal. `@Import` legitimately mutates business state with no emitted event. So the commit-time guard is **endpoint/receptor-scoped**, not a blanket "builder touched but no event → reject" — that rule would break both archive-on-react and every `@Import` |
| A5 | `historyDepth` default (recent-history window for idempotency and `historyBackward()`) | Default to the old `DEFAULT_SNAPSHOT_TRIGGER` value (100) to keep the effective dedup window; configurable per repository. **Semantics change:** the window shifts from "events since last snapshot" to "last `historyDepth` journal events" — document it                                                                                                                                                                                                                                                                                                                                                                                               |
| A6 | Rejection/exception semantics                                                          | Transaction rollback discards builder mutations; nothing is stored or posted. Verify `Transaction` rollback covers this; add tests                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| A7 | Journal trimming without snapshots                                                     | Snapshot-index `truncateOlderThan` dies with snapshots. Phase D introduces count/date-based trimming; until then journal grows append-only                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| A8 | `state()` visibility inside an open transaction                                        | Handlers read pre-transaction state, mutate via `builder()` (same shape as `ProcessManager`)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |

## Phase B — Core runtime cutover (`server` module)

Landing strategy: **PR-B1 (additive) and PR-B3 (docs) build green on their
own. PR-B2 does NOT decompose into green sub-commits** — see the atomicity
note in PR-B2. Each PR bumps the version once per branch and runs
`./gradlew build` + `dokkaGenerate` (proto changes: `clean build`).

### PR-B1 — Import receptor (additive)

1. New Kotlin annotation (per A1) + signature/receptor classes in
   `server/src/main/kotlin/io/spine/server/aggregate/` and
   `.../aggregate/model/` (pattern: `EventApplierSignature.java`,
   `Applier.java`, but as new Kotlin types). Verify the new receptor map does
   not collide with the existing applier map during signature scanning
   (`AggregateClass`/`ModelClass` receptor registration) — an event class may
   temporarily have both an `@Apply` and an `@Import` handler during
   migration; confirm that is allowed or explicitly rejected.
2. `AggregateClass` (`server/.../aggregate/model/AggregateClass.java`):
   register the new receptor map; `importableEvents()` / `importsEvents()`
   read from it, falling back to `@Apply(allowImport = true)` until PR-B2.
3. `EventImportEndpoint` (`server/.../aggregate/EventImportEndpoint.java`):
   prefer the new receptor when present.
4. New Kotlin test suite (`kotlin-jvm-tester` conventions) + one fixture
   aggregate using `@Import`.

### PR-B2 — The cutover (large; ATOMIC build-breaking change)

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
   shared `EventPlayer` type intact for `Projection`. `historyBackward()` /
   `historyContains()` now iterate the depth-bounded `RecentHistory` —
   document the changed window.
4. `@React` path (`AggregateEventReactionEndpoint.java`): same transaction
   pattern; enforce A4 (keep the legal zero-event lifecycle-only reaction).

**Load path (steps 5–7):**

5. `AggregateStorage` (`server/.../aggregate/AggregateStorage.java`): add
   `readState(I id)` against `stateStorage`. **Make `newStateRecord` always
   pack the business state** (decouple from `queryingEnabled`); keep
   `queryingEnabled` gating only `readStates`/query exposure. Deprecate
   `read(id, batchSize)` → `AggregateHistory`.
6. `AggregateRepository` (`server/.../aggregate/AggregateRepository.java`):
   `load(id)` = state read + instance restore (state, version, lifecycle
   flags — reuse the restore-shape from `Aggregate.restore(Snapshot)` but
   sourced from `EntityRecord`); load `RecentHistory` from the journal tail
   via `HistoryBackwardOperation` bounded by `historyDepth` (A5). Keep
   `extends Repository` — do **not** re-parent onto `RecordBasedRepository`
   (minimal-diff constraint). `restore(...)` no longer calls
   `onCorruptedState(...)` (there is no replay to corrupt).
7. `IdempotencyGuard` (`server/.../aggregate/IdempotencyGuard.java`): logic
   unchanged, but note **it matches on previously *emitted* events whose
   `EventContext.pastMessage` origin equals the incoming signal id**
   (`IdempotencyGuard.java:119-160`), not on the incoming id directly. So the
   journal-tail `RecentHistory` read must return emitted events **with intact
   `pastMessage`**. Verify `AggregateStorage.writeEvent`'s
   `event.clearEnrichments()` (`AggregateStorage.java:284-290`) does not strip
   `pastMessage`, and add a test proving dedup works purely from the journal
   tail with no snapshot.

**Save path (steps 8–10):**

8. `UncommittedHistory` (`server/.../aggregate/UncommittedHistory.java`):
   collapse to a plain uncommitted-events list — no snapshot segmentation.
9. `AggregateStorage.writeAll()`: events + **unconditional** state record
   (business state always packed, per step 5).
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

**Fixture and test migration (steps 13–15 — same commit; build must stay green):**

13. Migrate every `server/src/testFixtures` aggregate declaring `@Apply`:
    **64 files / 127 `@Apply` methods** (≈62 classes extending
    `Aggregate`/`AggregatePart`). Move each applier body into the
    corresponding `@Assign`/`@React` handler (or `@Import` receptor for
    `allowImport = true` cases, e.g.
    `server/src/testFixtures/java/io/spine/server/integration/given/DocumentAggregate.java`).
    Java fixtures stay Java.
14. Update `server/src/test` suites (**~7 direct `@Apply` occurrences**, plus
    the behavioral rewrites): `ApplierTest` becomes the model-error test for
    A2; `AggregateTest` rewrites replay cases into load-from-state cases. New
    suites in Kotlin.
15. `server-testlib`: migrate the 2 fixtures (`BbProjectAggregate`,
    `BbReportAggregate`); verify `BlackBox` behavior unchanged; resolve the
    `DiagnosticLog` subscription (step 12).

### PR-B3 — Docs & polish

16. Rewrite `Aggregate` class-level Javadoc ("Adding event appliers"
    section, lines ~97–122), `AggregateRepository`, `Apply` deprecation
    text, package-info. Run `review-docs`.
17. Migration guide: `docs/` note covering the handler migration recipe,
    import receptor, removed snapshot config, the idempotency-window
    semantics change (A5), and the **precise data caveat** — only
    querying-visible aggregates survive; `NONE`-visibility replay-only
    aggregates are a hard break (decision 2 / Data assumptions).

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

## Phase D — `EntityHistoryStorage` (state history to depth)

Decoupled; starts after Phase B is merged and stable.

1. New Kotlin storage contract `EntityHistoryStorage` in
   `server/src/main/kotlin/io/spine/server/entity/history/` storing recent
   `EntityRecord` versions per entity to a configured depth, over the
   standard `RecordStorage` SPI (works on all backends without vendor code).
2. Repository-level config (depth, enable/disable); write hook in
   `AggregateRepository.doStore()`.
3. Count/date-based journal trimming replacing the deprecated
   snapshot-index truncation (A7).
4. Design for future `ProcessManager` reuse (brief item 4) — the contract
   must not be aggregate-specific; actual PM wiring is out of scope.
5. Read API for debugging/analysis (state history alongside the journal).

## Phase E — Downstream rollout (dependency order)

Publish a `core-jvm` snapshot after Phase B; then migrate consumers in
order. Per-repo checklist: bump core version → migrate any real aggregates
(applier bodies into handlers / `@Import`) → build green → repo-local grep
gate (`grep -rn "@Apply" --include=*.java --include=*.kt`, excluding
`build/` and `generated/` → zero hits).

**Reality check (verified 2026-07-03):** most listed repos have **no
production aggregate migration** — their `@Apply` is confined to test
fixtures or vendored doc copies. Do not over-scope.

| Order | Repos | Actual `@Apply` work |
|-------|-------|----------------------|
| 1 | `core-jvm-compiler`, `mc-java` | **Test fixtures only.** No codegen/validation reference to appliers. Each has the same `routing-tests` fixture `.../given/home/HomeAutomationContext.kt` (`DeviceAggregate`, 2 methods) |
| 2 | `jdbc-storage`, `gcloud-jvm` | **None** — zero `@Apply`. Recompile + test-fallout only (already smoke-built in Phase C) |
| 2 | `firebase-storage` | **Test env only** — one aggregate in `firebase-mirror/src/test/.../given/FirebaseMirrorTestEnv.java`; no product aggregates |
| 3 | `web` | **Integration test-app only** (`web/integration-tests/test-app/.../given/{Task,Project,UserInfo}Aggregate.java`); the shipped `web` library has none. Depends only on core + compiler (not on any storage repo) — can migrate right after Order 1 |
| 3 | `delivery-server` | Storage vendor (see Phase C) **and** has aggregate fixtures (`SessionRegistry`, `GreatGreeter`). Keep the SPI recompile separate from the fixture migration |
| 4 | `users`, `roles`, `organizations`, `chat-bot` | Product/domain repos — verify each whether `@Apply` is production or fixture before scoping |
| 4 | `model-tools` | **Test fixtures only** — `EditAggregate` (aggregate) plus `RenameProcMan`, a `ProcessManager` carrying a stray `@Apply`. `@Apply` on a `ProcessManager` is invalid; `model-tools` removes it (repo owner will update). Confirm `ModelCheckTest` still passes afterward |
| 5 | `examples` (spine-examples org) | brief item 6 — real sample aggregates |
| 6 | `documentation`, `spine.io` | **12** files with `@Apply`: 10 Java sample aggregates under `docs/_code/examples/{airport,blog,kanban,todo-list}` + `docs/_code/samples/.../TaskAggregate.java`, and 1 prose file `docs/content/docs/introduction/_index.md`. **`docs/_code/examples/*` are vendored copies of the `examples` repo** — migrate in lockstep with Order 5 |

Excluded: `core-java-1x` (the 1.x line keeps event sourcing). Confirm no
other org repo implements `AggregateStorage`/`StorageFactory` or declares
`@Apply` before declaring Phase F clean.

## Phase F — Org-wide verification gate

The brief's completion criterion: **no `@Apply` anywhere in the
SpineEventEngine org except the deprecated annotation type itself.**

1. Script an org-wide check (`gh search code` or local clone sweep) for
   `@Apply` / `io.spine.server.aggregate.Apply`; allowed hits: the
   annotation source, its tests for the `ModelError` path, migration docs.
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

| Risk | Mitigation |
|------|-----------|
| **`NONE`-visibility aggregates persist no state** and become unloadable | Decouple state write from `queryingEnabled` (PR-B2 step 5/9) — the single most important correctness change; test load of a `NONE`-visibility aggregate |
| **Aggregate version stops advancing** once replay is gone | A3: reuse the PM per-dispatch `VersionIncrement.sequentially` (+1 per command); dedicated test asserting version increments by exactly 1 per command, regardless of event count |
| Idempotency window shrinks from "since last snapshot" to `historyDepth`, and dedup relies on `pastMessage` surviving in the journal tail | A5 default = old trigger; verify `clearEnrichments` keeps `pastMessage`; test dedup from journal tail with no snapshot |
| Accidentally removing the shared `EventPlayer` type breaks `Projection` | Drop only `Aggregate`'s `implements`; keep the entity-layer type |
| A4 blanket "no event → reject" breaks archive-on-react and every `@Import` | Endpoint/receptor-scoped guard comparing built business state, not "builder touched"; tests for react-that-only-archives and for `@Import` |
| PR-B2 is large and cannot land green-per-commit | Accept it as one atomic commit; fixture edits are mechanical and reviewable in bulk; keep runtime diff Java-minimal |
| Storage backends (incl. `delivery-server` redis/hazelcast) break on state-write semantics | Phase C smoke builds all vendors before the rollout wave |
| `AggregatePart` silently breaks | PR-B2 step 11 covers `AggregatePart`/`AggregatePartClass`/`PartFactory` in the A2 fail-fast and the state-load path |

## Out of scope

- Data migration tooling (decision 2).
- `ProcessManager` state history wiring (Phase D designs for it only).
- Removing the deprecated `@Apply` annotation itself and the other
  deprecations — happens at v2.0.0 final, tracked separately.
