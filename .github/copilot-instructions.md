# GitHub Copilot Instructions

## Repository context

This repository is part of the Spine SDK organisation (~40 repos).

Universal agent instructions are in [`AGENTS.md`](../AGENTS.md) at the
repository root — read it first.

If `.agents/project.md` exists, read it before reviewing. It provides the
language, architecture, role, and code review checklist for this specific repo.

Additional guidelines are in `.agents/guidelines/` — see
`.agents/guidelines/_TOC.md` for the index.

## Do not review

Never review `gradlew` or `gradlew.bat` in any repository, including `config`.
These files are provided by Gradle and are not edited manually.

If the current repository is `config`, review its files normally unless noted
above: they are authoritative there. In other repositories, the following files are managed by
the `config` submodule and must be reviewed in the `config` repository, not
here. In those consumer repositories, skip them without comment:

- `AGENTS.md`, `CLAUDE.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`
- `.agents/**` (except `.agents/project.md`)
- `.claude/**`, `.idea/**`, `.junie/**`
- `.github/copilot-instructions.md`
- `buildSrc/**` (except `buildSrc/src/main/kotlin/module.gradle.kts`)
- `gradle/`, `gradlew`, `gradlew.bat`
- `.codecov.yml`, `.gitignore`, `gradle.properties`, `lychee.toml`
- `.github/workflows/` — unless the workflow was introduced by this repo

## Universal rules

**Do not suggest:**
- Any git history operation — `git commit`, `git push`, `git tag`,
  `git rebase`, `git merge`, `git cherry-pick`, `gh pr merge`, or any other
  command that writes to history — leave these to the developer.
- Auto-updating dependency versions outside a dedicated update task.
- Feature flags, backwards-compatibility shims, or fallbacks for scenarios
  that cannot occur in the current codebase.
- Analytics, telemetry, or tracking code.
- Reflection or unsafe code without explicit approval.
