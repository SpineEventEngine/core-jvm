---
slug: remove-model-duplicate-handler-check
branch: claude/classmap-application-duplication-061ab7
owner: claude
status: in-progress
started: 2026-11-23
---

## Goal

Remove the duplicate command-handler check of the `Model` (`ClassMap.checkDuplicates`),
and everything that exists only for it: `DuplicateCommandReceptorError`,
`DuplicateHandlerCheck`, and the reset of the models (`Model.dropAllModels()`,
`ModelTests.dropAllModels()`, and their calls in tests).

## Context

- Two other checks reject two handlers of one command type: the `CommandBus` of a context
  (`CommandDispatcherRegistry`), and `Application.Builder.build()` across the contexts of
  an application (`DuplicateCommandHandlerException`, PR #1727).
- The `Model` check compares the classes of one context name, as the `@BoundedContext`
  package annotation gives it, across the whole JVM, when a model class is created.
  So it rejects classes that never run together, and one command type in two applications,
  which `Application` allows on purpose. It also fires before `Application.Builder.build()`
  with an `Error`.
- Tests reset the models only so that the check would not compare classes of previous tests.
- `DuplicateHandlerCheck` has no users in 2.x; `ModelVerifier` of 1.x used it.
- `Model` keeps its per-context instances: `StateSubscriberMethod` uses `contextName()`.

## Plan

- [x] Remove the check from `ClassMap`, and the reset from `Model`.
- [x] Remove `DuplicateCommandReceptorError` and `DuplicateHandlerCheck`.
- [x] Remove `ModelTests.dropAllModels()` and its test, and `ModelTestsTestEnv`.
- [x] Remove the duplicate cases of `ModelTest` and their classes in `ModelTestEnv`.
- [x] Remove the calls of `dropAllModels()` from the tests of `server`.
- [x] Note the removals in `docs/migration/unregistered-tenants.md`.
- [x] Bump the version to `.720`: a breaking change, since public API is removed.
- [x] Verify with `./gradlew check dokkaGenerate`, then `./gradlew build`.

## Log

- 2026-11-23 — implementation started in a worktree from `master` (`c7efaf2e48b`).
- 2026-11-23 — `check dokkaGenerate` and `build` pass. `:server:test` runs 2632 tests
  (was 2634), `:server-testlib:test` 162 (was 164): only the four removed cases are gone.
