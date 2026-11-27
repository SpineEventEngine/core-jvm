---
slug: emitted-command-origin
branch: claude/spine-core-jvm-1743-e84e0e
owner: claude
status: in-progress
started: 2026-11-27
---

## Goal

Fix issue #1743: a command emitted by a `@Command` method must keep the signal ancestry.
Its `CommandContext.origin` must be the handled signal as an origin, so that `parent()`,
`origin()`, and `rootMessage()` of the command, and of the events it causes, lead back
to the root signal.

## Context

- `CommandEmitter.toSuccessfulOutcome()` takes only the actor context of the handled
  signal and gives it to the client `CommandFactory`, which builds a context without
  an origin. Nothing on the server has ever set `CommandContext.origin` since the field
  came in 2019 (`b39dc034722`); `CommandMixin.rootMessage()` and `origin()` already read it.
- `CommandEmitter` is the only place that produces commands for commanders, process
  managers, and async commanders, for handled commands, events, and rejections.
- Events already carry the whole chain in `past_message`, with no depth limit.

## Plan

1. In `CommandEmitter.toSuccessfulOutcome()`, set `handledSignal.asMessageOrigin()` as
   the origin of each produced command. Keep `CommandFactory` for the ID, the message
   check, and the fresh actor context; no new client API.
2. Kotlin spec `server/src/test/kotlin/io/spine/server/model/CommandEmitterSpec.kt`:
   a commander that handles a command, a commander that handles an event (the chain
   `Command A → Event B → Command C → Event D` of the issue), and a process manager.

## Verification

- [x] `CommandEmitterSpec`: 5 of 5 pass with the fix, 0 of 5 without it (2026-11-27).
- [x] `./gradlew check dokkaGenerate`: green, `:server:test` 2723 tests (2026-11-27).
- [x] Version bumped to `.802` (open PR #1742 takes `.801`); `./gradlew build` green.

## Open

- Commands now carry the whole ancestry, as events already do. Protobuf parses at most
  100 levels of nesting by default, so a chain of about 95 signals would fail to parse;
  before the fix every emitted command cut the chain. A possible follow-up, not in scope.
