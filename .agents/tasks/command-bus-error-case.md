---
slug: command-bus-error-case
branch: claude/command-bus-error-case
owner: claude
status: in-progress
started: 2026-11-06
---

## Goal

The Command Bus answers a command that it refuses with an `ErrorCase` only, and the server
stores what happened as specific System events. Slice 2 of splitting #1705 ("tell clients
only the kind of an error"); stacked on #1710 (`ProcessingStatus`).

## Decisions (2026-11-06)

- No masking; the details are stored as System events of the serving context.
- One System event per kind of problem, named after the kind of request.
- Filters stop producing `Error`; a filter that finds a problem posts its event itself and
  refuses with `reject(envelope, ErrorCase)`. `reject(envelope, Error)` is removed, and
  an application filter's refusal is not recorded by the framework.
- `CommandErrored` is removed: nothing posts or reads it any more. 2.0 is a major version
  still in snapshots, so the type is not kept for events stored by earlier versions.
- `ProcessingStatus.error` is only for a failed handler; each refusal has its own field.
- Contexts exchange events, so the Event Bus records `EventValidationFailed`, and
  `UnsupportedEventReceived` for an event from another context only.
- `ServerErrorHandler` changes in slice 4.

## Plan

- [x] Events: `CommandValidationFailed`, `UnsupportedCommandReceived`,
  `CommandTenantNotSpecified`, `CommandTenantNotExpected` (`command_log_events.proto`);
  `EventValidationFailed`, `UnsupportedEventReceived` (`diagnostic_events.proto`).
- [x] `ProcessingStatus`: `validation_error`, `unsupported`, `tenant_not_specified`,
  `tenant_not_expected`.
- [x] `BusFilter.reject(envelope, ErrorCase)`; `EnvelopeValidator` returns a
  `ValidationError`; `InvalidMessageHandler` for the `ValidatingFilter`;
  `DeadMessageHandler` returns nothing.
- [x] `TenantFilter` (the tenant checks moved out of `CommandValidator`);
  `DeadCommandHandler` and `DeadEventTap` post their events; `CommandAckMonitor` posts only
  `CommandAcknowledged`; `CommandLogProjection` subscribes to the new events.
- [x] Test library: `UnsupportedCommandGuard` reads `UnsupportedCommandReceived`.
- [x] Reviews (`spine-code-review`, `kotlin-engineer`, `review-docs`) applied: specs for
  `Responses.errorWith(ErrorCase)` and `causedError(ErrorCase)`; `CommandAckMonitor` posts
  nothing for an ack with `Status.error`, which an application filter may still build;
  `MemoizingWriteSide.hasSeenEvents()`; an end-to-end case for `tenant_not_specified`.
- [ ] Version `.630`; `clean build dokkaGenerate`; `pre-pr`.
- [ ] PR against `claude/processing-status` (on the owner's go-ahead).

## Differences from the plan

- The `EventBus` reaches the System write side through the Bounded Context it registers
  with, not through `BoundedContextBuilder`.
- `ResponseStatusSubject` is unchanged: it checks the statuses of queries, which slice 3
  changes.

## Next slices

3. The services answer with `ErrorCase` only (`ServiceDelegate`, `CommandPostingFailed`,
   the read events, read failures with a fixed gRPC status and `grpc-status-details-bin`).
4. `Status.error` deprecated with its helpers; `ServerErrorHandler` takes an `ErrorCase`.
