---
slug: remove-aggregate-parts
branch: claude/remove-deprecated-api-cad139
owner: claude
status: in-progress
started: 2026-11-23
---

## Goal

Remove `AggregatePart`, `AggregateRoot`, and the code that serves them from the public API
before 2.0.0 GA, as issue #1667 describes.

## Context

- Issue: https://github.com/SpineEventEngine/core-jvm/issues/1667
- The API is deprecated in favour of `ProcessManager`: it does not isolate an invariant.
  2.0 is not GA yet, so we remove it directly, like `Mirror` in #1716.
- A concurrent session works on the model classes. Removing `AggregatePartClass` and
  `PartFactory` now spares it changes to code that goes away anyway.
- Outside `core-jvm`, only the 1.x `todo-list` example (on `1.0.0-SNAPSHOT`) uses the API.

## Plan

Production code (`server`):

- [x] Delete `AggregatePart.kt`, `AggregateRoot.java`, `AggregatePartRepository.java`,
      `DefaultAggregatePartRepository.kt`, `AggregateRootDirectory.java`,
      `InMemoryRootDirectory.java`, `model/AggregatePartClass.java`, `model/PartFactory.java`.
- [x] `BoundedContext`: remove the `aggregateRootDirectory` field and
      `InternalAccess.aggregateRootDirectory()`.
- [x] `BoundedContextBuilder`: remove the `rootDirectory` field, `aggregateRootDirectory()`,
      and the public `setAggregateRootDirectory(Supplier)`.
- [x] `DefaultRepository.of()`: remove the `AggregatePart` branch, its `DEPRECATION`
      suppression, and `defaultAggregatePartRepository()`.
- [x] `AggregateRepository.loadOrCreate()`: remove the `@JvmName` kept for the Java
      `AggregateRoot` caller (only Kotlin callers remain).
- [x] `AggregateClass`: remove the Javadoc sentence about `AggregatePartClass`.
- [x] Reword the prose about aggregate parts in `Commander` and in the `@BoundedContext`
      annotation of `core`.
- [x] Make `Aggregate.modelClass()` and `AggregateRepository.toModelClass()` `final`, like
      their `ProcessManager` and `Projection` siblings: only the part classes overrode them.

Tests and fixtures:

- [x] Delete `AggregatePartTest`, `AggregateRootTest`, `InMemoryRootDirectoryTest`,
      `model/AggregatePartClassTest`, the `aggregate/given/part` package,
      `AggregateRootTestEnv`, `AggregatePartTestEnv`, and `PersonRoot`.
- [x] `PersonNamePart` → `PersonNameAggregate` (`git mv`), a plain `Aggregate`.
      `EntityEventsTest` still needs a second aggregate that handles `CreatePersonName`
      and reacts on `PersonRenamed`. Remove its `commandToPart()` case: it tested
      dispatching to a part. Its check that the aggregate is created moves to
      `eventToReactorInProcman()`, so `PersonNameAggregate.TYPE` stays in use.
- [x] `DefaultRepositorySpec`: remove the aggregate-part case.
- [x] `BoundedContextBuilderTest`: remove the two `AggregateRootDirectory` cases.
- [x] Test protos of `server`: remove the types that only the deleted fixtures use —
      `AggTaskComments` and `Comment` (`task.proto`), `AggAddComment` (`commands.proto`),
      `AggCommentAdded` (`events.proto`), `ProjectLifecycle` and `ProjectDefinition`
      (`project.proto`). Reword the `PersonFirstName` comment.
- [x] Test protos of `server-testlib`: remove the unused aggregate-part states
      `TuProjectPart` and `TuComments` (`entities.proto`).

Docs:

- [x] `docs/migration/aggregates-without-event-sourcing.md`: remove the `AggregatePart`
      mentions in §1; add a "Removed" quick-reference entry that points to `ProcessManager`.
- [x] Leave ADR 0001 and the archived task notes as they are: they are historical records.

Version and verification:

- [x] Bump the version to `2.0.0-SNAPSHOT.740` (breaking: public API removed; `.730` is
      taken by the `classvalue-migration-analysis` branch).
- [x] `./gradlew clean build` (test protos change) and `./gradlew dokkaGenerate`.
- [x] Grep that no references remain outside the historical docs.

## Log

- 2026-11-23 — plan written; implementation done in the working tree.
- 2026-11-23 — `clean build dokkaGenerate` green (`server`: 2616 tests, 1 skipped);
  after the `server-testlib` and `EntityEventsTest` follow-ups, `:server-testlib` clean
  build (162 tests) and `EntityEventsTest` (7 tests) green.
- 2026-11-23 — committed locally: the version bump, the removal, and the dependency reports.
  Not pushed. The `classvalue-migration-analysis` branch is to be rebased onto this one.
