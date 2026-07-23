# Reducing the `public` `@Internal` API of the `server` module

Parent plan: [de-event-sourcing-plan.md](de-event-sourcing-plan.md).

The entity-hierarchy rework of the de-event-sourcing train introduced a number
of `public` members annotated `@Internal`: Java has no counterpart of Kotlin's
`internal` visibility, and Java framework collaborators could not otherwise
reach the implementation members they need. The goal of this task is to migrate
the involved Java code to Kotlin so that such members can use the `internal`
access level within the `server` module, shrinking the exposed API.

## Step 1 — merge `AssigneeEntity` into `SignalDispatchingEntity` ✅

Done (2026-07-23, this branch):

- `io.spine.server.command.AssigneeEntity` (Java) merged into
  `io.spine.server.entity.SignalDispatchingEntity` (Kotlin): the cached
  `producerId()`, the abstract `dispatchCommand(CommandEnvelope)`, and the
  `expectedXxx`/`unexpectedXxx` `ValueMismatch` helpers. The class now extends
  `TransactionalEntity` directly and implements `Assignee`.
- The mismatch-helper family is documented as the "Reporting value mismatches"
  section of the class-level KDoc: the idea (reporting a discrepancy between
  the state expected by a command and the actual one, stamped with the entity
  version) and why the methods are `protected` (they serve the receptor bodies
  of subclasses and are useless outside the entity's own handling code).
- `DispatchCommand` moved (`git mv`) from `io.spine.server.command` to
  `io.spine.server.entity`. It calls the `protected dispatchCommand` via
  Java same-package access; that only keeps working if it lives in the package
  of the (now Kotlin) declaring class. The alternative — widening
  `dispatchCommand` to `public @Internal` — would contradict this task's goal.
- `EventDispatch` moved (`git mv`) from `io.spine.server.event` to
  `io.spine.server.entity` for consistency with `DispatchCommand`: both are
  the dispatch operations wrapped by the entity-package `Phase` classes.
- `AssigneeEntityTest` + `AssigneeEntityTestEnv` replaced by the Kotlin
  `SignalDispatchingEntitySpec` (same-package spec with a private fixture
  re-exposing the `protected` helpers).

Not touched on purpose: `io.spine.server.entity.model.AssigneeEntityClass`
(the model class keeps its name and hierarchy; rename is a possible follow-up
once the `@Internal` analysis says what happens to the model layer).

## Step 2 — shrink the `@Internal` surface

### Inventory (2026-07-23)

The `public` `@Internal` members introduced on the entity classes by the
de-event-sourcing train, with every caller found in the codebase. Decisive
finding: **all callers live inside the `server` module** — `server-testlib`
and `testutil-server` use none of these members or their supporting types —
so Kotlin `internal` is reachable for the whole set.

| Member | Production callers (all Java) | Test callers |
|---|---|---|
| `SignalDispatchingEntity.setEventHistoryLoader` | `SignalDispatchingRepository` | — |
| `SignalDispatchingEntity.recordEvents` | `AggregateEndpoint`, `PmEndpoint` | — |
| `SignalDispatchingEntity.getUncommittedEvents` | — | `AggregateTest` (Java) |
| `SignalDispatchingEntity.hasUncommittedEvents` | `AggregateRepository`, `AggregateEndpoint`, `PmEndpoint` | — |
| `SignalDispatchingEntity.uncommittedEventHistory` | `SignalDispatchingRepository` | — |
| `SignalDispatchingEntity.commitEvents` | `SignalDispatchingRepository` | `AggregateTest` (Java) |
| `SignalDispatchingEntity.enableDoubleDispatchGuard` | `SignalDispatchingRepository` | — |
| `AbstractEntity.setStateHistoryLoader` | `AbstractEntityRepository` | — |
| `AbstractEntity.appendToStateHistory` | `AbstractEntityRepository` | — |
| `TransactionalEntity.changed` | `AggregateRepository`, `AggregateEndpoint`, `PmEndpoint` | `ProjectionTest` (Java), `TransactionalEntitySpec` (Kotlin) |
| `RecentHistory.append` (both overloads) | `UncommittedEventHistory` (Java), `AbstractEntity.kt`, `SignalDispatchingEntity.kt` | — |

Supporting `public` `@Internal` types with only in-module usage:
`EventHistoryLoader`, `StateHistoryLoader`, `HistoryLoader` (Kotlin);
`UncommittedEventHistory`, `UncommittedEvents` (Java). `DoubleDispatchGuard`
stays public: the `protected doubleDispatchGuard()` accessor exposes it to
external subclasses.

### Mechanics and traps (from `java-to-kotlin-visibility-traps` memory)

- A member flipped to `internal` whose remaining callers are Kotlin needs
  nothing else; the JVM name mangles (`foo$server`), hiding it from Java too.
- A member with a *Java* caller still standing (production or test) takes
  `internal` + `@JvmName("original")` — the bytecode stays public and
  unmangled, so Java callers keep compiling; source-level Kotlin visibility
  still shrinks. Drop the `@JvmName` when the last Java caller converts.
  `@JvmName` does not apply to `open` members (all flip candidates are final).
- **Fir2Ir ICE risk:** an `internal` member inherited through a *Java*
  intermediate (`Aggregate`, `ProcessManager`) and called by test-module
  Kotlin code can crash the compiler. `TransactionalEntitySpec` calls
  `pm.changed()` through such a chain. Verify each flip empirically with
  `compileTestKotlin`; if the ICE fires, type the receiver as the Kotlin base
  in the spec, or defer that member to the wave converting the intermediate.

### Waves

- **Wave A ✅ (2026-07-23) — `SignalDispatchingRepository.java` → Kotlin.**
  Flipped to `internal`: `setEventHistoryLoader`, `uncommittedEventHistory`,
  `enableDoubleDispatchGuard`, and `commitEvents` (+`@JvmName` for the Java
  `AggregateTest`). The loader interfaces could NOT flip: the public
  `RecentHistory` class references `HistoryLoader` in its generic bound
  `L : HistoryLoader<R>`, so `EventHistoryLoader`/`StateHistoryLoader`/
  `HistoryLoader` stay `public` `@Internal` until that bound is redesigned.
  Dokka note: a KDoc link to the inherited protected *static*
  `defaultStorageFactory` does not resolve from a Kotlin subclass — qualify
  as `[Repository.defaultStorageFactory]`.
- **Wave B ✅ (2026-07-23) — `AbstractEntityRepository.java` → Kotlin.**
  Flipped `setStateHistoryLoader` and `appendToStateHistory` to `internal`;
  reworded the KDoc of `RecentStateHistory`/`StateHistoryLoader`/
  `EventHistoryLoader` that linked the now-internal members (internal
  members leave the Dokka set — the `no-internal-API-in-public-docs` rule).
- **Wave C ✅ (2026-07-23) — both endpoints → Kotlin.** `AggregateEndpoint`
  and `PmEndpoint` (package-private Java) became Kotlin `internal` classes;
  their Java subclasses keep extending them (internal classes compile to
  public bytecode, no name mangling). `AggregateEndpoint.runTransactionFor`
  became `internal` (zero external callers); `PmEndpoint.runTransactionFor`
  stayed `protected open` — the `PmDispatcher` testFixtures subclasses call
  it. Flipped `recordEvents` → `internal`; `hasUncommittedEvents`,
  `getUncommittedEvents`, and `TransactionalEntity.changed` → `internal` +
  `@JvmName` (Java callers remain: `AggregateRepository`, `AggregateTest`,
  `ProjectionTest`). **No Fir2Ir ICE:** the Kotlin spec calling the internal
  `changed()` through the Java `ProcessManager` intermediate compiled clean —
  the ICE needs the *subclass-inherited-call* shape, not a plain call.
- **Wave D ✅ (2026-07-23) — extended scope (product owner): convert the
  remaining Java callers of the bridged members and migrate their test
  suites** — the opening move of the broader tests-first Java→Kotlin
  migration; the major production-code migration waits until after 2.0.0.
  1. `AggregateRepository.java` → Kotlin. Two interop lessons:
     `loadOrCreate` (was package-private) became `internal` +
     `@JvmName` — the Java `AggregateRoot.partState()` still calls it;
     the `dispatchTo(Set<I>, …)` override needs
     `@JvmSuppressWildcards` — Kotlin emits `Set<? extends I>` for a
     type-parameter argument, breaking the Java override chain
     (`AggregatePartRepository`).
  2. `Projection.java` + `ProjectionEndpoint.java` → Kotlin.
     `playOn`/`of` became `internal` companion members (Kotlin callers
     only); `Projection.apply` kept `internal` + `@JvmName` for the Java
     `ProjectionTransaction` caller; `ProjectionRepository.setupInbox`
     switched from `ProjectionEndpoint.of(...)` to the protected
     constructor (Java same-package access).
  3. `AggregateTest.java` → `AggregateSpec.kt` (dead
     `generateProjectEvents()` dropped; the `aggregate()` cast-helper
     obsolete — friend visibility reaches `internal` directly),
     `ProjectionTest.java` → `ProjectionSpec.kt`. Javadoc references to
     the old suite names reworded in the `given` fixtures.
  4. All four `@JvmName` bridges dropped with their `@Internal` markers:
     `changed()`, `hasUncommittedEvents()`, `getUncommittedEvents()`,
     `commitEvents()` are now plain `internal`. `SignalDispatchingEntity`
     carries **zero** `@Internal` annotations.
  Follow-ups landed the same day: `getUncommittedEvents()` removed from
  `SignalDispatchingEntity` outright — `AggregateSpec` reads the journal
  through a private `uncommittedEvents()` extension over the `internal`
  `uncommittedEventHistory()` accessor; `UncommittedEventHistory` →
  Kotlin `internal` class (its only source-level references were the two
  lines in `SignalDispatchingEntity.kt`).
  The last two items closed the list (2026-07-23): `RecentHistory.append`
  (both overloads) → `internal`, dropping the file's last `@Internal`;
  `UncommittedEvents` → Kotlin `internal` class — the suspected Java
  fixture usage (`GivenAggregate`, `AggregateRepositoryTestEnv`) turned
  out to be a method *named* `withUncommittedEvents`, not a reference to
  the type, so nothing blocked the conversion.

The parent plan's "existing Java test suites stay Java" rule was superseded
for this task (product owner, 2026-07-23): `AggregateTest` and
`ProjectionTest` migrated to Kotlin in Wave D as the opening move of the
tests-first Java→Kotlin migration — which is exactly what let the `@JvmName`
bridges on the entity members go.

Verified per wave: `:server:test` (2012 tests green) + `:server:dokkaGenerate`;
after Wave C additionally the full `./gradlew build` (detekt: the converted
repositories needed `TooManyFunctions` and `TooGenericExceptionCaught`
suppressions). The branch already carries the breaking-change version bump
(`.511 → .520`).

## Step 3 — Wave E: the `AbstractEntity` internals

Analysis (2026-07-23) of the three remaining `@Internal` functions and the
seven `@JvmName` bridges of `AbstractEntity`:

- `ensureAccessToState()` — a dead hook since the cutover relaxed state
  access (zero overriders, zero external callers) → deleted in E1.
- `thisClass()` — flips to `internal open` once its Java overriders
  (`Aggregate`, `AggregatePart`, `ProcessManager`) are Kotlin (E2).
- `modelClass()` — pinned by the public `Entity` *interface* member, not by
  descendants; freeing it means removing the member from `Entity` and
  consolidating on `thisClass()`; two callers to retarget
  (`Transaction.kt:614`, `FilterTest.java:174`); zero cross-module callers
  (E3).
- The `@JvmName` bridges serve same-package Java *collaborators and tests*,
  not descendants. Caller map: `updateVersion`, `incrementState`,
  `setVersion` had none left → dropped in E1. Still pinned: `setState`
  (`RecordBasedRepositoryTest`, `TransactionalEntityJavaSpec`),
  `updateState` ×2 (`AbstractEntityTest`), `setLifecycleFlags`
  (`RecordBasedRepositoryTest`).

- **Wave E1 ✅ (2026-07-23):** `DefaultConverter.java` → Kotlin `internal`
  class (its `injectState` was the last production Java caller of the
  bridged members); `DefaultConverterTest.java` → `DefaultConverterSpec.kt`;
  `ensureAccessToState()` deleted; the three unpinned `@JvmName`s dropped
  (module `clean` build per the incremental-staleness trap).
- **Wave E2 (proposed):** `Aggregate.java` (324) + `AggregatePart.java`
  (147) + `ProcessManager.java` (287) → Kotlin; then `thisClass()` →
  `internal open`. These are public base classes end users extend from
  Java — the conversion must keep the Java-facing surface stable, and
  Java-bridge `XJavaSpec` coverage is warranted. Migrating
  `AbstractEntityTest` and `RecordBasedRepositoryTest` (abstract Java test
  base — mind the inherited-`@Nested`-not-discovered trap) frees the four
  remaining bridges.
- **Wave E3 (proposed, after E2):** remove `modelClass()` from the `Entity`
  interface; model-class access consolidates on the internal `thisClass()`.
