---
slug: remove-command-scheduler
branch: claude/command-scheduler-removal-a0b633
owner: claude
status: in-progress
started: 2026-11-24
---

## Goal

Remove the command scheduling feature before 2.0.0 GA: the scheduler SPI, its executor-based
implementation, the delay in `CommandContext`, and everything that serves them.

## Context

- A scheduled command never reaches an entity. When it is due, it still carries its schedule,
  so `InboxOfCommands.determineStatus()` stores it as `SCHEDULED`, and no delivery station
  reads that status. A probe confirmed it (2026-11-24): a control command reaches its
  aggregate, a scheduled one does not; with only that override removed, both do. The sender
  gets `OK` either way, and the inbox record stays forever. The existing tests stop
  at `CommandDispatched`, so they do not see it.
- `CommandScheduler.scheduledCommandIds` is static, unsynchronized, and only grows.
  Schedules live in memory, so a restart loses them.
- No sibling 2.x repository uses the scheduler API. The JVM client has no API that sets
  a schedule; only a hand-built `CommandContext` can carry one.
- `jdbc-storage` and `gcloud-jvm` store enum columns by `ordinal()`. Removing `SCHEDULED` from
  the middle of `InboxMessageStatus` moves `DELIVERED` from 3 to 2 and `TO_CATCH_UP` from 4
  to 3 in the stored columns. The stores of `delivery` only compare with `TO_DELIVER`.
- `CommandBus` became `ContextAware` (`7af28bf0e11`) only to obtain its scheduler.

## Decisions (2026-11-24)

1. `InboxMessageStatus.SCHEDULED`: remove and reserve; inboxes are drained before the upgrade.
2. `Command.SystemProperties`: remove the message, reserve field 4 of `Command`.
3. `CommandContext.schedule`: remove and reserve. The delay of a sender that is not updated
   becomes an unknown field, and its command runs at once.
4. `BusFilter.reject(envelope)`, which refuses with the `OK` status: remove.
   `reject(envelope, ErrorCase)` stays.
5. `CommandBus` posts `CommandDispatched` itself; `CommandFlowWatcher`, `FlightRecorder`, and
   `ContextAware` of the bus go.

## Plan

### Protobuf

- [x] `command.proto`: remove `Command.SystemProperties`, `CommandContext.Schedule`, and
      `CommandValidationError.EXPIRED`; reserve; drop the unused imports.
- [x] `inbox.proto`: remove `SCHEDULED`; reserve.
- [x] `command_log_events.proto`: remove `CommandScheduled`.
- [x] `command_log.proto`: remove `CommandTimeline.when_scheduled`; reserve.
- [x] Delete `scheduled_command.proto`.

### Production code

- [x] Delete `CommandScheduler`, `ExecutorCommandScheduler`, `CommandFlowWatcher`,
      `FlightRecorder`, `ScheduledCommand`, `ScheduledCommandRepository`.
- [x] `CommandBus`, `BoundedContext`, `BoundedContextBuilder`, `ServerEnvironment`,
      `SystemContext`, `CommandLogProjection`, `InboxOfCommands`, `BusFilter`.
- [x] `CommandMixin`: remove `isScheduled()`; trim `readValue()`.
- [x] `CommandFactory.createBasedOnContext()` (no callers are left),
      `GivenCommandContext.withScheduledDelayOf()`.

### Tests and fixtures

- [x] Delete the scheduler tests and fixtures; trim the suites that configure a scheduler.
- [x] `CommandMixinSpec`: `readValue()` equals `getField()` for every field.
- [x] `CommandBusSpec`: the dispatching of a command is recorded before the dispatcher
      gets the command (the deleted `CommandSchedulingTest` asserted the dispatching last).

### Wrap-up

- [x] `docs/migration/command-scheduling-removal.md`; fix the `scheduleCommandsUsing()` row of
      `server-environment-and-application-instances.md`.
- [x] Team memory: replace the examples that no longer exist.
- [x] Delete the task files of the merged #1732 and #1733.
- [x] Bump the version (breaking): `.780`.
- [x] `./gradlew clean build dokkaGenerate`; read the test counts; mutation check of
      `CommandDispatched`.
- [x] Reviewers: `spine-code-review`, `kotlin-engineer`, `review-docs`.

Not changed: the ADR and the archived task notes (historical records).

Follow-ups in other repositories, when each bumps CoreJvm: `compiler`
(`EventContextDependenciesTest`), `spine-ts` (Protobuf copies, re-exports, docs), `delivery`
(generated TypeScript of `admin-ui`).

## Log

- 2026-11-24 — feature mapped, probe run, plan written.
- 2026-11-24 — implemented (uncommitted). `clean check dokkaGenerate` green: `core` 174,
  `core-testlib` 20, `client` 183, `client-testlib` 2, `server` 2558, `server-testlib` 162,
  `server-otel` 22 tests, 0 failures, 0 skipped. The `server` count is 2589 (#1733) less 12
  for the follow-ups of #1731–#1733, less 19 removed here.
- 2026-11-24 — reviewers approve. Taken: the direct dispatching test, `SystemProjectionsIgTest`
  helpers, wording of the guide. Not taken: naming internal Protobuf types in the guide
  (team rule). The claim that a store by ordinals reads a leftover inbox message as
  `DELIVERED` is wrong: the stores keep the message as bytes and use the ordinal in lookups.
- 2026-11-24 — `clean build dokkaGenerate` at `.780` green: the counts above, `server` 2559 with
  the new dispatching test. Mutation check: without the posting of `CommandDispatched`, 4 tests
  fail (3 of `CommandLogTest`, 1 of `CommandBusSpec`).
