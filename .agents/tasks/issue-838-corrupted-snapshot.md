# Issue #838 — Corrupted aggregate snapshot under eventually-consistent history

GitHub: https://github.com/SpineEventEngine/core-jvm/issues/838 (label: bug)
Branch: `claude/trusting-bardeen-56899a`

## Problem

With an eventually-consistent store (e.g. Datastore), an event already durably
written to an aggregate's history can be temporarily **unavailable** during a
later backward history read. The load path then assembles an `AggregateHistory`
that is missing one or more events; every returned event applies cleanly, so
replay looks *successful*, and on the next command the snapshot trigger fires and
a snapshot built from **partial state** is persisted permanently — poisoning all
future loads.

The issue's own example is **tail-loss**: `TaskAssigned` (the newest of the two
stored events) is missing at load, so the aggregate loads at v1, then applies a
new event at v2 — colliding with the already-stored-but-invisible v2.

## Root cause (verified)

- `ReadOperation.checkRecord` (`ReadOperation.java:175`) only asserts
  "snapshot or non-empty events" — no completeness/gap check.
- `UncommittedHistory.onAggregateRestored` (`UncommittedHistory.java:196`) seeds
  `eventCountAfterLastSnapshot` from `history.getEventCount()` — just the size of
  what was read; the snapshot trigger (`:138`) then fires on partial state.
- The existing `AggregateHistoryCorrupted` diagnostic only fires when an applier
  **throws**; a silently-short history replays cleanly and is invisible to it.

## Chosen approach — reconcile against the stored state version

Every store calls `writeAll` → `writeState` (`AggregateStorage.java:398`), and
`newStateRecord` **always** sets the aggregate `version` (`AggregateRecords.java:139`),
keyed by aggregate id. On Datastore a **get-by-key is strongly consistent** while
the backward history read is a **query** (eventually consistent). So the stored
state version is an authoritative current-version oracle the load can trust.

`VersionSequence` (`VersionSequence.java:60`) assigns strictly contiguous +1
versions, so a single **count reconciliation** detects tail-, middle-, and
oldest-loss:

```
baseVersion   = history.hasSnapshot() ? snapshot.version.number : 0   // genesis base TBD (0 vs 1)
expectedCount = V_authoritative.number - baseVersion
actualCount   = history.getEventList().size()
INCOMPLETE if actualCount != expectedCount   (+ contiguity check for a precise diagnostic)
```

Edge cases:
- **No state record** (legacy pre-2.0 data / brand-new): `stateStorage.read(id)`
  empty → cannot reconcile → fall back to internal contiguity only, do not break
  the load (backward compat).
- **`read()` empty but V_auth > 0**: whole history temporarily invisible →
  treat as incomplete (prevents wrongly creating a fresh aggregate that then
  collides).

## Behavior on detection — FAIL-FAST (recommended default)

Throw a dedicated unchecked exception from `AggregateStorage.read`, propagating up
to fail the command dispatch. No aggregate is loaded → **no new events emitted and
no snapshot written**. The eventually-consistent read is expected to be retried by
the delivery layer once consistency settles.

Rationale over *suppress-snapshot-and-continue*: suppression still lets the handler
run on partial state, emitting real events with wrong context AND colliding
versions — arguably worse. Fail-fast is the only option that prevents both corrupt
snapshots and corrupt new events.

**Validation point:** confirm a load-time throw is treated as transient/retryable
by the delivery mechanism, not permanently dead-lettered. If it is not retried,
add bounded in-read retries (small backoff) before throwing.

## Diagnostic — dedicated exception now; system event deferred

`AggregateHistoryCorrupted`'s fields model an applier failure and do not fit
"history incomplete". Plan: throw a well-documented `IllegalStateException`
subtype. A new `AggregateHistoryIncomplete` system diagnostic event (proto +
`EntityLifecycle` wiring) is an optional follow-up for observability — deferred to
keep this change code-only (no proto → no `clean build` gate) unless requested.

## Files to change

Production:
- `server/src/main/java/io/spine/server/aggregate/AggregateStorage.java` —
  reconcile in `read(id, batchSize)` after `op.perform()`, using `stateStorage.read(id)`.
- `server/src/main/java/io/spine/server/aggregate/ReadOperation.java` — add a
  version-contiguity check (precise diagnostic; also the legacy-fallback guard).
- New `.../aggregate/IncompleteHistoryException.java` (or similarly named) —
  dedicated unchecked exception.
- (Optional/deferred) `diagnostic_events.proto` + `EntityLifecycle.java` — new
  `AggregateHistoryIncomplete` diagnostic.

