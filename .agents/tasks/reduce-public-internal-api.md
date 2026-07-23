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
`UncommittedEventHistory`, `UncommittedEvents` (Java); `DoubleDispatchGuard`
(Java — converted last: its `protected doubleDispatchGuard()` accessor
proved caller-less and was deleted rather than kept as a public window).

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
- **Wave E2 ✅ (2026-07-23):** `Aggregate`, `AggregatePart`, and
  `ProcessManager` → Kotlin; `AbstractEntity.thisClass()` → `internal
  open`, dropping its `@Internal` (the four overrides — now all Kotlin —
  inherit the visibility automatically). Settled while implementing:
  - `ProcessManager.injectContext` (was package-private) → `internal` +
    `@JvmName` for the Java `ProcessManagerRepository` caller;
  - `select()` gained an explicit `checkNotNull(context)` — the
    null-marked `QueryingClient` constructor exposed the Java original
    passing a possibly-null context;
  - the Java same-package callers of the `protected` `tx()` /
    `dispatchCommand` / `dispatchEvent` overrides (the still-Java
    endpoints `AggregateEventReactionEndpoint`, `PmCommandEndpoint`,
    `PmEventEndpoint`, and `AggregateTransaction`) keep compiling via JVM
    `ACC_PROTECTED` package access;
  - Kotlin *test* callers lost that access: `AggregateSpec` switched
    `versionNumber()` → the public `version().number` (8 sites) and goes
    through new fixture bridges — `IntAggregate.exposedBuilder()`,
    `LastSignalMemo.doDispatchCommand()` (for `ProcessManagerSpec`);
  - the deprecated `historyBackward()`/`historyContains()` carry Kotlin
    `@Deprecated(..., ReplaceWith(...))` instead of `@InlineMe`;
  - `AbstractEntity.versionNumber()` is eliminated entirely (product owner):
    the mismatch helpers and `updateVersion` read `version().number`
    directly, and `AggregateSpec` carries a private `versionNumber()`
    extension as the test-side convenience;
  - the test-only `Aggregate.recentEventHistory()` re-export is removed
    and the parent method sealed (no `open`) — its one production caller,
    `DoubleDispatchGuard.java`, sits in the declaring package and keeps
    JVM package access;
  - `Aggregate.tx()` must KEEP its protected re-export override — removing
    it breaks the still-Java `AggregateCommandEndpoint` and
    `AggregateEventReactionEndpoint`, which reach the transaction through
    the Java package slice of `protected` (a latent dependency an
    up-to-date `compileJava` had masked); the override carries a
    constraint comment and dies when those endpoints convert.
- **Ledger close-out ✅ (2026-07-23):** the last four `@JvmName` bridges
  (`setState`, `updateState` ×2, `setLifecycleFlags`) and the loader
  interfaces are done — with two economies over the original plan:
  1. `RecordBasedRepositoryTest` did NOT need the 571-line migration: it
     already used the public `TestTransaction.archive/delete` path
     everywhere except three sites, which were swapped in place
     (`setEntityState` → `TestTransaction.injectState`; the two
     `MarkRecords` flag flips → `archive`/`delete`).
     `TransactionalEntityJavaSpec` seeds via `injectState` too and stays
     the sanctioned Java-bridge suite. `AbstractEntityTest` →
     `AbstractEntitySpec.kt`, dropping two cases the Kotlin language now
     guarantees (the finality-by-reflection checks and the null-argument
     NPE — enforced by `internal`-by-default finality and non-null
     parameter types).
  2. The `RecentHistory` generic-bound redesign proved unnecessary: with
     `Aggregate`'s re-export already deleted, the last `protected`
     accessors (`recentEventHistory` with a `@JvmName` for the Java
     `DoubleDispatchGuard`, `recentStateHistory`) flipped `internal`, and
     the WHOLE machinery went `internal` wholesale — `RecentHistory`,
     `RecentEventHistory`, `RecentStateHistory`, `HistoryLoader`,
     `EventHistoryLoader`, `StateHistoryLoader` — shedding the last
     `@Internal` markers of the entity package's Kotlin machinery. The
     user-facing history API is the entities' `protected`
     `eventHistoryBackward`/`eventHistoryContains`/`stateAt`/
     `stateHistoryBackward` only.
