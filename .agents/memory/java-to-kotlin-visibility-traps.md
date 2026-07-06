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
- Do NOT convert the `id`/`version`/`state` shortcut extensions into member properties of entity
  base classes: a member `val version` emits `getVersion()`, which collides with
  `HasVersionColumn.getVersion()` implemented by `ProcessManager`/`Projection` — a protected member
  cannot implement a public interface method, and a public one would silently override an
  entity-column getter.
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

**Why:** Discovered while converting `TransactionalEntity.java` (2026-07-06) and `Transaction.java`
(2026-07-07) to Kotlin. These are compile-/runtime-level facts, not style preferences.

**How to apply:** Before converting a core Java class (e.g. in `io.spine.server.entity`), grep for
same-package Kotlin callers of its protected/package-private members, and for `get*`-style column
getters across the subclass hierarchy; plan member folding and `internal` + `@JvmName` accordingly.