Tests (Kotlin, JUnit5 + Kotest, `internal`, `Spec`-suffixed — per kotlin-jvm-tester):
- New `server/src/test/kotlin/io/spine/server/aggregate/` specs covering:
  tail-loss, middle gap, oldest-loss, whole-history-invisible, snapshot-boundary
  gap, happy path (no throw), legacy no-state-record (no throw). Simulate a short
  read by writing a full aggregate then removing one event record while keeping the
  state record (following the `AggregateRepositoryTest.throwWhenCorrupted` pattern).
- Repository/endpoint-level spec reproducing the issue scenario: a short read fails
  the dispatch and **no corrupted snapshot is written**.
- Regression: existing `ReadOperationTest`, `AggregateTest`, `AggregateRepositoryTest`
  stay green.

## Open items to resolve while coding
- Genesis base version (0 vs 1) for the no-snapshot full-history head check.
- Whether event **import** can legitimately create version gaps (would affect the
  strict-contiguity assertion; count reconciliation via V_auth is unaffected).
- Exact exception type/name and package conventions.

## Build / verification
- Code-only change → `./gradlew build` + `dokkaGenerate` (per running-builds.md);
  proto change (only if the optional diagnostic is added) → `clean build`.
- Version already bumped to `2.0.0-SNAPSHOT.384` on this branch (gate satisfied).
- Run reviewers (`kotlin-engineer`, `spine-code-review`) before PR.

## Delivery-layer finding (important)

Traced the dispatch path: `AbstractMessageEndpoint.dispatchTo` (`delivery/AbstractMessageEndpoint.java:68-77`)
**catches** any exception from `performDispatch` and converts it to an error
`DispatchOutcome`; `TargetDelivery` then calls `DeliveryMonitor.onReceptionFailure`,
whose **default returns `reception.markDelivered()`** — i.e. the message is marked
delivered and **dropped, not retried** (`DeliveryMonitor.java:118-121`).

Consequence: a fail-fast throw *prevents corruption* (the bug's subject) but, by
default, the triggering signal is dropped. A retry hook exists —
`FailedReception.repeatDispatching()` (`delivery/FailedReception.java:108-111`) via a
custom `DeliveryMonitor` — but it retries immediately (may not let consistency
settle) and is opt-in.

Decision: this change implements **detect + fail-fast prevention** (the corruption
fix) with a dedicated, catchable `IncompleteHistoryException`. It does **not** add
blocking in-read retries (they would stall the single-threaded shard and are flaky
to test). Proper "retry once consistency settles" belongs at the delivery layer
(delayed redelivery / a retrying `DeliveryMonitor`) and is a recommended follow-up.

## Implemented

- `server/src/main/java/io/spine/server/aggregate/IncompleteHistoryException.java` — new
  public `IllegalStateException` subtype; documents the transient/retryable nature.
- `server/src/main/java/io/spine/server/aggregate/HistoryCompleteness.java` — pure
  reconciliation: `expected = authoritativeVersion − baseVersion` (base = snapshot
  version, else 0, genesis=0); throws when `eventsRead < expected`; skips when the
  authoritative version is `null` (legacy) and accepts over-complete histories.
- `AggregateStorage.read(id, batchSize)` — reads the strongly-consistent state
  version via `stateStorage.read(id)` and calls `HistoryCompleteness.check`.
- Tests:
  - `server/src/test/kotlin/io/spine/server/aggregate/HistoryCompletenessSpec.kt` —
    unit coverage of the reconciliation (accept / skip / reject, incl. the #838
    tail-loss scenario). 10 cases, all green.
  - `AggregateRepositoryTest.throwWhenHistoryIncomplete` (+ `NewestEventDroppingStorage`
    helper) — end-to-end wiring test proving `AggregateStorage.read` fetches the
    authoritative state version and throws when the backward read drops the newest
    event. Guards against the check silently no-op'ing.

## Review outcomes

- **kotlin-engineer**: APPROVE. Platform-type note moot (aggregate package is
  `@NullMarked`). `@NonValidated` not required on `private` test fixtures.
- **spine-code-review**: APPROVE WITH CHANGES. Fixed: (1) two Kotlin lines > 100
  chars broke `:server:detekt`; (2) Java import ordering (`ImmutableList` before
  `ImmutableSet`). Confirmed: copyright headers 2026; version gate satisfied
  (branch already bumped to `2.0.0-SNAPSHOT.384`); **no public-API baseline exists**
  (no `.api`/apiCheck), so the new public `IncompleteHistoryException` needs none.
  Both reviewers verified the reconciliation arithmetic against the write-order
  (`writeAll` writes events then state) and `ReadOperation`'s snapshot-stop semantics.

## Status: IMPLEMENTED & REVIEWED — final full `build` in progress; #838 changes
left UNCOMMITTED for the user. Retry-layer (delivery redelivery / retrying
`DeliveryMonitor`) recommended as a follow-up issue.
