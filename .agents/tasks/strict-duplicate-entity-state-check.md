---
slug: strict-duplicate-entity-state-check
branch: strict-duplicate-entity-state-check
owner: claude
status: in-review
started: 2026-10-27
---

# Check duplicate entity states in tests too

PR #1698 made a Bounded Context fail to take a repository whose entity state type another
open context already serves (`DuplicateEntityStateException`). But the check is off under
`Tests` by default (`TypeConfigurator.checkDuplicateEntityStates(boolean)`). So the framework
has to tolerate duplicate entity states only for the sake of tests, and the lookup of the
serving context has to cope with several contexts.

This task removes the switch. The check is always on, and the tests that serve one type in two
open contexts are fixed instead.

All line numbers are on `master` at `2e597e36bf0`.

## Status

- [ ] Plan approved by the owner (CLAUDE.md gate).
- [x] Production changes. See "Design".
- [x] Tests fixed. See "Tests to fix", and "Found during implementation".
- [x] `DuplicateEntityStateSpec` updated: 14 cases, including a new one for two contexts
      with the same name.
- [x] Verified: full check twice, and a leak scan of the fixed tree (see "Verification").
- [x] Reviews by `spine-code-review`, `kotlin-engineer` and `review-docs` addressed
      (see "Review fixes").
- [x] Version bump to `2.0.0-SNAPSHOT.590`, authorized on 2026-10-27. The PR is breaking
      (a public method is removed, and the check now runs in tests), so the version is
      rounded up to the next multiple of ten, above the `.580` of #1699.
- [x] `pre-pr` passed, and PR #1700 is open (authorized on 2026-10-27).

## Found during implementation

- **`BlackBoxTest.CreateByBuilder` asserted the wrong black box.** `assertRepositories()`
  read the class-level `context`, not the `blackBox` built by the case. It passed only because
  both had a `BbProjectRepository`. Closing the class-level one first exposed it.
- **`AggregateQueryingTestEnv.InvisibleSound` built a context in a static initializer** and
  never closed it, so `MRSoundRecord` was served for the whole JVM. The first leak scan of
  the fixed tree found it. The fixture now creates a repository, which the test registers with
  its own context.
- **`QueryServiceTest.returnErrorOnQueryFail()`** built a second set of contexts with the same
  types while the first set was open. `setUpService()` now closes the previous ones.
- **A `Server` that was never started cannot be shut down** (`GrpcContainer` checks the state),
  so its contexts stay open. `ServerSpec` starts such a server only to shut it down.

## Review fixes

- `Server.shutdownAndWait()` closes the contexts in a `finally`, so they are closed even
  when the gRPC shutdown throws, e.g. for a server that was never started. Two new
  `ServerSpec` cases cover the closing, with and without a start.
- `ProjectionEndToEndTest` and `ProjectionRepositoryTest` close their contexts with
  try-with-resources instead of a last statement that a failed assertion would skip.
- Doc wording: `ServerEnvironment` class Javadoc, `ServedStateTypes`, the exception KDoc,
  `Server.shutdown()`, and one form for test comments that close a context first.
- Pre-PR reviews: teardowns of `TracingTest`, `OtelTracingSpec` and the `outside of tests`
  group of `DuplicateEntityStateSpec` reset the environment in a `finally`; the two cases of
  `AggregateRepositoryStateHistorySpec` build their context with `use {}`, inside the `try`
  that resets the environment; `getOrNull()` instead of `orElse(null)`; clearer wording of
  the `ServerEnvironment` class Javadoc; fixes to this file.
- Codex on PR #1700: `Server.Builder.build()` left the contexts it had built open when
  building a later one failed, e.g. with `DuplicateEntityStateException`. Nobody could close
  them, because no `Server` was returned. The builder now closes them before rethrowing.
  A new `ServerSpec` case fails without the fix, and so do three later cases of the spec.

## Evidence

Three runs in scratch copies of `master`, on 2026-10-27. Nothing of them is committed.

1. **Check forced on**, `./gradlew test`: 285 failures in 39 classes: `:server` 274 of 2,167,
   `:server-testlib` 10 of 159, `:server-otel` 1 of 22. Every failure is
   `DuplicateEntityStateException`.
2. **Check off, with a throwaway JUnit extension** that lists the domain contexts a top-level
   test class leaves open: 30 classes leak (list below). Five of them fail no test in run 1,
   only because no later class used their types in that order. The order of test classes
   varies between runs.
3. **Check on, only the failing classes that do not leak**, in one JVM: all pass except
   `RepositoryTest` (1) and `AggregateRepositoryStateHistorySpec` (10), which open a second
   context with the same type inside a case. `ProjectionStateHistorySpec` (2) fails as a
   consequence: a case of `AggregateRepositoryStateHistorySpec` fails to build before its
   `finally`, so `ServerEnvironment.reset()` never removes its failing history storage.

## Design

### Public API

1. **Remove `ServerEnvironment.TypeConfigurator.checkDuplicateEntityStates(boolean)`** and its
   `EnvSetting`. It has been on `master` since `2e597e36bf0` (2026-10-26), and is in no release.
