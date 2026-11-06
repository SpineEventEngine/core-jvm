---
name: feedback-no-restating-system-context
description: Docs of System-context types (`spine.system.server`, `*_events.proto`) must not restate that System events or records stay on the server side — it is implied by where the type lives
metadata:
  type: feedback
---

Do not explain, in the docs of a System-context type, that its data stays on the server side
or therefore keeps all the details (e.g. "System events stay on the server side, so the error
keeps all the details."). Being in `spine.system.server` and in a `*_events.proto` file
already says so.

**Why:** product owner (2026-11-05), on `CommandErrored.error`: the comment "duplicates the
knowledge of what system events are. This is assumed as long as we're in the
`spine.system.server` and in the `..._events.proto` file."

**How to apply:** document what a field holds ("The error that stopped the command."), not
the nature of the System context. The same goes for System entity states such as
`HandlingStatus`.
