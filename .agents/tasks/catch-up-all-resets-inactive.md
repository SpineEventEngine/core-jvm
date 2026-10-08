---
slug: catch-up-all-resets-inactive
branch: catch-up-all-resets-inactive
owner: claude
status: in-progress
started: 2026-10-08
---

# A catch-up of all resets the archived and deleted instances too

Issue: [core-jvm#1687][issue-1687]. Decision taken in the issue: **Reset them too.**

## Goal

`catchUpAll()` sends the start signal, `CatchUpStarted`, to every stored projection
instance, active, archived, or deleted. Each one is deleted and rebuilt from the replayed
history, so no event is applied twice. An instance comes back active unless the replayed
events archive or delete it again. One the replayed history does not reach is removed, as
an active one is today.

## Context

Line references are at `f8e4d7bac0c` (`master` after #1684).

- `CatchUpProcess.dispatchCatchUpStarted()` (:391) takes its targets from
  `targetsForCatchUpSignals()` (:614): for a catch-up of all, `repository().index()`.
- `EntityRecordStorage.index()` (:105) lists the active records only. So the archived and
  deleted instances keep their state, and the replay, routed regardless of the lifecycle
  flags, applies the events since `since` on top of it.
- `ProjectionRepository.sendToCatchingUp()` (:369) has the same fallback for a
  `CatchUpSignal` sent with no IDs: `restrictToIds ?: index()`. The process never reaches
  it today: an empty set of targets reaches it only when `index()` is empty anyway.
- A catch-up of chosen IDs already resets them whatever their flags:
  `CatchUpEndpoint.onCatchUpStarted()` deletes the record by ID. So the change makes the
  catch-up of all consistent with it.

## Plan

1. `ProjectionRepository.sendToCatchingUp()`: with no `restrictToIds`, a `CatchUpSignal`
   goes to every stored record — a new private `storedIds()`, an unconstrained
   `RecordQuery` passed to `recordStorage().index(query)`, which, unlike `index()`, adds no
   lifecycle filter. The KDoc says so instead of claiming that signals "cannot be
   dispatched to all the repository instances".
2. `CatchUpProcess`: `dispatchCatchUpStarted()` dispatches through `dispatchAll(events)`,
   as the history is dispatched, so the repository alone decides what "all" means.
   `targetsForCatchUpSignals()` goes away. The Javadoc of the `STARTED` section, of
   `handle(CatchUpRequested)`, and of `DispatchCatchingUp.perform()` follow.
3. `ProjectionRepository.catchUp()` KDoc: what happens to the stored instances —
   deleted before the replay, rebuilt from it, inactive ones included for a catch-up of all.
4. Tests, new, Kotlin: `InactiveInstancesCatchUpIgTest`, `@SlowTest`, built like
   `EmptyRepositoryCatchUpIgTest`. The history comes from live events, so the instances are
   stored with it; then `TestTransaction` archives or deletes some.
   - rebuilds the archived and deleted instances, next to an active one, 1 and 3 shards;
   - rebuilds them when none is active — the path of #1684;
   - removes an inactive instance that the history does not reach.
   Each case checks: nothing failed, the job `COMPLETED`, the totals, the lifecycle flags,
   no messages left. Expected red on `master`: totals doubled, flags kept.
5. Verify: red on `master`, green with the fix; `./gradlew check dokkaGenerate`; reviewers.
6. Version bump, dependency reports, commits, PR — authorized in the prompt of 2026-10-08.

## Status

- [x] Branch from `origin/master` at `f8e4d7bac0c`; `./config/pull` committed first as
      "Update `config`" (Gradle wrapper 9.8.1, `buildSrc`, `publish.yml` pin)
- [x] Tests: `InactiveInstancesCatchUpIgTest`, 5 cases — see "Regression tests"
- [x] Fix, as planned — see "As implemented"
- [x] Version `.566`
- [x] Verification: `./gradlew build dokkaGenerate` passes. Every module tested, executed
      rather than restored from the cache: `:server:test` ran 2116 tests, the 5 new ones
      included, 0 failed; detekt, Checkstyle, PMD and Dokka clean. After the review
      changes: the catch-up suites (49 tests), detekt, Checkstyle, PMD and Dokka of
      `:server` pass again
- [x] Reviews — see "Reviews"
- [ ] PR

## Regression tests

`InactiveInstancesCatchUpIgTest`, outcomes read from the XML reports.

| Case                                                      | `master` | Fixed |
|-----------------------------------------------------------|----------|-------|
| rebuilds the archived and deleted instances, 1 shard      | red      | green |
| rebuilds the archived and deleted instances, 3 shards     | red      | green |
| rebuilds the instances when none of them is active, 1     | red      | green |
| rebuilds the instances when none of them is active, 3     | red      | green |
| removes an inactive instance the history does not reach   | red      | green |

On `master`: `second` and `third` total 10 instead of 5, and stay archived and deleted;
the archived `fourth`, which the history does not reach, stays stored. `first`, active,
is right in every case. Nothing failed and nothing was left in the inboxes: the defect
is silent.

The flags and the removal of `fourth` tell "Reset them too" from "Skip them": skipping
would give the right totals too.

## As implemented

- `ProjectionRepository.sendToCatchingUp()`: a `CatchUpSignal` with no `restrictToIds`
  goes to `storedIds()`, a new private function: `recordStorage().index()` with
  an unconstrained `RecordQuery`. `EntityRecordStorage` adds the active-only filter to
  `index()` and `readAll(RecordQuery)`, not to `index(RecordQuery)`.
- `CatchUpProcess.dispatchCatchUpStarted()` calls `dispatchAll(events)`, which passes no
  targets for a catch-up of all; `targetsForCatchUpSignals()` is removed.
- Docs: the KDoc of `catchUp()` says what happens to the stored entities, inactive ones
  included; `catchUpAll()` and `sendToCatchingUp()` follow. In `CatchUpProcess`: the
  "Not started" and `STARTED` sections, `handle(CatchUpRequested)`, and
  `DispatchCatchingUp.perform()`.
- The copyright hook replaced the header of `ProjectionRepository.kt` with that of the
  CodeMatters profile; it stays.

## Behavior change

Every catch-up of all, not only one of a repository with no active instance:

- An archived or deleted instance is reset and rebuilt. It comes back active unless the
  replayed events archive or delete it again; one archived before `since`, or not by an
  event, keeps only the state the replayed events build.
- An inactive instance that no replayed event reaches is removed, as an active one is.
- The start signal reaches more instances, so the catch-up waits for more
  `EntityPreparedForCatchUp` reports before it reads the history.

## Reviews

- `kotlin-engineer` — approved. Applied: the KDoc of `storedIds()` says why it does not
  call `index()`, so nobody "aligns" `index(RecordQuery)` with `readAll(RecordQuery)`;
  the leftover rationale in `sendToCatchingUp()`; a message instead of `Optional.get()`
  in the test.
- `review-docs` — approved with changes. Applied: the two items above, which it raised too;
  `catchUp()` no longer reads as if only a catch-up of all resets inactive entities;
  `catchUpAll()` on one line; "deleted" for "reset to default" in a bullet of
  `handle(CatchUpRequested)`. Left: the wording nits in the test helpers, copied verbatim
  from `EmptyRepositoryCatchUpIgTest` — they move together in the follow-up below.
- `spine-code-review` — approved. Its untested clause, an entity "comes back active, unless
  the replayed events archive or delete it again", is kept: after the reset,
  `findOrCreate()` builds a new entity, and a handler's `setArchived()` commits as usual.

## Follow-ups

- The helpers of the three catch-up integration tests — the recording `Delivery`,
  `jobStatuses()`, `totals()`, `undelivered()`, the pause past the turbulence — belong in
  `server/src/testFixtures` (already a follow-up of #1684, now with a third copy).
- `Repository.index()` says it lists "all the entities", while `EntityRecordStorage`
  lists the active ones only.

[issue-1687]: https://github.com/SpineEventEngine/core-jvm/issues/1687
