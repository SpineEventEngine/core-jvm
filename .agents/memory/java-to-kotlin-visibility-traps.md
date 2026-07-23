---
name: java-to-kotlin-visibility-traps
description: Visibility traps when converting core Java entity classes to Kotlin — protected/package-private mapping, column-getter clashes
metadata:
  type: project
---

Converting a Java class that has same-package collaborators to Kotlin changes who can call its members:

- Java `protected` means subclass **or** same package, and Kotlin honors that for Java-declared
  targets (`TransactionalEntityExts.kt` could call the Java-protected `builder()`). Once the class
  is Kotlin, its `protected` is subclass-only for *Kotlin* callers — same-package Kotlin extensions
  break and must be folded in as members. *Java* same-package callers keep compiling, because JVM
  `ACC_PROTECTED` includes package access.
- Java package-private members → Kotlin `internal` **plus `@JvmName`** on each function; otherwise
  the bytecode name is mangled (`foo$server`) and same-package Java callers
  (e.g. `Transaction.java`) stop compiling.
- The `id`/`version`/`state` shortcut extensions CAN become **public** member properties of the
  now-Kotlin `AbstractEntity` (done 2026-07-17, `:server:build` + 1968 tests green). A member
  `val version` emits `getVersion()`, which the earlier note feared "collides" with the
  `HasVersionColumn.getVersion()` default (implemented by `Aggregate`/`ProcessManager`/`Projection`):
  in practice a **public** `val version` cleanly overrides that default with the identical value
  (`getVersion()` returned `version()` anyway), and entity-column scanning is unaffected — like the
  Fir2Ir note, the "collision" was over-cautious. The `id`/`state` twins have no column-getter to
  clash with at all. Caveat that still holds: the property must be **public** — a `protected`
  `val version` cannot implement the public interface method `getVersion()`. Keep the backing fields
  underscore-prefixed (`_id`/`_state`/`_version`) so they stay distinct from the properties; the
  interface methods `id()`/`state()`/`version()` coexist with the properties (different JVM names).
  **Superseded 2026-07-20:** `HasVersionColumn` was removed outright as a redundant `@Internal`
  marker — together with its sibling `HasLifecycleColumns` — because `SpecScanner` adds the
  `version`/`archived`/`deleted` columns to every entity's `RecordSpec` unconditionally and the
  `get*` defaults had no callers. So this `getVersion()`-vs-interface-default clash is now moot:
  `AbstractEntity.version` is the sole `getVersion()`. `getArchived()`/`getDeleted()` are gone too —
  read lifecycle flags via `isArchived()`/`isDeleted()` from `WithLifecycle`.
- Java non-final methods must become `open` in Kotlin, or existing overrides
  (e.g. `Aggregate.recentHistory()`, `ProcessManager.missingTxMessage()`) break.
- **Converting a same-package Java *test* of the now-Kotlin class hits the same wall.**
  Empirically confirmed (throwaway compile, 2026-07-06): a same-package Kotlin non-subclass
  *can* call Java package-private members (instance `setLifecycleFlags`, static
  `Transaction.toBuilder`) and public ones (`lifecycleFlags()`), but *cannot* call the class's
  Kotlin-`protected` members (`tx`, `isTransactionInProgress`, `setArchived`, `setDeleted`) — nor
  a collaborator's `protected` (`Transaction.lifecycleFlags()`). Route those through a spec-local
  subclass fixture that re-exposes them (the established `Fixture`/`doTryAlter` pattern), or
  rewrite the assertion to use accessible API.
- **Java-friendly overloads:** giving a `B.() -> Unit` member a sibling `Consumer<B>` overload
  makes Java lambdas ambiguous (javac can't pick between `Function1` and `Consumer` for an
  implicitly-typed lambda). Mark the Kotlin-lambda overload `@JvmSynthetic` so Java sees only the
  `Consumer` one; Kotlin call sites still bind the function-type version.
- **Reverse trap — converting a *collaborator* (`Transaction.java` → Kotlin, 2026-07-07):** an
  already-Kotlin same-package *non-subclass* (`TransactionalEntity.kt`) that called the
  collaborator's Java-`protected` members (`entity()`, `lifecycleFlags()`, `setArchived()`,
  `setDeleted()`) stops compiling once they are Kotlin-`protected`. Widen them to `internal`
  (`final` ones → `internal` + `@JvmName`; the `open` `lifecycleFlags()` cannot take `@JvmName`,
  so drop its pure re-export override in `AggregateTransaction` and make the base non-`open`
  `internal @JvmName`). Members with **no** Kotlin non-subclass caller stay `protected`.
