---
slug: catch-up-ig-test-fixtures
branch: catch-up-ig-test-fixtures
owner: claude
status: in-progress
started: 2026-10-08
---

# Shared helpers of the catch-up integration tests

A follow-up to #1684 and #1688: see "Follow-ups" in `catch-up-of-empty-repository-hangs.md`
and `catch-up-all-resets-inactive.md`.

## Goal

The three catch-up integration tests of `CounterView` take their helpers from one base
class in `server/src/testFixtures`. Each test keeps its cases, its assertions, and its
test count.

## Context

The branch starts at `4fd47fc82e4`, the head of `catch-up-all-resets-inactive` (#1688,
open, approved), since `InactiveInstancesCatchUpIgTest` comes from it. The PR targets that
branch and is retargeted to `master` once #1688 merges.

All three tests are in `server/src/test/kotlin/io/spine/server/delivery/` and extended
`AbstractDeliveryTest`. Each defined its own copy of:

| Helper | `Repeated…` | `EmptyRepository…` | `InactiveInstances…` |
|---|---|---|---|
| `repository = CounterView.Repository()` | yes | yes | yes |
| `failures` | `Throwable` | `String` | `String` |
| the recording `Delivery` | field, 1 shard, `@BeforeEach` | `useDelivery()` | `useDelivery()` |
| caught in the observer wrapper | `RuntimeException` | `Exception` | `Exception` |
| `DeliveryMonitor` recording receptor failures | no | yes | yes |
| `jobStatuses()` reads | own `delivery` | `ServerEnvironment` | `ServerEnvironment` |
| `totals(...)` | `vararg ids` | fixed 3 IDs | fixed 4 IDs |
| `undelivered()` | yes | yes | yes |
| `emitHistory()`, `PAST_TURBULENCE_MILLIS` | 2 fixed IDs | — (`appendHistory()`) | `vararg ids` |
| weight reset to 1 | `@AfterEach`, in `emitHistory()` | `@BeforeEach` | `@BeforeEach` |

`emitHistory()` was the same in the two copies (`Repeated…` and `InactiveInstances…`): it
posts a `NumberAdded` with `value` 0 to each ID, `EVENTS_PER_TARGET` times, takes `since`
a minute back, then pauses for 1 s, past the 500 ms turbulence period of `CatchUpProcess`
(a private constant there).

`CatchUpJobsPerPageIgTest` of #1686, not merged, has a fourth, customized copy: a watchdog
monitor and a hooked observer. Out of scope; see "Follow-ups".

## Plan

1. New `server/src/testFixtures/kotlin/io/spine/server/delivery/AbstractCatchUpIgTest.kt`:
   `abstract class AbstractCatchUpIgTest : AbstractDeliveryTest()`.
   The class is public, as `testing.md` asks of an abstract base used from other modules;
   its members are `protected`, the narrowest visibility that works for such a base.
   - `repository: CounterView.Repository`;
   - `failures: List<String>`, read-only, backed by a private `CopyOnWriteArrayList`;
   - `useDelivery(strategy)`: the `Delivery` with the strategy, a `Durations.ZERO`
     deduplication window, a private inner `FailureRecorder : DeliveryMonitor` for
     `onReceptionFailure()`, and the `LocalDispatchingObserver` wrapper that catches
     `Exception`, which `Delivery.onNewMessage()` logs and swallows, records
     `"Delivery run: $e"`, and rethrows; then `under<Tests> { use(delivery) }`;
   - `jobStatuses()`: reads the catch-up storage of the `Delivery` installed in
     `ServerEnvironment`;
   - `totals(vararg ids)`, `undelivered()`;
   - `emitHistory(context, eventsPerTarget, vararg ids): Timestamp` and a private
     `PAST_TURBULENCE_MILLIS`;
   - `restoreDefaultWeight()`, one method, annotated `@BeforeEach` and `@AfterEach`, that
     restores the weight to 1: the union of what the tests did. It runs before a test,
     because other suites leave the weight changed, and after it, because
     `RepeatedCatchUpIgTest` leaves it at 100. Its name must not be `setUp` or `tearDown`,
     which would override those of `AbstractDeliveryTest` and drop them.
   KDoc with the review-docs wording: "Records the receptor failures, which `Delivery`
   does not throw"; "`DeliveryMonitor` marks a message as delivered even when its receptor
   failed"; "A pause longer than the 500 ms turbulence period of a catch-up".
