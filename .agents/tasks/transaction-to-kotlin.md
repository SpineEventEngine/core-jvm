# Convert `Transaction` to Kotlin; hide the four `TransactionalEntity` tx-control members

**Status:** implemented — awaiting commit/PR. Stacked on PR #1644
(`transactional-entity-to-kotlin`); do **not** fold back into it. Delete this file on merge.

## Goal

Resolve the PR #1644 Codex P2: `TransactionalEntity.kt`'s four transaction-control members —
`injectTransaction`, `releaseTransaction`, `transaction()`, `updateStateChanged` — were
`internal` + `@JvmName`, i.e. JVM-public and callable by ANY Java. Make them
`@JvmSynthetic internal` (invisible to Java, in-module Kotlin only). Their sole production
caller `Transaction.java` must become Kotlin first so it can still call them.

## What changed

- `server/src/main/java/.../entity/Transaction.java` → `.../kotlin/.../Transaction.kt`
  (`git mv` + idiomatic Kotlin, KDoc). Visibility mapping:
  - `internal @JvmName`: `entity`, `builder`, `version`/`setVersion`, `lifecycleFlags`,
    `setArchived`, `setDeleted`, `incrementStateAndVersion`, `entityId` — a **production Java**
    collaborator (`Migration`, `Phase`, `BatchDispatch`, `Aggregate`, Java tx subclasses) still
    calls them; accessors became properties with `@get:`/`@set:JvmName`.
  - `protected`: constructors, `version()`-getter, `propagate`, `commitAttributeChanges`, plus
    `deactivate`/`markStateChanged` (called by test-module Kotlin subclasses — see the ICE note).
  - plain `internal`: `initAll`, `stateChanged`, `phases`, `isActive`, `rollback(Error)`,
    `toBuilder` — only non-subclass Kotlin calls them now.
- `TransactionalEntity.kt`: the four members → `@JvmSynthetic internal`; added a
  `@JvmSynthetic internal fun triggerOnBeforeCommit()` forwarder (the `protected` `onBeforeCommit`
  is invoked by `Transaction.kt`, a non-subclass).
- `AggregateTransaction.java`: deleted the pure re-export `lifecycleFlags()` override so the base
  can be non-`open` `internal @JvmName` (`Aggregate.java` still resolves `tx.lifecycleFlags()`).
- **Coupled tests → Kotlin** (chosen to drop the six test-only `@JvmName`s from production):
  `TransactionTest` (+ subclasses `AggregateTransactionTest`, `PmTransactionTest`,
  `ProjectionTransactionTest`), `StubTransaction`, `TransactionalEventPlayerTest`. Backtick names,
  Kotest, class-level `@DisplayName` only.
- `TransactionalEntityJavaSpec.java` stays **Java** (deliberate Java-bridge): reroute — the
  `StubTransaction` ctor auto-injects; live state via same-package `entity.tx().builder()`.
- `version.gradle.kts` → `2.0.0-SNAPSHOT.411` (non-breaking; `.410` is #1644's).

## Traps hit (recorded in `.agents/memory/java-to-kotlin-visibility-traps.md`)

- Reverse visibility trap (Kotlin non-subclass loses same-package access to the now-Kotlin
  collaborator's `protected` members).
- `internal (+@JvmName)` member inherited-and-called by a **test-module Kotlin subclass** →
  Fir2Ir ICE; cure is `protected` (dropping `@JvmName` alone does not fix it).
- JUnit ignores `@Nested` inherited from an **abstract** base → flatten to inherited `@Test`
  methods (this also restored 18 previously-inert test executions across the 3 subclasses).

## Verification

- `./gradlew build` green (compile + full suite + detekt). Transaction suites: Aggregate 15,
  Pm 14, Projection 17, TxPlayer 2, JavaSpec 4, `TransactionalEntitySpec` unchanged — all pass.
- Grep confirms no Java source references the four members.
