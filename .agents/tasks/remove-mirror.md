---
slug: remove-mirror
branch: claude/remove-mirror
owner: claude
status: in-progress
started: 2026-11-09
---

## Goal

Remove the `Mirror` projection of Spine 1.x and the `MirrorMigration` assistant from
the public API before 2.0.0 GA, as issue #1715 describes.

## Context

- Issue: https://github.com/SpineEventEngine/core-jvm/issues/1715
- `Mirror` was "deprecated in Spine 2.0" only by a comment. It had no deprecation marker,
  so it would ship in the GA as public API. 2.0 is not GA yet, so we remove it directly,
  like #1667 does for `AggregatePart`.
- After the change, 1.x aggregate data has no supported upgrade path. This is the same
  product decision as §8 of `docs/migration/aggregates-without-event-sourcing.md`.

## Plan

- [x] Remove `mirror.proto` (`Mirror`, `MirrorId`, `EntityColumns`) and
      `mirror_migration.proto` (`MirrorsMigrated`).
- [x] Remove the `io.spine.server.migration.mirror` package.
- [x] Remove `StorageFactory.createMirrorStorage(ContextSpec)`.
- [x] Remove the migration tests, Java fixtures, and test protos.
- [x] Use `Menu` instead of `Mirror` in `EntityQueryProcessorTest.failOnInvalidQuery()`.
- [x] Update §8 of `docs/migration/aggregates-without-event-sourcing.md`.
- [x] Update the open task notes that mention `MirrorStorage` or `MirrorToEntityRecord`
      (`event-store-context-prefix.md`; `spec-scanner-state-retention.md` belongs to the
      merged #1681, so it is left as is).
- [x] Bump the version (`.650`: a breaking change, and `.640` is taken by #1713).
- [x] Verify with `./gradlew clean build` and `dokkaGenerate`.

## Log

- 2026-11-09 — issue #1715 filed; implementation started on a branch from `origin/master`.
- 2026-11-09 — `clean build dokkaGenerate` green: `:server:test` 2458 tests, 0 failures (1 skipped).
