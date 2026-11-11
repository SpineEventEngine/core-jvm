---
slug: remove-internal-access-register
branch: claude/spine-core-jvm-1721-b7f169
owner: claude
status: in-progress
started: 2026-11-11
---

## Goal

A repository joins a Bounded Context only through its builder, so `checkUnique()` sees every
repository of an `Application` when `Application.Builder.build()` runs. Issue #1721.

## Context

- Issue: https://github.com/SpineEventEngine/core-jvm/issues/1721
- Since #1720, `checkUnique(builders)` (`ContextRegistry.kt`) runs once, over the repositories
  declared on the builders. `BoundedContext.InternalAccess` let tests add repositories and
  dispatchers to a built context, unseen by that check (item 5 of "To confirm" in
  `application-instances.md`).
- `InternalAccess.registerEventDispatcher()` had one production caller,
  `EventDispatchingRepository.registerWith()`: that call is how every aggregate, process manager
  and projection repository joined the buses. Passing a repository to it from outside would run
  `registerWith()`, subscribe the repository and give it an inbox, all behind `checkUnique()`.
- The branch starts at the head of PR #1723 (`fcac495ca7f`, `.700`), which was merged into
  `master` as `a108f30a896` with the same tree; the PR targets `master`.

## Decisions (the open questions of the issue)

1. **System context.** `SystemClientTestEnv` adds `MealOrderRepository` to the domain builder
   (option 2 of the issue). A domain repository that subscribes to System events is registered
   with the System event bus as well, so `DefaultSystemWriteSideTest` still proves that
   `SystemWriteSide.postEvent()` posts to the System context. No new API.
2. **`checkUnique()` does not cover System contexts.** Nothing outside the framework adds a
   repository to a System context any more. Every System context has the same framework
   repositories, so comparing them would always collide; #1719 keys their inboxes by type and
   context.
3. **`BoundedContext.register(Repository)` stays `protected final`.** `SystemContext` lives in
   another package and registers its repositories through it. `BoundedContext` cannot be
   subclassed outside the framework.
4. **The dispatcher route closes too.** `register(Repository)` subscribes a repository to the
   buses itself, after the in-context duplicate check; `EventDispatchingRepository.registerWith()`
   no longer does. `registerEventDispatcher()` and `registerCommandDispatcher()` refuse
   a repository, which also covers `install(Probe)`. Probes keep working: `install()` is
   a method of `BoundedContext`, not of `InternalAccess`, and `BlackboxProbe` hands out no
   repositories.

## Plan

- [x] `BoundedContext`: remove `InternalAccess.register()`, `registerCommandDispatcher()`, and
      `registerEventDispatcher()`; `register(Repository)` subscribes the repository to the buses;
      the protected `register…Dispatcher()` methods refuse repositories.
- [x] `EventDispatchingRepository`, `SignalDispatchingRepository`: no bus registration in
      `registerWith()`; docs updated.
- [x] `checkUnique()` KDoc: the builders declare every repository of their contexts.
- [x] Migrate the test call sites to the builder (`add()`, `addEventDispatcher()`,
      `addCommandDispatcher()`).
- [x] Remove `ForeignContextConfig` and `BoundedContextSpec.RestrictRegistrationCalls`; add
      a case for a repository refused by `registerEventDispatcher()`.
- [x] Bump the version (`.701`, not breaking: `InternalAccess` is `@Internal`).
- [ ] Verify with `./gradlew check dokkaGenerate`, then `build dokkaGenerate` after the bump.

## Log

- 2026-11-11 — plan approved; implementation started on top of PR #1723.
- 2026-11-11 — `./gradlew check dokkaGenerate` green: `server` 2591 tests, 0 failures
  (1 skipped); `client` 186, `core` 188, `server-testlib` 163. Review fixes re-checked with
  the affected specs, `:server:detekt`, and `:server:dokkaGenerate`. Version bump and
  commits wait for the owner.
