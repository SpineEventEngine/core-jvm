---
slug: one-context-per-name
branch: claude/one-context-per-name
owner: claude
status: in-progress
started: 2026-10-27
---

# One open Bounded Context per name, under `OpenContexts`

PR #1699 runs the catch-up (`CatchUpProcess`) and the shard maintenance
(`ShardMaintenanceProcess`) as process managers in every System context. `InboxDeliveries`
keys the inboxes of these two types by the type and the System context name. Two open
contexts with one name share that key (Copilot's thread 4391661021 on #1699): the catch-up
of the earlier context runs in the System context of the later one, and closing the later
context removes the inboxes the earlier one still needs.

PR #1700 makes one open context per entity state type a rule in tests too. This task adds
the same rule for names, so that #1699's key is correct by construction.

The branch starts at #1700's head `cb4df6dbf4d`. #1699 merges the `master` that includes
this change.

## Status

- [ ] Plan approved by the owner (CLAUDE.md gate).
- [x] `OpenContexts` with both checks; `ServerEnvironment` no longer keeps the record.
- [x] `DuplicateContextNameException`.
- [x] `BoundedContextBuilder.assumingTests()` adds a counter suffix: `AssumingTests-1`, ….
- [x] Tests fixed. See "Tests fixed".
- [x] Verified: full suites green (`:server` 2,180 with 1 skipped, `:server-testlib` 159,
      `:server-otel` 22). A probe run, in a scratch copy, logged every build while a context
      with the same name was open, without throwing: 4 builds, all in the specs of the rule
      itself (`DuplicateContextNameSpec`, `DuplicateEntityStateSpec`, `ServerSpec`).
- [x] Reviews by `spine-code-review`, `kotlin-engineer`, and `review-docs` addressed
      (see "Review fixes"). One finding is left for the owner (see "Open questions").
- [x] Version bump to `2.0.0-SNAPSHOT.600`, as the owner asked on 2026-10-27.
- [x] `./gradlew build dokkaGenerate` passed after the review fixes, regenerating the
      dependency reports for `.600`: `:server` 2,179 tests (1 skipped), `:server-testlib` 159,
      `:server-otel` 22, `:core` 180, `:client` 189, `:core-testlib` 25, `:client-testlib` 2;
      0 failures. No unresolved doc links.
- [ ] A PR with the base `strict-duplicate-entity-state-check` (#1700), as the owner decided
      on 2026-10-27.

## Design

- **`internal object OpenContexts`** (`server/src/main/kotlin/io/spine/server/OpenContexts.kt`)
  is the record of the open domain contexts of the JVM, and the guard of their creation.
  - `checkNameFree(name)` throws `DuplicateContextNameException` if an open context has the
    name. `build()` calls it before anything is built: a System context registers the inboxes
    of its repositories as soon as it is built, so a later check would let a rejected context
    touch the registrations of the open one.
  - `opened(context)` records the new context under its name right after it is created,
    before its repositories are added. So a nested build of the same name from a repository
    callback fails, and a failed build frees the name when it closes the context.
  - The check and the record are two steps: two threads building one name at the same moment
    may both pass. The owner chose this over a reservation of names being built, for
    simplicity (2026-10-27).
  - `serve()`, `forget()`, and `findContextServing()` move here from `ServerEnvironment`.
    They delegate to `ServedStateTypes`, which is unchanged apart from its KDoc.
  - `closed(context)` forgets the types first, then frees the name, so a new context that
    gets the name can also serve the types. Both records compare contexts by identity.
  - System contexts are not recorded. They are built through `buildPartial()`, not `build()`.
- **`BoundedContextBuilder.build()`** calls `checkNameFree()` before `buildSystem()`, and
  `opened()` after `buildDomain()`.
- **`assumingTests()`** names a context `AssumingTests-<n>`, with a JVM-wide counter, so that
  test contexts built by it, `BlackBox.singleTenantWith()`, and `blackBoxWith()` never clash.
  `BoundedContextNames.assumingTests()` stays `AssumingTests`: `Model` uses it for classes of
  packages without `@BoundedContext`.

## Found during implementation

- **Without the suffix, the rule failed 567 tests in 80 classes.** 549 of them were
  `AssumingTests` clashes with contexts that earlier tests left open, mostly contexts without
  repositories; #1700's leak scan did not report such contexts. 24 of those were one cascade:
  `AggregateRepositoryTestEnv` builds a context in its static initializer.
- **The suffix left the explicit-name leaks.** See "Tests fixed".
- **The rule settles an item #1700 left open:** `Server.Builder` collects the contexts into a
  set, and `BoundedContext.equals()` compares names. A second context with the name of the
  first was dropped from the set and never closed. Now its build fails.

## Tests fixed

- `CommandBusBuilderTest`: closes the context of `setUp()`.
- `AbstractEventReactorTest`: closes both black boxes of `setUp()`.
- `ContextAwareTestTest`: closes the context its first case creates.
- `SubscriptionServiceTest`: closes the contexts of `ctx()` and `randomCtx()`.
- `BoundedContextSpec`: compares closed contexts with one name.
- `ServerSpec`: two `users()` contexts now fail on the name.
- `DuplicateEntityStateSpec`: reads `OpenContexts`. Its same-name case is removed:
  `DuplicateContextNameSpec` covers it.

## New tests

- `DuplicateContextNameSpec`, with context names no other test uses.

## Review fixes

- `kotlin-engineer`: `closed()` forgets the types before it frees the name. Its other findings
  were about the reservation of names being built, which is gone (see "Design").
- `review-docs`: the public `BoundedContext.name()` Javadoc no longer links `@Internal` API;
  wording fixes in the KDoc of `OpenContexts`, `ServedStateTypes`, and the exception.
- `spine-code-review` and `kotlin-engineer`: the stale KDoc of `ServedStateTypesSpec`.

## Open questions

- **A domain context can be named like a System context.** While `Users` is open, a domain
  context named `Users_System` is accepted, so it shares a name with the System context of
  `Users`. `OpenContexts` could reject such a name (and the reverse), or the rule could stay
  for domain contexts only. The owner's call.
- **#1700's task file** describes `ServerEnvironment.findContextServing()` and its record as
  the design. It goes stale once this lands.

## Release notes

- Two open Bounded Contexts can no longer have one name: `BoundedContextBuilder.build()`
  throws `DuplicateContextNameException`. A closed context frees its name.
- `BoundedContextBuilder.assumingTests()` names each context `AssumingTests-<n>`.
- `ServerEnvironment.findContextServing()` is removed (`@Internal`, added by #1700).