2. The three tests extend `AbstractCatchUpIgTest` and drop their copies. Test bodies and
   assertions stay; the call sites change only as follows:
   - `RepeatedCatchUpIgTest`: `@BeforeEach useSingleShard()` calls
     `useDelivery(UniformAcrossAllShards.singleShard())`; `emitHistory(context)` becomes
     `emitHistory(context, EVENTS_PER_TARGET, FIRST, SECOND)`; the `@AfterEach` reset and
     the reset inside `emitHistory()` go, as the base does both.
   - `EmptyRepositoryCatchUpIgTest`: `totals()` becomes `totals(FIRST, SECOND, THIRD)`.
     `appendHistory()`, `ThirdApart`, `EmitLiveEventWhenFinalizing` stay.
   - `InactiveInstancesCatchUpIgTest`: `emitHistory(context, ...)` gains
     `EVENTS_PER_TARGET`; `totals()` becomes `totals(*ALL_IDS.toTypedArray())`, so
     `ALL_IDS` stays an immutable `List`. `changeStored()` and `inactive()` stay.
   - The IDs, `EVENTS_PER_TARGET` (10 in one test, 5 in the others), and `WITH_SHARDS`
     stay in each test: they are its scenario, not infrastructure.
3. Two deliberate changes to `RepeatedCatchUpIgTest`, both stricter: it now records the
   receptor failures too, and it catches `Exception` and keeps strings, like the other two.
   `TargetDelivery` passes a failure of any receptor, the `CatchUpProcess` handlers
   included, to the monitor. So whether the test still passes was a hypothesis for the run
   to check, not a given. It held: no receptor failure was recorded.
4. Verify: the counts of the three suites and of `:server:test` before and after;
   `./gradlew build dokkaGenerate` after the bump below, so that the build does not
   overwrite the `.566` of #1688 in Maven Local; reviewers.
5. Version: `bump-version` with `BASE=catch-up-all-resets-inactive`, `.566` → `.567`;
   a commit for the bump and one for the dependency reports, as the skill authorizes.
   The refactoring itself stays uncommitted until the user asks.

## Verification

`:server:cleanTest :server:test --no-build-cache`, executed, not restored from the cache:

| Run | Gradle summary | XML reports | `Repeated…` | `Empty…` | `Inactive…` |
|---|---|---|---|---|---|
| Before, `4fd47fc82e4` | 2115, all passed | 2116, 1 skipped | 2 | 5 | 5 |
| After | 2115, all passed | 2116, 1 skipped | 2 | 5 | 5 |

`./gradlew clean build dokkaGenerate` at `.567`, after the bump: green; `:server:test`
executed 2115 tests again, all passed; `:server:detekt` and `:server:dokkaGenerate` ran.

## Reviews

- Design review, by a planning agent, before the code: sound. It corrected the reason
  for `protected`: the known Fir2Ir crash needs a Java class between the declaring
  class and the caller, which is not the case here. Its other points: name the lifecycle
  method apart from `setUp`/`tearDown`, and treat step 3 as a hypothesis.
- `spine-code-review`: approved. Its nit, `totals(*ALL_IDS.toTypedArray())` at three call
  sites, is kept: an array constant would trade the immutable `List` for one fewer copy.
- `kotlin-engineer`: approved. Applied: `restoreDefaultWeight()` is `protected`, so the
  base has no public member. Same `ALL_IDS` nit as above.
- `review-docs`: approved with changes. Applied: the runt and the "which" antecedents in
  the KDoc of the base, `emitHistory` in its class KDoc, and the table and wording of this
  file. Not added: a usage snippet in the KDoc of the base, since the three tests show its
  use.

## Follow-ups

- Once #1686 merges, `CatchUpJobsPerPageIgTest` can take `jobStatuses()`, `totals()`,
  `undelivered()`, and the turbulence pause from the base; its watchdog and hooked
  observer stay in that test.
- On merge, delete this file and the follow-up entries it closes in the two task files.
