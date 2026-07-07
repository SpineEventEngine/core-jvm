# Convert `TransactionalEntity` to Kotlin, fold extensions in as protected members

**Status:** done — awaiting commit/PR

> **Pending at PR time:** this change is API-breaking (public `update`/`alter`/`tryAlter`
> extensions and the `TransactionalEntityExtensions` facade functions are gone).
> Per the version policy, bump `version.gradle.kts` `2.0.0-SNAPSHOT.401` → **`.410`**
> (next multiple of ten) on the feature branch (`bump-version` skill).

## Goal

1. Convert `server/src/main/java/io/spine/server/entity/TransactionalEntity.java`
   to idiomatic Kotlin (`server/src/main/kotlin/.../TransactionalEntity.kt`).
2. Move the extension **functions** `update`, `alter`, `tryAlter` from
   `TransactionalEntityExts.kt` into the class as members: `update`/`alter` are
   `protected`; `tryAlter` is `public` + `@VisibleForTesting` (per review — tests
   call it directly; outside tests it is receptor-only since it needs an open
   transaction). Each has a `Consumer`-based overload for `void` Java lambdas.

## Key constraints discovered

- The extension **properties** `id`, `version`, `state` must **stay extensions**:
  a member `val version` would emit a `getVersion()` getter clashing with
  `HasVersionColumn.getVersion()` implemented by `ProcessManager`/`Projection`
  (protected member cannot implement a public interface method).
- Package-private members (`injectTransaction`, `releaseTransaction`,
  `transaction`, `updateStateChanged`) become `internal` + `@JvmName` so Java
  callers (`Transaction.java`, tests) keep compiling (avoids name mangling).
- Java same-package callers of `protected` members (e.g. `Transaction.onBeforeCommit`,
  tests calling `tx()`) still compile: JVM `protected` includes package access.
- Kotlin same-package non-subclass callers lose access → rework
  `TransactionalEntitySpec.kt` (renamed from `...ExtensionsSpec.kt`; `tryAlter` called via a fixture
  method) and `TransactionalEntityExtensionsJavaSpec.java` (facade is gone;
  becomes a Java-subclass bridge spec).
- Non-final Java methods stay `open`; Java `final` maps to Kotlin default.
- 9 fixture files import `io.spine.server.entity.alter`/`tryAlter` — imports
  must be dropped (calls resolve to the inherited members).

## Steps

- [x] `git mv` Java file → `src/main/kotlin/.../TransactionalEntity.kt`, rewrite in Kotlin
- [x] Strip `update`/`alter`/`tryAlter` from `TransactionalEntityExts.kt`, keep properties
- [x] Add `doTryAlter` exposer to `StockKeeper` fixture; drop stale imports (9 files)
- [x] Rework Kotlin spec; `git mv` JavaSpec → `TransactionalEntityJavaSpec.java` + rewrite
- [x] `version-bumped` skill (on `master` base — nothing to gate), then verification:
      repo-wide compile ✓, 150 entity tests ✓, 373 aggregate/procman/projection tests ✓,
      `:server:dokkaGenerate` ✓
- [x] Java-friendly `Consumer<B>` overloads of `update`/`alter`/`tryAlter`; the
      `B.() -> Unit` overloads marked `@JvmSynthetic` to kill Java overload
      ambiguity. `tryAlter` made `public @VisibleForTesting`; `StockKeeper.doTryAlter`
      wrapper removed (Kotlin spec calls `tryAlter` directly again).
- [x] `TransactionalEntitySpec.kt` renamed from `...ExtensionsSpec.kt` (class renamed too).
- [x] **Merge `TransactionalEntityTest.java` → `TransactionalEntitySpec.kt`** (one
      Kotlin suite for the now-Kotlin subject). Verified visibility empirically: a
      same-package Kotlin non-subclass CAN call Java package-private
      (`setLifecycleFlags`, static `Transaction.toBuilder`) and public `lifecycleFlags()`,
      but the four Kotlin-`protected` members (`isTransactionInProgress`, `tx`,
      `setArchived`, `setDeleted`) need a subclass → exposed via the spec-local
      `Fixture` (StringEntity-based; `activeTx`/`transactionInProgress`/`markArchived`/
      `markDeleted`). `returnActiveTxFlags` rewritten to avoid protected
      `Transaction.lifecycleFlags()` (asserts flag change is visible through the active
      tx instead). Retired the now-orphaned `TeEntity.java` (used only by the deleted
      test; `EmptyEntity` kept — still used by `EntityTest`/`AssigneeEntityTestEnv`).
      Result: 27 tests in the merged suite (17 converted + 10 pre-existing), all green;
      524 entity/aggregate/procman/projection tests green.
