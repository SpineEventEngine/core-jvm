---
slug: remove-event-appliers
branch: claude/remove-applier-class-e80026
owner: claude
status: in-progress
started: 2026-11-23
---

## Goal

Remove the deprecated `@Apply` annotation, the `Applier` receptor, and the code that
detects appliers, now that aggregates no longer use event sourcing.

## Context

- `Applier` and `EventApplierSignature` served only `AggregateClass.failIfHasAppliers()`,
  which turned a leftover `@Apply` into a `ModelError` (ADR 0001, D2).
  `AggregateClass.stateEvents()` and `applierOf()` had no callers.
- Without the annotation, a leftover `@Apply` is a compile error, which is earlier than the
  `ModelError`. ADR 0001 scheduled the removal for v2.0.0; PR #1730 removed other
  deprecated aggregate API the same way.
- Base: `claude/classvalue-migration-analysis-868972` (PR #1731, `.750`). Version: `.760`.
- Downstream checkouts that still declare `@Apply`: `chat-bot` (4 aggregates) and the
  vendored examples in `documentation`. They already fail with the `ModelError` against
  current snapshots.

## Plan

Production code (`server`):

- [x] Delete `aggregate/Apply.java`, `aggregate/model/Applier.java`,
      `aggregate/model/EventApplierSignature.java`.
- [x] `AggregateClass`: drop the `stateEvents` map, `failIfHasAppliers()`, and
      `stateEvents()`. (`applierOf()` went in the base branch, `6e29e3f98fa`.)
- [x] `Aggregate.kt` KDoc: drop the `ModelError` sentence about appliers.
- [x] `VersionIncrement.fromEvent()` Javadoc: drop the link to `Apply` and the stale
      aggregate example.
- [x] `Phase` Javadoc and the `NoReaction` / `Nothing` proto docs: drop the mentions of
      appliers.

Tests and fixtures:

- [x] Delete `ApplierTest` and the `EngineAggregateWithApplier` fixture; remove
      `AggregateClassTest.rejectAppliers()`.
- [x] `RcWrongAnnotation`: drop `@Apply`; keep `@Command` and `@Subscribe`.
- Test and fixture names that still say "applier" (`FaultyAggregate.brokenApplier`,
  `ReceptionistAggregate.makeApplierFail`, some `AggregateSpec` titles) stay: they do not
  refer to the removed types.

Docs:

- [x] Migration guide: `@Apply` is removed (compile error); move it from "Deprecated" to
      "Removed" in the quick reference.
- ADR 0001 stays as a historical record.

## Verification

- [ ] No code references to `Apply`, `Applier`, `EventApplierSignature`, `applierOf`,
      `stateEvents` outside prose.
- [ ] `./gradlew build dokkaGenerate` green; `server` test count drops only by the deleted
      cases.
- [ ] `spine-code-review` and `review-docs` on the branch diff.

## Follow-ups

- `config`: remove the `apply` / `applyc` live templates from
  `.idea/live-templates/Spine.xml`.