- **Wave E3 ✅ (2026-07-23), extended while implementing (product owner):**
  1. `modelClass()` removed from the public `Entity` interface. It survives
     as an `internal open` factory hook on `AbstractEntity` (through which
     `thisClass()` caches); the covariant overrides in the four entity base
     classes turned `internal` with it, dropping their `@Internal` markers —
     `AbstractEntity` and all four base classes now carry **zero**
     `@Internal` annotations. `FilterTest` → `FilterSpec.kt` (the sole
     caller through the interface; the Kotlin spec smart-casts to
     `AbstractEntity` under friend visibility).
  2. `ProcessManagerRepository` → Kotlin. The reverse trap handled with
     `internal` bridges for the Kotlin endpoint (`findOrCreateProcess`,
     `openTransactionFor`, and `isEventHistoryEnabled` on the base
     repository); `beginTransactionFor` stays `protected open` (the Java
     `TestPmRepository` fixture overrides it) and the `findOrCreate`
     re-export stays (the Java `ProcessManagerRepositoryTest` uses the
     package slice). `postCommands` became `internal`. The `@JvmName`
     bridge on `ProcessManager.injectContext` dropped — its only Java
     caller (`configure`) is Kotlin now.
  3. The five remaining dispatch-path Java endpoints → Kotlin:
     `AggregateCommandEndpoint`, `AggregateEventEndpoint`,
     `AggregateEventReactionEndpoint` (package-private → `internal`),
     `PmCommandEndpoint`, `PmEventEndpoint` (public `@Internal` →
     `internal`; Kotlin forbids a public class exposing an internal
     supertype, and their Java fixture subclasses compile against the
     public bytecode). Their `tx()` access goes through the new
     `internal` `TransactionalEntity.activeTransaction()` seam, which
     let BOTH `tx()` re-export overrides (`Aggregate`, `ProcessManager`)
     be deleted — IDEA's "redundant override" flag on the aggregate one
     is finally satisfied, for the right reason.
- **`DoubleDispatchGuard` → Kotlin `internal` ✅ (2026-07-23):** the last
  `public @Internal` class of the entity dispatch machinery.
  1. `check()` returns idiomatic `Error?` instead of `Optional<Error>`, so
     `detectDuplicate` sheds its `.getOrNull()`; the Guava
     `Iterators.any`/`Predicate` combos became `asSequence().any {}`.
  2. The `protected doubleDispatchGuard()` accessor had no callers at all
     and was deleted (a `protected` member could not have exposed an
     `internal` return type anyway).
  3. The `@JvmName("recentEventHistory")` bridge dropped — the guard was
     its last Java caller. Remaining `@JvmName`s in the module: only
     `loadOrCreate` (`AggregateRoot.java`) and `Projection.apply`
     (`ProjectionTransaction.java`).
  4. `DoubleDispatchGuardTest.java` → `DoubleDispatchGuardSpec.kt` — forced
     by the `Error?` return (the Truth `Optional` asserts would not
     compile), aligned with the tests-first migration policy. Fixture
     Javadoc (`IgTestAggregate`, `IgTestAggregateRepository`) re-pointed at
     the spec.
  5. Visibility-residue sweep over the now-`internal` classes (user):
     redundant `internal constructor()` on `RecentHistory`/
     `RecentEventHistory` removed; meaningless `public` on members of
     `internal` types (`RecentHistory.read`, `HistoryLoader.load`, both
     `stateAt`s) and redundant `internal` on their members
     (`useLoader`, `append` ×2, `runTransactionFor`, four
     `companion object`s) dropped. Inside an `internal` class both
     modifiers add nothing: the effective visibility is `internal` either
     way. `RoutingMap.kt` has the same pattern but predates this branch —
     left untouched.
