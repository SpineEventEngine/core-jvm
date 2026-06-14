@AGENTS.md

## Claude Code-specific notes

- Use Plan mode (`EnterPlanMode`) for architecture, refactoring, multi-file
  changes, or lengthy documentation. Show the plan (`ExitPlanMode`) before
  implementing.
- Track live progress with `TaskCreate`.
- In JVM repos: before reading library source code from `~/.gradle/caches`,
  follow the `api-discovery` skill — never `unzip` JARs directly.
- Per-developer memory lives in the built-in auto-memory dir. Use it for
  personal preferences, ephemeral project state, and per-machine resources.
  Litmus test: *would a teammate benefit from this next month?* → repo.
  Otherwise → auto-memory.
- This is living team memory. Update it regularly and keep it concise
  (<120 lines / ~2.5k tokens).
