# 👋 Welcome, Agents!

## Orientation

If `.agents/project.md` exists in this repository, read it first — it describes
the language, architecture, and role of this specific repo within the Spine SDK
organisation. It is a symlink to `docs/project.md`; to create one, copy
`.agents/guidelines/project.template.md` to `docs/project.md` and fill it in. If it
links to a shared requirements file (e.g. `jvm-project.md`), read that too.

- Start every session by reading `.agents/guidelines/quick-reference-card.md` (if present).
- For specific tasks (code review, PR prep, dependency updates, docs, etc.),
  prefer the matching skill from `.agents/skills/`.
- Full standards reference: `.agents/guidelines/_TOC.md` (if present) — consult when a
  skill doesn't cover the needed context.

Shared skills, scripts, and guidelines come from the `.agents/shared` submodule (the
[`agents`][agents-repo] repository) exposed via symlinks.
`./config/pull` initializes and floats it automatically; on a fresh clone that skips
`pull`, run `git submodule update --init --remote .agents/shared`.

## Commit and history safety

**Do not commit, push, tag, rebase, merge, cherry-pick, or otherwise write to git history**
unless one of the following is true *right now*:

1. The currently active skill's `SKILL.md` has a `## Commit authorization` section
   that explicitly permits the operation.
2. The user's *current* prompt explicitly requests the operation.

Authorization does not carry over between turns or sessions. When in doubt: stage
changes, show the diff, and stop — let the user commit.

See [`.agents/guidelines/safety-rules.md`](.agents/guidelines/safety-rules.md) → *Commits and history-writing*.

## Other safety rules

- All code must compile and pass static analysis.
- Do not auto-update external dependencies outside a dedicated update task.
- No analytics, telemetry, or tracking code.
- No reflection or unsafe code without explicit approval.

See [`.agents/guidelines/safety-rules.md`](.agents/guidelines/safety-rules.md) for the full list.

## Moving files

When moving or renaming tracked files, always use `git mv`. Do not simulate a
move by deleting the old file and creating a new one — preserve Git history
unless the user explicitly asks for a fresh replacement.

If `git mv` fails due to permissions or sandbox restrictions, request approval;
do not fall back to delete/create.

## Memory

Team-shared memory lives in `.agents/memory/` (checked into git). Use it for
feedback rules, durable project rationale, and external system pointers.
See `.agents/memory/README.md` for layout and write protocol.

Review `.agents/memory/MEMORY.md` at the start of every session.
Ruthlessly iterate until mistakes stop repeating.

## Asking questions

- Ask at most one question per message. If a decision has a small set of
  options, include those options as part of that one question.
- Do not bundle unrelated clarification questions. Ask the next question only
  after the user answers the previous one.
- Apply this rule both when the agent needs clarification and when the user's
  prompt means "ask questions".
- Prefer a reasonable assumption over another question when the answer would not
  materially change the next step.

## Verification & Quality

- Never mark a task done without proof (tests, logs, diff vs main).
- Ask: "Would a senior/staff engineer approve this?"
- For non-trivial changes: pause and consider a more elegant solution.
- Fix bugs autonomously — find root cause, no hand-holding, no band-aids.

## Core Principles

- Simplicity first: minimal code impact, minimal surface area.
- No laziness: always find root causes.
- Minimal side effects: avoid new bugs.
- Prefer early returns and clear naming.
- Challenge your own work before presenting it.

## Task planning

- Write plans to `.agents/tasks/<slug>.md` before coding.
  See `.agents/tasks/README.md` for format and lifecycle.
- Verify changes before marking a task done.
- Update memory if lessons emerged.
- Delete the task file on merge to master.

## Code review

Never review `gradlew` or `gradlew.bat` in any repository, including `config`.
These files are provided by Gradle and are not edited manually.

When reviewing a pull request or diff in a consumer repository, skip any
file that the `config` module distributes. Those files belong in a review
of the `config` repo, not the consumer repo — reviewing them there adds
noise without value.

Do **not** apply this skip rule when reviewing the `config` repository
itself. In `config`, these files are source files owned by the current
repo and must be reviewed normally, except `gradlew` and `gradlew.bat`.

In consumer repositories, skip without comment any path matching:

- `AGENTS.md`, `CLAUDE.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`
- `.agents/**` (except `.agents/project.md`)
- `.claude/**`, `.idea/**`, `.junie/**`
- `.github/copilot-instructions.md`
- `buildSrc/**` (except `buildSrc/src/main/kotlin/module.gradle.kts`)
- `gradle/`, `gradlew`, `gradlew.bat`
- `.codecov.yml`, `.gitignore`, `gradle.properties`, `lychee.toml`
- `.github/workflows/` — unless the workflow was introduced by this repo

[agents-repo]: https://github.com/SpineEventEngine/agents
