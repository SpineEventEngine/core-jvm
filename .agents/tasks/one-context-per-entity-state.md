---
slug: one-context-per-entity-state
branch: claude/awesome-joliot-2f6ff3
owner: claude
status: in-progress
started: 2026-10-23
---

# Serve each entity state type in one domain Bounded Context

The first of two PRs that replace PR #1694. The second one, "Rebuild catch-up as process
managers", depends on this one. Its task file is `.agents/tasks/catch-up-as-process-managers.md`
on the branch `claude/quirky-agnesi-56b7d3` (section "Prerequisite: the check PR").

All line numbers are on `master` at `39ef520dc16`.

## Status

- [x] Branch starts at `origin/master` (`39ef520dc16`), with no commits of #1694.
- [x] Probe copied: `SameTypeInTwoContextsProbeTest.kt` in
      `server/src/test/kotlin/io/spine/server/delivery/`.
- [x] Exception name agreed with the owner on 2026-10-23: `DuplicateEntityStateException`.
- [x] Plan approved by the owner (CLAUDE.md gate) on 2026-10-23.
- [x] Implementation. See "Plan".
- [x] Tests: `DuplicateEntityStateSpec`, 13 cases, all pass. The probe copy is deleted.
- [x] Pre-PR review fixes (owner's go-ahead on 2026-10-23): `forget()` in a `finally`, with
      a case where a repository fails to close (it fails without the `finally`); the spec resets
      the environment after each case and closes contexts that should not have been built;
      `@throws` on `build()`; doc wording. Weak references stay out, as decided.
- [x] Team memory correction from #1694, as a separate commit.
- [ ] Version bump and `pre-pr` (authorized on 2026-10-23).
- [ ] PR (not authorized yet).

## Problem

An example. Two contexts in one JVM, `First` and `Second`, each register
`DeliveryTestEnv.CalculatorRepository`. A command posted to `First` is handled and stored by
`Second`, and its event is posted on the bus of `Second`. No error is reported. This is
`SameTypeInTwoContextsProbeTest`, run on `master`.

- **Why.** The `Delivery` keeps one inbox per entity state type for the whole JVM. The inbox
  registered last replaces the earlier one (`InboxDeliveries.java:70-73`).
- **The `Server` does not check this either.** `TypeDictionary.Builder.putAll()` calls
  `map.put(type, context)` (`TypeDictionary.java:111`), so the last context wins, in the order
  of a `HashSet`.
- **The existing check covers one context only.** `VisibilityGuard` stops a second repository of
  a state class within one context (`VisibilityGuard.java:70-87`).

## Design

### 1. The record

A new package-private class `io.spine.server.ServedStateTypes`. `ServerEnvironment` holds one
instance. It maps each entity state type (`TypeUrl`) to the open domain contexts that serve it.

- **Added** in `BoundedContext.register(Repository)`, which both paths go through: the builder
  (`BoundedContextBuilder.java:618-624`) and `internalAccess().register()`
  (`BoundedContext.java:640-643`). So contexts built by `BlackBox` are covered too:
  `BlackBox.from(builder)` calls `builder.build()`.
- **The check runs first in `register()`**, before `registerIfAware()`. The reason:
  `registerWith()` already builds the inbox, and building it registers it in the `Delivery`
  (`Repository.java:542-552`, `Inbox.java:220`). A check made later would throw after the
  second context has already taken the inbox of the first one.
- **Check and add are one `synchronized` step**, so two contexts built at the same time cannot
  both pass.
- **Contexts are compared by identity.** `BoundedContext.equals()` compares names
  (`BoundedContext.java:589-593`), but two context objects with the same name are still two
  contexts with two inboxes.
- **Removed** in `BoundedContext.close()`, after `shutDownRepositories()`. After that point, the
  inboxes of the closed context are gone, so a new context may take the type.
- **System contexts are skipped**: only `DomainContext` instances are added. Only the framework
  adds repositories to a System context, and each System context belongs to exactly one domain
  context. Today they register `CommandLogRepository` and `ScheduledCommandRepository` when the
  command log is on (`SystemContext.java:79-84`).
- **References are strong.** This follows `InboxDeliveries`, which also keeps repositories until
  they close. Risk: tests that never close their contexts keep them in memory. See "Checks
  during implementation".

### 2. The exception

`io.spine.server.DuplicateEntityStateException extends RuntimeException`, public, like
`UnknownEntityStateTypeException`. It carries only a message:

```
The entity state type `spine.test.delivery.Calculator` is served by more than one open
domain Bounded Context: `First`, `Second`. Serve each entity state type in one context only,
or close `First` before building `Second`.
```

The public constructor takes the type and the names of the contexts. The second PR uses it
when its lookup finds several contexts.

### 3. The switch

`ServerEnvironment.TypeConfigurator.checkDuplicateEntityStates(boolean)`, an `EnvSetting`
like the other settings:

```java
ServerEnvironment.under(Tests.class)
                 .checkDuplicateEntityStates(true);
```

- **Defaults:** off under `Tests`, on under every other environment type.
- **The record is kept when the check is off.** Only the throwing depends on the switch.
- **`ServerEnvironment.reset()`** restores the default of the switch. It keeps the record,
  because the record describes contexts that are still open, not a setting.
- The Kotlin `under<Tests> { checkDuplicateEntityStates(true) }` works with no extra code.

### 4. The lookup

```java
@Internal
public ImmutableList<BoundedContextName> contextsServing(TypeUrl stateType)
```

on `ServerEnvironment`, so the `delivery` package can call it. The result is empty, has one
name, or, with the check off, several names. The second PR fails with
`DuplicateEntityStateException` in the last case.

### 5. A failed build closes the half-built context

Example: `Second` registers `TaskView` and then `Calculator`, which `First` already serves.
`build()` throws, and nobody can close `Second`, so the record would keep `TaskView` served by
it until the JVM stops.

So `BoundedContextBuilder.build()` closes the context when registering its repositories or
dispatchers fails, and then rethrows (`BoundedContextBuilder.java:575-587`). An error thrown by
`close()` is added as suppressed. Without this, a later test that uses `TaskView` would see two
contexts in the lookup of the second PR.

### Out of scope

- `Server` collapsing contexts with the same name. With the check on, two such contexts can no
  longer serve the same type, which is the harmful case.
- `InboxDeliveries.unregister()` removes by type only. With the check on, two open contexts
  never share a type, so it cannot remove the inbox of another context.

## Plan

1. Add `DuplicateEntityStateException`.
2. Add `ServedStateTypes` with `serve(BoundedContext, TypeUrl, boolean check)`,
   `forget(BoundedContext)` and `contextsServing(TypeUrl)`.
3. `ServerEnvironment`: hold the record, add the `EnvSetting` and the lookup; reset the setting
   in `reset()`; document the rule in the class Javadoc ("Relation to Bounded Contexts").
4. `TypeConfigurator.checkDuplicateEntityStates(boolean)`.
5. `BoundedContext.register()`: check and add first, for a `DomainContext` only.
   `BoundedContext.close()`: forget the context after `shutDownRepositories()`.
6. `BoundedContextBuilder.build()`: close the context when registration fails.
7. The new spec (see "Tests"), and delete the probe.
8. Commit the team memory correction from #1694 separately: apply
   `git diff origin/master...claude/quirky-agnesi-56b7d3 -- .agents/memory/`
   (`kotlin-storage-spi-interop-traps.md` and its line in `MEMORY.md`).
9. `bump-version` (`.577` → `.578`), `pre-pr`, PR. Each needs the owner's authorization.

## Tests

A Kotest spec, `server/src/test/kotlin/io/spine/server/DuplicateEntityStateSpec.kt`, with the
copyright header. It replaces the probe: the scenario of the probe becomes the `BlackBox` case.

- Outside tests (`DefaultMode`, with in-memory factories set for it): two domain contexts with
  the same state type. Building the second throws `DuplicateEntityStateException`, and the
  message names the type and both contexts.
- Under `Tests`: the same two contexts build with no error by default, and the second one
  throws after `checkDuplicateEntityStates(true)`.
- The same type is allowed again after the first context has closed.
- A repository added later through `internalAccess().register()` is checked.
- Contexts built by `BlackBox` are checked.
- System contexts are not counted: two contexts with `SystemSettings.enableCommandLog()` build
  with the check on, and `contextsServing()` of the command log type is empty.
- The lookup reports none, one, and, with the check off, two contexts.
- A failed build leaves nothing in the record (the example in "Design / 5").
- One context registering the same state type twice still fails with the error of its own
  checks, not with the new exception. For `CalculatorRepository`, that error comes from the
  command bus (`IllegalArgumentException`: `AddNumber` already has a dispatcher), before
  `VisibilityGuard` is reached.

Every case closes its contexts and turns the switch back off. Each case first checks that
`contextsServing()` of its type is empty, so a context left open by another test fails the
case with a clear message instead of a confusing one.

## Checks during implementation

Two temporary runs of `:server:test` on 2026-10-23, in a scratch copy of this branch. The copy
had an instrumented `ServedStateTypes`, and the probe, but not the new spec. Nothing of it is
committed.

- **Check off (the default):** 2,154 tests, 1 failure. The failure is the probe, which fails
  on `master` by design. So the record and the closing of half-built contexts break no test.
- **Check forced on:** 274 of 2,154 tests fail, in 35 test classes, all with
  `DuplicateEntityStateException`. The most affected are `StandTest` (35),
  `AggregateSpec` (32), `ProjectionRepositoryTest` (27) and `AggregateRepositoryTest` (24).
  This is why the check is off under `Tests` by default.
- **Memory.** A first report said the record was empty at the end. That was wrong: a second,
  empty record instance wrote to the same file. Measured again, one report per instance: at the
  end of `:server:test`, the record holds 122 contexts that tests never closed (193 entries,
  42 types). The largest sources are `StandTest.createStand` (40) and
  `AggregateQueryingTest.setUp` (24). `:server:test` still takes about 2 minutes, with no
  memory errors. Weak references would avoid keeping these contexts, but they would make
  the check depend on the garbage collector. The owner decided on 2026-10-23 to keep strong
  references.
- **Order of test classes varies between runs.** In one full run, `ReceptionFailureTest` ran
  before the spec and left two contexts serving `Receptionist` open: it never closed its
  `BlackBox`es ("We don't care about closing black boxes in this test."). The precondition of
  the spec caught it. Fixed: the test now closes them with try-with-resources. Verified with
  both classes in that order.
- **Full build:** `./gradlew check dokkaGenerate --no-build-cache` passes. `:server` runs
  2,165 tests with 0 failures; the other modules run 577 tests with 0 failures.