- **A `protected open` user-override callback invoked by a non-subclass** (`Transaction.kt` calls
  the entity's `onBeforeCommit()`): add a `@JvmSynthetic internal` forwarder on the entity
  (`triggerOnBeforeCommit`) and call that instead.
- **`internal` (± `@JvmName`) inherited-and-called by a *test-module Kotlin subclass* crashes the
  compiler** — `Fir2Ir` `SpecialFakeOverrideSymbolsResolver`: `No override for FUN … visibility:internal`
  (triggered here through the Java intermediate `EventPlayingTransaction`). Removing `@JvmName`
  does NOT help. **Cure: make the member `protected`** (normal fake-override resolution). So a
  member a Kotlin subclass calls → `protected`; a member only non-subclass Kotlin + Java call →
  `internal` (+ `@JvmName` for the Java caller). `deactivate`/`markStateChanged` became `protected`.
- **JUnit does not discover `@Nested` classes inherited from an abstract superclass** (concrete-class
  `@Nested` run fine; inherited `@Test` methods run, inherited `@Nested` classes do not). An abstract
  test base (`TransactionTest`) must declare its cases as flat `@Test` methods, not `@Nested`.
- **The Fir2Ir ICE also needs the *subclass-inherited-call* shape — a plain (non-subclass) call
  compiles clean even through a Java link** (2026-07-23, `reduce-public-internal-api`):
  `TransactionalEntitySpec` (test-module Kotlin, NOT a subclass) calls `internal @JvmName`
  `changed()` on a receiver whose static type chain passes through the Java `ProcessManager`,
  and `AggregateTest`-adjacent Kotlin fixtures likewise touch `internal` members — all compiled
  with no ICE. So: internal member + Java intermediate is dangerous only when a test-module
  Kotlin *subclass* calls it as inherited; external call sites are fine.
- **The Fir2Ir ICE needs a Java link in the inheritance chain — an all-Kotlin chain does NOT ICE**
  (`AbstractEntity.java` → Kotlin, 2026-07-17). `Fixture : TransactionalEntity<…>()` (test-module
  Kotlin subclass) inherited-calls `internal setState`/`checkEntityState` through the now-all-Kotlin
  chain `Fixture → TransactionalEntity(kt) → AbstractEntity(kt)` and compiled clean. The earlier ICE
  fired only through the Java intermediate `EventPlayingTransaction`. So: `internal` inherited by a
  Kotlin subclass is safe **when every link from the caller down to the declaration is Kotlin**;
  keep the "→ `protected`" cure for chains that still pass through a Java class. Verify empirically
  with `compileTestKotlin` rather than pre-emptively widening.
- **`protected` (Java) ≠ package-private (Java) when mapping to `internal` — `internal` CONTRACTS a
  `protected` subclass API.** A Java `protected` member is external-subclass API; a package-private one
  is not. Both, when they have a same-package non-subclass Kotlin caller (`Transaction.kt`), *tempt* you
  to `internal`, but `internal` removes a real `protected` member from external Kotlin subclasses (Java
  subclasses keep it via `@JvmName`'s public symbol). Fix: keep it `protected` and route the non-subclass
  Kotlin caller through a **public** path — e.g. `AbstractEntity.defaultState()` stayed `protected`, and
  `Transaction.kt` switched from `entity.defaultState()` to `entity.modelClass().defaultState()` (the
  public `EntityClass.defaultState()` that `IdField` already used). Only widen genuinely package-private
  members to `internal`.
- **Dropping (or adding) `@JvmName` on an `internal` member can leave STALE same-module Kotlin
  callers under incremental compilation** (2026-07-23, Wave D4): after removing
  `@JvmName("changed")`, `PmEndpoint.class` still called the unmangled `changed()` →
  `NoSuchMethodError` at runtime in `server-testlib` tests, while `:server:testClasses` compiled
  "green". The incremental compiler does not treat the JvmName-attribute change as an ABI change
  for all callers. Cure: `./gradlew :server:clean` and a full rebuild after any `@JvmName`
  add/drop; treat a downstream `NoSuchMethodError` on a freshly renamed member as staleness, not
  as a code bug.
- **Kotlin emits wildcards for type-parameter generics in override positions — breaking Java
  subclass chains** (2026-07-23, `AggregateRepository` conversion): an override of the Java
  `dispatchTo(Set<I>, …)` declared with Kotlin `Set<I>` compiles to `Set<? extends I>` (declaration-
  site variance + non-final type argument), and javac then rejects Java subclasses with "same
  erasure, yet neither overrides the other". Fix: `ids: @JvmSuppressWildcards Set<I>`. Final type
  arguments (`Iterable<Event>`) emit no wildcard — only type parameters and non-final classes do.
- **Guava `checkNotNull` (NPE) ≠ Kotlin `checkNotNull` (ISE).** Converting `id() = checkNotNull(_id)`
  verbatim silently changes the thrown type NPE→ISE. Preserve with `_id ?: throw NullPointerException(…)`
  (no `!!` — the skill forbids it). Non-null Kotlin *param* types keep their NPE via `Intrinsics`, so only
  explicit `checkNotNull` on fields/returns drifts.
- **Locals and parameters shadow proto-DSL receiver properties, not the other way around**
  (2026-07-23, `AbstractEntitySpec`): in `project { id = projectId }` with a ctor param `id` in
  scope, the assignment target resolves to the *parameter* ("'val' cannot be reassigned"), because
  Kotlin gives locals/params priority over implicit-receiver members. Qualify the DSL property
  (`this.id = …`) or rename the outer local.
- **Inside an `internal` class, member `public`/`internal` modifiers are no-ops — sweep them after
  internalizing** (2026-07-23, `DoubleDispatchGuard`/recent-history sweep): the effective visibility is
  `internal` either way, so `internal constructor()`, `internal fun`, `internal companion object`, and
  explicit `public fun` are residue once a formerly-public class turns `internal`; drop them (an empty
  redundant `()` primary ctor also trips detekt `EmptyDefaultConstructor`). The one observable
  difference: an `internal` member's JVM name mangles (`foo$server`) while a public member of an
  internal class stays unmangled — so keep `internal` (or add `@JvmName`) only if a same-module *Java*
  caller still uses the member.

**Why:** Discovered while converting `TransactionalEntity.java` (2026-07-06), `Transaction.java`
(2026-07-07), and `AbstractEntity.java` (2026-07-17) to Kotlin. These are compile-/runtime-level facts,
not style preferences.

**How to apply:** Before converting a core Java class (e.g. in `io.spine.server.entity`), grep for
same-package Kotlin callers of its protected/package-private members, and for `get*`-style column
getters across the subclass hierarchy; plan member folding and `internal` + `@JvmName` accordingly.