2. **`DuplicateEntityStateException`: the constructor becomes `internal`.** It was public for
   the lookup of the second PR (#1699), to report several serving contexts. That case can no
   longer happen: only the check throws it. The message stays as it is.
3. **`Server.shutdownAndWait()` closes the contexts of the server**, as `shutdown()` does
   (`Server.java:128-142`). Its Javadoc names unit-test clean-up as the main use, and a test
   that calls it leaves its contexts open today (`TestClientTest`). This changes production
   behavior, so the PR description names it.

### Internals

4. **`ServedStateTypes` keeps one context per type**: `MutableMap<TypeUrl, BoundedContext>`.
   `serve(context, stateType)` always checks, and loses the `check` parameter. Contexts are
   still compared by identity, so two same-named contexts still count as two.
5. **The lookup returns one context.** `ServerEnvironment.contextsServing(TypeUrl)`, which
   returns a list of names, becomes
   `@Internal Optional<BoundedContext> findContextServing(TypeUrl)`. This is the signature that
   #1699 adds on top of `contextsServing()`, without its `@throws DuplicateEntityStateException`.
   So whichever PR merges second takes the version of this one, and #1699 drops its
   "several contexts" path.
6. **Docs.** `ServerEnvironment` class Javadoc, `BoundedContext.register()` and
   `BoundedContextBuilder.build()` lose "and the check is on". The `ServerEnvironment` Javadoc
   says that the rule holds in tests too, and that a test should close the contexts it builds.
7. **Delete `.agents/tasks/one-context-per-entity-state.md`**, the task file of #1698. It
   should have gone on the merge.

### Out of scope

- A permanent guard that fails a test class leaving a context open. The owner decided on
  2026-10-27 not to add one.
- `Server.Builder.contexts()` collects the contexts into an `ImmutableSet`, and
  `BoundedContext.equals()` compares names. A second context with the name of the first is
  built, dropped from the set, and never closed by `shutdown()`. A possible follow-up.
- Test suites of other repositories that leave contexts open. They fail with
  `DuplicateEntityStateException` once they use this version. The PR description says so.

## Tests to fix

The usual fix is to close what the test builds: `@AfterEach` for a field, `use {}` or
try-with-resources for a local, `server.shutdown()` for a `Server`.

**Leak a context (30 classes; run 2).** In `:server`: `TracingTest`, `AggregateRepositoryTest`,
`AggregatePartTest`, `AggregateRootTest`, `AggregateQueryingTest`, `BoundedContextSpec`,
`RejectionInFilterTest`, `StandTest` and `MultitenantStandTest` (the contexts are built by
`StandTestEnv.newStand()`, now `newContext()`, in `testFixtures`, which returned only the
`Stand`), `InMemoryDeliveryTest`
(its base `DeliveryTest`), `EntitySpec`, `FilterSpec`, `ProcessManagerRepositoryTest`,
`ProjectionSpec`, `StateRoutingTest`, `ThirdPartyContextTest`, `AbstractEntityLoggingTest`,
`DefaultConverterSpec`, `AggregateRepositoryViewsTest`, `EntityEventsTest`,
`CommandServiceMultiTenancySpec`, `CommandServiceSingleTenancySpec`, `CommandOnRejectionTest`,
`ProjectionEndToEndTest`, `QueryServiceTest`, `TwoBoundedContextsTest`, `ServerSpec`.
In `:server-testlib`: `StBlackBoxTest` and `MtBlackBoxTest` (their base `BlackBoxTest`),
`TestClientTest` (fixed by item 3). In `:server-otel`: `OtelTracingSpec` (the same pattern as
`TracingTest`; run 2 did not cover the module).

**Two contexts with one type inside a case.** `RepositoryTest.iterateBareRepository()` and
two cases of `AggregateRepositoryStateHistorySpec` build a second context with the type of
the context of `@BeforeEach`. They get their own context instead.

**Fail only because of the above (run 3), no change expected:** `AggregateSpec`,
`ProjectionRepositoryTest`, `SubscriptionServiceTest`, `RecordBasedRepositoryTest`,
`DoubleDispatchGuardSpec`, `AggregateRecentHistorySpec`, `ProjectionStateHistorySpec`,
`AggregateCommandEndpointTest`, `ProjectionRepositoryCatchUpSpec`. (`ProjectionRepositoryTest`
was changed later anyway; see "Review fixes".)

**`DuplicateEntityStateSpec`.** Drop the cases of the switch (`in tests`, `be turned on`,
`several, when the check is off`) and the `turnCheckOn()` set-up. Keep the `DefaultMode` case
as "check outside of tests too". Assert the lookup by identity of the context.

## Verification

Before the bump, `./gradlew build` was blocked by the version gate, so the first two checks
ran as `./gradlew :server:cleanTest check dokkaGenerate --no-build-cache` (JDK 17). Test counts
are read from the XML reports.

- Branch check 1 (2026-10-27): passes. `:server` 2,167 tests, `:server-testlib` 159,
  `:server-otel` 22, 0 failures.
- Branch check 2, after the first review fixes: passes. `:server` 2,169 tests (two new
  `ServerSpec` cases), `:server-testlib` 159, `:server-otel` 22, 0 failures.
- After the bump to `.590`: `./gradlew build dokkaGenerate` passes (the `pre-pr` build).
- After the Codex fix: `./gradlew build dokkaGenerate` passes. `:server` 2,170 tests.
- Leak scan: the throwaway extension of "Evidence" run 2, applied to a scratch copy of the
  fixed branch, reports no class. Not committed.
