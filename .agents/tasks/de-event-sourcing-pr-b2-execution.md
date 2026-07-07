# PR-B2 — Aggregate event-sourcing cutover (execution plan)

## Context

Spine `Aggregate`s currently load by **replaying their event journal**. That is
fragile: `NONE`-visibility aggregates persist no state and can become
unloadable, a half-applied dispatch can leave partial validity, and the journal
is un-retirable. **PR-B2** is the atomic cutover that makes the **latest
persisted `EntityRecord` the source of truth**: aggregates load from state (no
replay), handlers mutate `builder()` inside a transaction opened *before* the
handler runs (exactly the `ProcessManager` model), the version advances **+1 per
command** (not per event), and the event journal becomes a pure append-only
traceability log. Event import is removed; `@Apply` becomes a fail-fast
`ModelError`.

Design is **already accepted** in `docs/adr/0001-aggregates-without-event-sourcing.md`
(D1–D10) and detailed in `.agents/tasks/de-event-sourcing-plan.md` (PR-B2, steps
1–15). This plan is the **execution strategy** grounded in the current tree,
which has moved since the plan was written (`Transaction`/`TransactionalEntity`
are now Kotlin — #1644/#1645).

Prep already landed on `de-event-source-PR-B2` (this session): version bumped to
`2.0.0-SNAPSHOT.420` (breaking), `Validation` → `.449`, reports regenerated,
plan marked.

## The two load-critical invariants (must not get these wrong)

1. **State is stored unconditionally.** Today `AggregateRecords.newStateRecord`
   packs state only when `includeState` is true, and the caller passes
   `queryingEnabled` (true only for non-`NONE` visibility). Decouple: always
   pack business state for loading; keep `queryingEnabled` gating only the
   read-side query exposure (`readStates`).
2. **Version advances +1 per command, explicitly.** Replay currently advances
   the version as a side effect of playing events. Remove replay and reuse the
   PM path: one `CommandDispatchingPhase` + `VersionIncrement.sequentially`.
   `EventEmitter` already stamps all events of a dispatch with the one
   pre-dispatch version — so removing appliers + `VersionSequence` +
   `AggregateEndpoint.correctProducedEvents` yields PM-identical versioning with
   **no new stamping machinery**.

## Atomicity — why this can't land green in pieces

The runtime rewrite and the fixture migration are **one commit**. The instant
`@Apply` becomes a `ModelError` (D2), every `@Apply` fixture fails model-building,
so all ~64 fixture files must already be migrated in the same commit. Removing
`invokeApplier`/`play`/`replay`/`apply` breaks the endpoint, transaction, and
repository simultaneously. The branch is **un-buildable from the first edit until
the whole change lands** — this is expected (plan: "PR-B2 does NOT decompose into
green sub-commits").

## Recommended rollout

Because the branch is red from the first edit until the whole change lands, I'll
implement in phase order and **pause once for a design review at the
runtime/design boundary** — before the mechanical 64-file fixture grind, where
course-correction is cheapest:

1. **Phases 1–3** — runtime dispatch/tx/version + load/save/storage + `@Apply`
   `ModelError` & import removal (the design-critical core).
   **→ Checkpoint here for review** (build is intentionally still red; I'll share
   the rewritten dispatch/tx/version + load/save diff).
2. **Phase 4** — migrate the 64 fixtures + test suites → first green
   `./gradlew clean build` → **single atomic commit** (bump already on branch).

Alternative if preferred: one continuous push through all four phases to green,
no mid-review. My recommendation is the checkpoint — a wrong turn in the runtime
core would otherwise be replicated across 64 fixtures before anything compiles.

## Execution sequence

Grounded in ADR public-API sketch (`docs/adr/0001…md:670-836`) and plan steps.

### Phase 1 — Runtime dispatch / transaction / version (plan steps 1–4)
- `AggregateTransaction` (`…/aggregate/AggregateTransaction.java`): re-base from
  `EventPlayingTransaction` onto the Kotlin `entity/Transaction.kt` (as
  `PmTransaction` does); drop event-play dispatch; use a single per-dispatch
  `VersionIncrement.sequentially` (+1), not per-event `fromEvent`.
- `AggregateEndpoint` (`…/aggregate/AggregateEndpoint.java`): open the tx
  *before* `invokeDispatcher()`, run one `CommandDispatchingPhase`, validate,
  commit — mirror `PmEndpoint.runTransactionFor()` / `PmTransaction.perform`.
  **Delete `correctProducedEvents`** (per-event version back-fill).
- `Aggregate` (`…/aggregate/Aggregate.java`): drop `implements EventPlayer`;
  remove `invokeApplier`, `play`, `replay`, `apply`, `ApplierWatcher`; relax
  `ensureAccessToState()` (D8). Add `historyBackward(int)` /
  `historyContains(int, Predicate)`; deprecate the parameterless forms
  (delegate with `depth = historyDepth()`).
- `AggregateEventReactionEndpoint`: same tx pattern; `@React` emission optional
  (D4); migrate applier-set lifecycle flags into the handler body.
- Delete internal `VersionSequence`.

### Phase 2 — Load / save / storage (plan steps 5–9)
- `AggregateStorage`: add `Optional<EntityRecord> readState(I)` bypassing the
  `ensureStatesQueryable()` gate; make `newStateRecord` **always** pack state
  (drop `includeState`, stop passing `queryingEnabled`); `writeAll` stores the
  state record unconditionally. Deprecate `read(id, batchSize)`, `writeSnapshot`,
  `truncateOlderThan`.
- `AggregateRepository`: `load(id)` = `readState` + restore (state, version,
  lifecycle flags — reuse the `restore(Snapshot)` shape but from `EntityRecord`);
  **lazy** journal-tail history (not eager `RecentHistory`); `restore(...)` no
  longer calls `onCorruptedState`. Add `historyDepth()`/`setHistoryDepth(int)`
  (default 100), `useIdempotencyGuard()`/`idempotencyGuardEnabled()` (default
  false). Keep `extends Repository` (minimal-diff).
- **Expand the store gate** (`AggregateEndpoint.storeAndPost`): store on
  `withEvents || lifecycleFlags changed || state changed` (D4) — else a
  zero-event, state-only reactor is silently dropped.
- `UncommittedHistory`: collapse to a plain uncommitted-events list (no snapshot
  segmentation). Deprecate `Aggregate.toSnapshot()`.
- `IdempotencyGuard`: opt-in/off-by-default; when on, reads the lazy journal
  tail (must preserve `EventContext.pastMessage`; verify `clearEnrichments`).

### Phase 3 — `@Apply` fail-fast + event-import removal (plan steps 11–12)
- `@Apply`: `@Deprecated` + migration Javadoc. `AggregateClass` **and
  `AggregatePartClass`** raise a `ModelError` when appliers are detected.
- **Remove** event import: `ImportBus`, `EventImportEndpoint`,
  `EventImportDispatcher`, `UnsupportedImportEventException`,
  `AggregateRepository.eventImportRouting`/`setupImportRouting`,
  `AggregateClass.importableEvents()`/`importsEvents()`, `BlackBox.importsEvent`.
- **Deprecate for wire compat** (keep): `InboxLabel.IMPORT_EVENT`, `EventImported`
  + `onEventImported`, `AggregateHistoryCorrupted` + `onCorruptedState`, proto
  `Snapshot`/`AggregateHistory`, snapshot config (`setSnapshotTrigger`).
- Remove dead `DiagnosticLog.on(AggregateHistoryCorrupted)` subscriber
  (server-testlib).

### Phase 4 — Fixture + test migration (plan steps 13–15) — SAME COMMIT
- Migrate every `server/src/testFixtures` aggregate/part declaring `@Apply`
  (~64 files / 127 methods): move each applier body into the corresponding
  `@Assign`/`@React` handler; move lifecycle-flag flips into the emitting
  handler. `allowImport = true` fixtures convert to command/reaction dispatch.
  Java fixtures stay Java.
- `server/src/test`: `ApplierTest` → the D2 model-error test; `AggregateTest`
  replay cases → load-from-state cases. New suites in Kotlin.
- `server-testlib`: migrate `BbProjectAggregate`, `BbReportAggregate`; remove
  `importsEvent`; resolve `DiagnosticLog`.

*(Exact current-code anchors and the full fixture inventory: filled from the
Explore agents — see "Current-code map" below.)*

## New tests (Kotlin, JUnit5 + Kotest, `Spec` suffix, backticked names)
- Version advances **exactly +1 per command** regardless of event count.
- Load a **`NONE`-visibility** aggregate from its state record.
- **Zero-event, state-only `@React`** persists (store-gate expansion).
- Guard-on dedup works purely from the **journal tail, no snapshot**
  (`pastMessage` intact); guard-off dispatch does **no** journal read.
- `@Apply` present → `ModelError` at model-building (incl. `AggregatePart`).
- Rollback on rejection/invalid state discards builder mutations.

## Must-not-regress (ADR)
`StorageFactory` SPI shape; client query path (`QueryService` → `Stand` →
`findRecords` → `readStates`); `BlackBox`/`EventSubject`/`CommandSubject` (minus
`importsEvent`); shared `EventPlayer`/`EventPlayingTransaction` (kept for
`Projection` — only `Aggregate`'s implementation is dropped).

## Verification
- `JAVA_HOME` → Corretto 17. Proto changes participate (import/snapshot protos
  deprecated) → **`./gradlew clean build`**.
- `./gradlew dokkaGenerate` (doc-bearing `.kt`/`.java` changes).
- Reviewers via `pre-pr`: `spine-code-review`, `kotlin-engineer`, `review-docs`.
- Single atomic commit when green.

## Checkpoint OUTCOME (approved 2026-07-07)
User chose **"Proceed as designed."** Confirmed: (1) emitted events carry the
**pre-dispatch version V** (ADR D3, subtractive — delete `VersionSequence` +
`correctProducedEvents`); (2) event-recording seam = **endpoint records produced
events post-commit** into `uncommittedHistory` (only on success). Full coupled
runtime block → compile `:server` main → then 64-fixture migration → green →
atomic commit.

## Proposed checkpoint
Given the 64-file mechanical grind, **pause after Phase 1–3 (runtime core) for a
design review** of the rewritten dispatch/tx/version + load/save path before
investing in the fixture migration — even though the build is not yet green.
Catch any approach issue before it multiplies across 64 fixtures.

## Implementation status (session 2026-07-07) — RESUMABLE

Branch `de-event-source-PR-B2`, 4 commits ahead of `master` (bump `.420`,
Validation `.449`, reports, plan-marking). PR-B2 runtime edits are **uncommitted
WIP** in the working tree — the atomic commit lands only when green.

### ✅ DONE — dispatch/tx/version/event-recording/store core (6 files)
- **`AggregateTransaction.java`** — re-based onto Kotlin `Transaction` (off
  `EventPlayingTransaction`); `perform(DispatchCommand)` + `dispatchEvent(EventEnvelope)`
  via `CommandDispatchingPhase`/`EventDispatchingPhase` + `VersionIncrement.sequentially`.
- **`AggregateCommandEndpoint` / `AggregateEventReactionEndpoint`** — dispatch via
  `aggregate.tx().perform(...)` / `.dispatchEvent(...)`.
- **`AggregateEndpoint.java`** — `runTransactionFor` (tx opened before dispatch);
  `storeAndPost` records produced events post-commit + gate `withEvents || changed()`;
  `isModified = changed() || hasUncommittedEvents()`; removed
  `handleAndApplyEvents`/`applyProducedEvents`/`correctProducedEvents`/`firstErroneousOutcome`.
- **`Aggregate.java`** — dropped `implements EventPlayer`, `invokeApplier`, `play`,
  `apply`, `replay`, `ApplierWatcher`, `ensureAccessToState`; added `tx()` (package-
  visible), `recordEvents(List<Event>)`, `restore(EntityRecord)`, `historyBackward(int)`/
  `historyContains(int, Predicate)`; deprecated parameterless forms + `toSnapshot`.
- **`UncommittedHistory.java`** — collapsed to a plain event list (`record`/`get`/
  `events`/`hasEvents`/`commit`); keeps the single-segment `AggregateHistory` `get()`
  shape so `AggregateStorage.writeAll` is unaffected.

### ✅ MILESTONE (2026-07-07): `:server:compileJava` is GREEN
The entire runtime cutover compiles. Committed WIP: `ac906c41a2` (dispatch core),
`6f59025cde` (load-from-state + import removal), `7645ffd354` (guard opt-in +
config). Steps 1–7 below are **DONE**; only Phase 4 (fixtures/tests) + a few
refinements remain.

### ✅ DONE since the milestone
- Deleted `VersionSequence` + `ApplierWatcher`. Storage decouple
  (`newStateRecord` unconditional, `readState`). Repo load-from-state. `@Apply`
  `ModelError` + deprecation. Event-import removal (files + `AggregateRepository`
  + `BoundedContext`). `IdempotencyGuard` opt-in/off-by-default. Repo config
  (`historyDepth`/`setHistoryDepth`/`useIdempotencyGuard`/`idempotencyGuardEnabled`;
  `snapshotTrigger` deprecated).

### ⏳ REMAINING
**Refinements (non-blocking, main compiles):**
- Lazy journal-tail read for the opt-in guard: today it reads in-memory
  `recentHistory`, empty after a state-only load — wire
  `AggregateStorage.historyBackward`/`HistoryBackwardOperation` so an enabled
  guard actually dedups. (Guard is OFF by default, so default behavior is correct.)
- Suppress `@Apply`-deprecation warnings in `EventApplierSignature:54` &
  `AllowImportAttribute:81,85` (legitimate detection use → `@SuppressWarnings("deprecation")`).
- Deprecate `AggregateStorage.read(I,int)`/`read(I)`/`writeSnapshot`/`truncateOlderThan`.
- Deprecate protos for wire compat: `InboxLabel.IMPORT_EVENT` (`inbox.proto`),
  `EventImported` (`entity_log_events.proto`) + `EntityLifecycle.onEventImported`,
  `AggregateHistoryCorrupted` (`diagnostic_events.proto`) + `onCorruptedState`;
  `Snapshot`/`AggregateHistory` (`aggregate.proto`). Remove
  `DiagnosticLog.on(AggregateHistoryCorrupted)` (server-testlib).

**Phase 4 — fixtures/tests → `./gradlew clean build` green → squash/keep WIP commits:**
- Migration recipe (per fixture): delete each `@Apply` method; move its body into
  the `@Assign`/`@React` receptor that emits that event, mutating state via
  `builder()`; move any `setArchived/setDeleted` flip into that receptor (D4).
  `allowImport=true` fixtures → convert the imported event to a command/reaction.
- 64 testFixtures / 127 methods (full list in Agent C's inventory below), + 2
  `server/src/test` (`ApplierTest`→becomes the ModelError test; `AggregateTest`
  replay→load-from-state), + 2 `server-testlib` (`BbProjectAggregate`,
  `BbReportAggregate`).
- Test-side deletions/updates: `EventImportTest.java`, `ApplyAllowImportTest.java`
  (obsolete), `BlackBox.importsEvent`/`importsEvents` + `BlackBoxSetup.importBus` +
  `NastyClient.importsEvent` (server-testlib), and `BoundedContextSpec.kt:146`
  `fun ImportBus()` (references removed `importBus()`).

### (historical) coupled removal — now landed:
1. **Delete `VersionSequence.java`** (dead — only caller `Aggregate.apply` gone) and
   **`ApplierWatcher.java`** (field removed). Verify `VersionIncrement.fromEvent`/
   `IncrementFromEvent` has no other caller, then remove.
2. **Storage decouple (invariant #1):** `AggregateRecords.newStateRecord` — drop
   `boolean includeState`, always `setState`; caller `AggregateStorage.writeState:388`
   stop passing `queryingEnabled`. Add `AggregateStorage.readState(I): Optional<EntityRecord>`
   (bypass `ensureStatesQueryable`). Deprecate `read(id,batchSize)`/`writeSnapshot`/
   `truncateOlderThan`.
3. **`AggregateRepository` load-from-state:** `load(id)` = `storage.readState(id)` +
   `restore(EntityRecord)` (replaces `loadHistory`+`replay`+`restore(AggregateHistory)`);
   drop `onCorruptedState`; decouple `configureQuerying`/`exposedToQuerying` from state
   write; add `historyDepth()`/`setHistoryDepth(int)`(=100)/`useIdempotencyGuard()`/
   `idempotencyGuardEnabled()`; wire lazy journal-tail history (`historyBackward(depth)`
   via `HistoryBackwardOperation`) for the guard + business access.
4. **`IdempotencyGuard`** — opt-in/off-by-default (`enabled` flag); `check()` no-op when
   off; when on, read via `historyContains(historyDepth, …)` from the lazy journal tail
   (`Aggregate.dispatchCommand:267`/`reactOn:293` call sites).
5. **`@Apply` ModelError:** `AggregateClass.java:62` — throw `ModelError` after
   `ReceptorMap.create(..., new EventApplierSignature())` if `stateEvents` non-empty
   (covers parts via `super`). Remove `importableEvents` field/methods; `outgoingEvents`
   no longer unions them. `@Apply` → `@Deprecated` + migration Javadoc.
6. **Import removal:** delete `ImportBus`/`EventImportEndpoint`/`EventImportDispatcher`/
   `UnsupportedImportEventException`/`ImportValidator`/`AllowImportAttribute`; strip call
   sites in `AggregateRepository` (`eventImportRouting`/`setupImportRouting`/`importEvent`/
   `initInbox:214-215`), `BoundedContext` (`importBus`), `Inbox` (importer path),
   `BlackBoxSetup`/`BlackBox.importsEvent`. Deprecate-keep protos
   (`InboxLabel.IMPORT_EVENT`, `EventImported`@`entity_log_events.proto:132`,
   `AggregateHistoryCorrupted`) + emitters; remove `DiagnosticLog.on(AggregateHistoryCorrupted)`.
7. **Compile `:server` main** → iterate to green (main only).
8. **Phase 4:** migrate 64 testFixtures / 127 `@Apply` + `ApplierTest`→ModelError test +
   `AggregateTest` replay→load-from-state + 2 `server-testlib` fixtures + new Kotlin Spec
   suites → `./gradlew clean build` green → **atomic commit**.

### Design decisions locked (approved this session)
- Emitted events carry **pre-dispatch version V** (ADR D3; subtractive).
- Event-recording seam = **endpoint, post-commit** (`AggregateEndpoint.storeAndPost`).
- Store gate = `withEvents || aggregate.changed()`; `isModified` widened accordingly.
- `historyDepth` default 100 lives (for now) as `Aggregate.DEFAULT_HISTORY_DEPTH`; move to
  repo-configurable `historyDepth()` in step 3.

## Current-code map (from Explore agents)

### Load / save / storage (verified)
- **`AggregateRepository extends Repository`** (`AggregateRepository.java:100-105`),
  NOT `RecordBasedRepository`. Keep as-is.
- **State gate to decouple:** `AggregateStorage.writeState:388` passes
  `queryingEnabled` → `AggregateRecords.newStateRecord:140` `if (includeState)`.
  `queryingEnabled` field `:146`, setter `enableStateQuerying:192-194`, readers
  `writeState:388` + `ensureStatesQueryable:373-381`. Visibility coupling:
  `AggregateRepository.configureQuerying:557-561` / `exposedToQuerying:571-575`
  (→ `visibility().isNotNone()`), called from `registerWith:175`.
  **Plan:** always pack state in `newStateRecord`; keep `queryingEnabled` gating
  only `readStates`/`ensureStatesQueryable` (read side).
- **Two backing stores:** `eventStorage` (journal+snapshots), `stateStorage`
  (`EntityRecordStorage`, latest record) — `AggregateStorage.java:136,141`.
  **No per-id single-record load exists today**; `readState(id)` is new and must
  bypass `ensureStatesQueryable`.
- **Load chain (replay today):** `loadOrCreate:597` → `load:627-631` →
  `loadHistory:647-652` (`read(id, snapshotTrigger+1)`) → `restore:664-682`
  (`replay` + `onCorruptedState` on failure). Replace with `readState` + restore.
- **Restore shape to reuse:** `Aggregate.restore(Snapshot):416-425` restores
  `{state, version, archived, deleted}` — 1:1 with `EntityRecord`
  (`newStateRecord` writes id/flags/version/state, `AggregateRecords.java:136-144`).
  New `restore(EntityRecord)` maps directly; drop the `onCorruptedState` call.
- **Guard:** `IdempotencyGuard.java:119-160` matches `pastMessage` origin id;
  reads `Aggregate.recentHistory()` (in-memory, eager via
  `Aggregate.replay:353-355`). `writeEvent:287 clearEnrichments` **keeps
  `pastMessage`** (verified `EventMixin.clearEnrichments`). Lazy seam:
  `AggregateStorage.historyBackward:429-434` / `HistoryBackwardOperation:70-80`.
- **Snapshot surfaces to deprecate:** `UncommittedHistory` segmentation
  (`:124-145`), `Aggregate.toSnapshot:479-501`, `AggregateStorage.writeSnapshot:300-305`,
  snapshot trigger (`AggregateRepository.java:108,131,531-533,548-551`),
  `truncateOlderThan:449-490` / `TruncateOperation:80-98`.

### Dispatch / transaction / version (verified)
- **PM pattern to mirror:** `PmEndpoint.runTransactionFor:100` opens the tx
  (`beginTransactionFor:362`) *around* `invokeDispatcher`; `PmCommandEndpoint.invokeDispatcher:64`
  runs `pm.tx().perform(dispatch)`; `PmTransaction extends Transaction` (not
  EventPlaying) `:55`, `perform:78` uses `CommandDispatchingPhase` +
  `createVersionIncrement:110 = VersionIncrement.sequentially(this)` (+1 once);
  `dispatchEvent:92` for `@React`. `ProcessManager.tx():197` is package-exposed.
- **Aggregate command path today (2-pass, no tx during dispatch):**
  `AggregateEndpoint.performDispatch:68` → `handleAndApplyEvents:114`
  (`invokeDispatcher` — no tx) → `applyProducedEvents:133` (opens tx `:137`,
  `apply:139`, commit `:141`, `correctProducedEvents:142→:160`). Receptor returns
  events but mutates nothing; `@Apply` replay rebuilds state.
- **Version today (per-event):** `Aggregate.apply:387` → `VersionSequence(V):388`
  re-stamps events V+1..V+N; `AggregateTransaction.createVersionIncrement:91 =
  fromEvent`; `correctProducedEvents:160` copies V+1..V+N back onto outgoing
  events. Aggregate ends at V+N.
- **Emitted-event version source:** `EventEmitter.toSuccessfulOutcome:64` stamps
  every event with `target.version()` (`:68`) — pre-increment V. PMs rely on
  exactly this; aggregates only diverge via `VersionSequence`+`correctProducedEvents`.
- **Rewrite = mostly subtractive.** Change `AggregateCommandEndpoint.invokeDispatcher:50`
  to run through `aggregate.tx().perform(...)`; open the tx before dispatch in the
  endpoint (move `startTransaction:199` earlier); add `Aggregate.tx()` (mirror
  `ProcessManager.tx()`); re-base `AggregateTransaction` (`:47`) onto Kotlin
  `Transaction.kt` with `perform`+`dispatchEvent` (copy `PmTransaction`); replace
  `createVersionIncrement:91` with `sequentially`.
  **Delete:** `Aggregate implements EventPlayer:141`, `invokeApplier:315`,
  `play:321`, `apply:387`, `ApplierWatcher:154`, `ensureAccessToState:245`,
  `VersionSequence` (whole file), `AggregateEndpoint.correctProducedEvents:160`,
  `handleAndApplyEvents:114`/`applyProducedEvents:133` (2nd pass), and
  `VersionIncrement.fromEvent:61`+`IncrementFromEvent` (verify no other caller).
- **Store gate:** already `withEvents || flags changed` at `AggregateEndpoint.storeAndPost:87-88`;
  expand to `|| state changed` (D4). `isModified:213` uses `hasUncommittedEvents()`
  → switch to a `changed()`-style check (cf. `PmEndpoint.isModified:55`) so a
  zero-event, state-only reactor still stores.
- **Applier-needed caveat is DISSOLVED:** import dropped + load-from-state ⇒ no
  applier anywhere in `Aggregate`. `EventPlayingTransaction`/`EventPlayer`/
  `TransactionalEventPlayer` stay in the tree for `Projection` only.
- **`@Assign` must still emit:** keep `AggregateCommandEndpoint.onEmptyResult:69`
  throwing (D4); `@React` `AggregateEventReactionEndpoint.onEmptyResult:64` stays
  a no-op.
### `@Apply` machinery, event import, fixtures (verified)
- **`ModelError` choke point (one line covers all):** `AggregateClass` ctor
  `model/AggregateClass.java:60-65`, immediately after `:62`
  (`ReceptorMap.create(cls, new EventApplierSignature())`) — throw if `stateEvents`
  non-empty. `ModelError` already imported (`:35`); `AggregatePartClass:58-60` calls
  `super(cls)` so parts are covered too. Dead after: `stateEvents`/`importableEvents`
  fields + `applierOf`/`importableEvents`/`importsEvents`/`outgoingEvents` union.
- **`@Apply` detection retained to *detect*:** `Apply.java` (annotation, `allowImport:67`),
  `EventApplierSignature.java`, `Applier.java` (kept only to feed the ModelError).
- **REMOVE whole files:** `ImportBus.java`, `EventImportEndpoint.java`,
  `EventImportDispatcher.java`, `UnsupportedImportEventException.java`,
  `ImportValidator.java`, `model/AllowImportAttribute.java`.
- **REMOVE call sites:** `AggregateRepository` (`eventImportRouting:120`, ctor
  `:140-142`, `registerWith:169-172`, `setupRouting:181`, `initInbox:214-215`,
  `importableEvents:419-420`, `setupImportRouting:305-308`, `importEvent:458-464`,
  `routeImport:466-469`, `idForImported:484-494`); `BoundedContext`
  (`importBus:107`, `buildImportBus:187-188`, `importBus():420-422`,
  `closeIfOpen:491`); `Inbox.java` importer send-path (prune endpoint, keep the
  label); `BlackBoxSetup` (`importBus:71,81,170`); `BlackBox.importsEvent:507+` /
  `importsEvents:527+`.
- **REMOVE import test suites:** `EventImportTest.java`, `ApplyAllowImportTest.java`.
- **DEPRECATE (keep for wire compat):** `InboxLabel.IMPORT_EVENT`
  (`inbox.proto:72`); `EventImported` (**`entity_log_events.proto:132`** — plan
  said diagnostic_events; corrected) + `EntityLifecycle.onEventImported:251-260`;
  `AggregateHistoryCorrupted` (`diagnostic_events.proto:146`) +
  `onCorruptedState:415-446`. Remove dead `DiagnosticLog.on(AggregateHistoryCorrupted):101-111`.
- **Fixture inventory (atomic-commit scope): 68 files / 141 methods.**
  - `server/src/testFixtures`: **64 files / 127** — 59 `Aggregate`, 4
    `AggregatePart` (`AggregateRootTestEnv.java`, `part/TaskCommentsPart.java`,
    `part/TaskPart.java`, `system/…/entity/PersonNamePart.java`), 1 neither.
  - `server/src/test`: `model/ApplierTest.java` (6 → becomes the D2 ModelError
    test), `model/FilterTest.java` (1); plus behavioral rewrites of
    `aggregate/AggregateTest.java` (replay→load-from-state).
  - `server-testlib`: `given/BbProjectAggregate.java` (5), `given/BbReportAggregate.java` (2).
  - **Import examples → convert to command/reaction dispatch (5 methods / 4 files):**
    `given/importado/Dot.java`, `given/klasse/EngineAggregate.java` (×2),
    `delivery/given/CalcAggregate.java`, `integration/given/DocumentAggregate.java`;
    plus `delivery/NastyClient.java` (calls `importsEvent`).
  - **⚠ Special case — not a mechanical migration:**
    `event/model/given/reactor/RcWrongAnnotation.java` is `@Apply` on a
    non-aggregate `TestEventReactor` (a model-validation fixture). Verify its suite
    expects the new behavior rather than migrating it like an aggregate.
  - Migration is mechanical *per file* but needs per-file judgment: move each
    applier body into the emitting `@Assign`/`@React`, and move any
    `setArchived/setDeleted` flip into that handler (D4).
