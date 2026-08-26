# Task: Event history for Process Managers (Phase G)

## Origin

Phase G of [de-event-sourcing-plan.md](de-event-sourcing-plan.md): Process
Managers gain the opt-in event journal with full business-API parity, and the
aggregate journal machinery moves up so one implementation serves both entity
types. Direction from the product owner (2026-07-22):

1. **`SignalDispatchingRepository` manages `SignalDispatchingEntity`** — the
   `E` parameter re-binds from `AbstractEntity<I, S>` to
   `SignalDispatchingEntity<I, S, ?>`. This is what lets the shared repository
   call `setEventHistoryLoader(...)` / `enableDoubleDispatchGuard(...)`
   generically.
2. **`ProcessManager` descends from `SignalDispatchingEntity`** (was:
   `AssigneeEntity`).
3. **The double-dispatch guard becomes a feature of `ProcessManager` and
   `ProcessManagerRepository`.** Both entity kinds receive client-side signals
   subject to accidental re-submission — a weakly connected client app re-sends
   a command the server did not acknowledge.

The entity layer has been ready since PR #1664: `SignalDispatchingEntity`
carries `RecentEventHistory`, `UncommittedEventHistory`, `DoubleDispatchGuard`,
`recordEvents`, and the depth-explicit `eventHistoryBackward` /
`eventHistoryContains`; `Aggregate` already extends it.

## Decisions

- **Guard-on + journal-off fails fast at context registration** (product
  owner, 2026-07-22): the guard requires the journal that feeds it; requesting
  the guard without the journal is a configuration error raised by
  `SignalDispatchingRepository.registerWith`. The knobs stay orthogonal and
  explicit — no implicit enabling (Phase E/F "fail fast on a disabled
  recorder" philosophy).
- **PM journal is opt-in, off by default** (locked, 2026-07-16); the
  aggregate journal stays unconditional. The posture is expressed by the
  `abstract SignalDispatchingRepository.eventHistoryEnabled()` — each concrete
  repository kind states its own (renamed from `eventJournalEnabled` and made
  abstract on owner review, 2026-07-22: "history" is the term in API names,
  "journal" stays a documentation word; a default implementation confused the
  design). `AggregateRepository` returns `true` (final); `ProcessManagerRepository`
  returns a flag raised by `recordEventHistory()` (name parallels the
  state-history `recordStateHistory()`).
- **Fail-fast reads via the installed loader** — the state-history pattern of
  `AbstractEntityRepository.setUpStateHistoryReading`: the loader is installed
  unconditionally; when journaling is off, reading through it throws with
  configuration guidance. A bare instance (no loader) keeps reading empty.
- **Deviation from the plan text:** Phase G item 1 said `eventHistoryDepth`
  "stays put" in `AggregateRepository` — written when the guard was expected
  to remain aggregate-only. With the guard shared, the depth is its shared
  scan window and moves to `SignalDispatchingRepository`.
- Rejections and emitted commands are never journaled (parity with
  aggregates; `UncommittedEventHistory.record()` also filters rejections).
- No `IdempotencyGuard` revival; delivery still owns primary dedup (A5/D5).

## What moves — repository side

From `AggregateRepository` to `SignalDispatchingRepository` (Java; edited
code stays Java):

| Member                                                 | Today (`AggregateRepository.java`) | Delta while moving                                                |
|--------------------------------------------------------|------------------------------------|-------------------------------------------------------------------|
| `eventHistoryDepth` field + accessor + setter          | `:111`, `:343`, `:353`             | none                                                              |
| `eventStorage` field + lazy `eventStorage()`           | `:118`, `:392`                     | Javadoc de-aggregatized                                           |
| `createEventStorage()` seam                            | `:373`                             | Javadoc de-aggregatized                                           |
| `setUpHistoryReading(A, I)`                            | `:203`                             | renamed `setUpEventHistoryReading`; loader gains fail-fast gate   |
| `create(id)` / `toEntity(record)` history-wiring calls | `:185`, `:438`                     | become base-class overrides (`@OverridingMethodsMustInvokeSuper`) |
| `doStore(A)` journal write + `commitEvents()`          | `:235-250`                         | untouched-skip precheck STAYS in `AggregateRepository`            |
| `store(Collection)` per-entity override                | `:261-263`                         | none (bulk `RecordBasedRepository.store` bypasses `doStore`)      |
| `close()` override + `closeEventStorage()`             | `:462-489`                         | none (`Repository.attemptClose` is `protected static`)            |

## What changes — PM side

- `ProcessManager`: re-parent; `dispatchEvent` package-private → `protected
  @Override` (the Kotlin base declares it `protected abstract`; `protected`
  keeps same-package access for `PmTransaction`); guard checks in
  `dispatchCommand` / `dispatchEvent` via a new shared
  `SignalDispatchingEntity` helper also adopted by `Aggregate`.
- `ProcessManagerRepository`: `recordEventHistory()` +
  `eventHistoryEnabled()` override; settings Javadoc.
- `PmEndpoint.runTransactionFor`: after commit, `recordEvents(...)` when the
  repository journals — mirroring `AggregateEndpoint.runTransactionFor`;
  `isModified` becomes `changed() || hasUncommittedEvents()`.

## Tests (Kotlin, JUnit 5 + Kotest, `Spec` suffix)

Under `server/src/test/kotlin/io/spine/server/procman/`, mirroring
`server/src/test/java/io/spine/server/aggregate/DoubleDispatchGuardTest.java`:

- `PmDoubleDispatchGuardSpec` — duplicate command/event rejected; outside-window
  and unrelated signals pass; guard-off duplicates dispatch normally; dedup
  holds against a reloaded instance (journal-backed).
- `PmEventJournalSpec` — journaling-on writes per successful dispatch; off
  writes nothing; rejections and commander output not journaled; `close()`
  closes the journal.
- `PmEventHistorySpec` — depth window honored; journaling-off managed instance
  fails fast; bare instance reads empty.
- Registration validation — guard-on + journal-off `registerWith` fails;
  guard-on + journal-on registers.
- Aggregate suites stay green — the hoist is behavior-preserving for
  aggregates.

## Acceptance

- `./gradlew build` and `./gradlew dokkaGenerate` pass.
- Version bumped once on the branch (`version-bumped` guard) at pre-PR time.
- Phase G items 1–3 of the plan marked done; item 4 (truncate maintenance)
  satisfied by the `eventStorage()` move; item 5 (non-goals) unchanged.

## Status

- 2026-07-22 — task opened; implementation in progress on
  `more-on-signal-dispatching-entities`.
- 2026-07-22 — implemented and verified: full `./gradlew build` green,
  `dokkaGenerate` green, 18 new PM spec tests + full aggregate/procman/entity
  regression pass. Version bumped to `2.0.0-SNAPSHOT.511`. Reviewed by
  `spine-code-review` and `kotlin-engineer` (both APPROVE WITH CHANGES;
  all findings applied — Kotlin-idiomatic `detectDuplicate`, proto-DSL
  `duplicateOutcome`, dead test field removed, stale `resource`
  suppression dropped). Empirical note for testers: the delivery layer
  drops a re-posted identical command before it reaches the entity, so
  guard specs assert journal-backed detection on a freshly loaded
  instance rather than double-posting end-to-end.
