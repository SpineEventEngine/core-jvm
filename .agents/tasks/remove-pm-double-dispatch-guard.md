---
slug: remove-pm-double-dispatch-guard
branch: claude/github-issue-1744-c78f9f
owner: claude
status: in-progress
started: 2026-11-27
---

## Goal

Remove the opt-in double-dispatch guard from process managers, as issue #1744 asks.
Aggregates keep the guard with an unchanged API.

## Context

- Issue: https://github.com/SpineEventEngine/core-jvm/issues/1744
- The guard scans the journal of the events an entity emitted and matches their
  `pastMessage`. A process manager that answers a signal with commands journals nothing
  for it, so the guard catches a command that led to an event, and misses a command that
  led to a command. Journaling the commands too is judged too much.
- The guard lived in the shared `SignalDispatchingRepository` / `SignalDispatchingEntity`
  base. A Kotlin subclass cannot hide an inherited `protected` member, so the guard API
  moves down to `AggregateRepository` / `Aggregate`.

## Plan

- [x] `SignalDispatchingRepository`: remove the guard switch, the history depth, the
      journal check at registration, and the guard install; update the docs.
- [x] `SignalDispatchingEntity`: remove the guard field and the duplicate checks.
      `DEFAULT_HISTORY_DEPTH` stays.
- [x] `AggregateRepository`: take over `useDoubleDispatchGuard()`,
      `doubleDispatchGuardEnabled()`, `eventHistoryDepth()`, `setEventHistoryDepth()`
      with the same signatures; install the guard in `create()` and `toEntity()`.
- [x] `Aggregate`: own the guard; the duplicate checks become `private`.
- [x] `ProcessManager`: dispatch without the duplicate checks.
- [x] `ProcessManagerRepository`: drop the guard from the docs.
- [x] Tests: remove `PmDoubleDispatchGuardSpec`; drop the guard options of
      `JournalTestPmRepo` and the duplicate checks of `JournalTestProcman`.
- [x] Bump the version (`.810`: a breaking change, and `.801` is taken by #1742).
- [x] Verify with `./gradlew build dokkaGenerate`.

## Log

- 2026-11-27 — issue #1744 filed; implementation started on a branch from `origin/master`.
- 2026-11-27 — `./gradlew build dokkaGenerate` green: 2710 `:server` tests, 0 failures.
