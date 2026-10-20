# Catch-up when several repositories share the `Delivery`

Related branches, which edit the same classes:
`claude/funny-shannon-456685` (catch-up of an empty repository) and
`claude/dazzling-noyce-92ab0c` (repeated catch-ups). See "Coordination".

## Status

- [x] Code read; the suspected bug traced statically — see "What the code says"
- [x] Reproduction: `SeveralRepositoriesCatchUpIgTest`, 6 cases, run against the unchanged
      `master` (`7b64d415975`): 4 fail, the 2 controls pass — see "Reproduction"
- [x] Diagnostic of the `FINALIZING` hang (a temporary test, deleted after the run)
- [x] Plan reviewed by a plan agent against the code; its valid findings folded in
- [x] Plan approved on 2026-10-06
- [x] Fix — see "Plan"
- [x] Tests extended — see "Tests"
- [x] Mutation checks: breaking any of 5 guards of the fix fails a case — see "What was run"
- [x] Verification: `./gradlew clean build dokkaGenerate` is green — see "What was run"
- [x] Version `2.0.0-SNAPSHOT.565`; the dependency reports regenerated
- [x] Reviews by `review-docs`, `kotlin-engineer` and `spine-code-review`: no must-fix;
      the valid findings applied, then the build re-run
- [ ] Commit, push, PR — waiting for the owner's authorization

## Problem

`InboxDeliveries.register()` keeps one delivery per inbox type URL
(`contents.put(entityType.value(), inbox.delivery())`), so the inbox registered last wins.
That suits entity repositories: there is one per entity type in a JVM. But the framework
creates two internal reactors many times over, each time with the same state type:

| Reactor | Created | Inbox type |
|---|---|---|
| `CatchUpProcess` | per projection repository (`ProjectionRepository.initCatchUp()`) | `CatchUp` |
| `ShardMaintenanceProcess` | per Bounded Context (`Delivery.registerDispatchersIn()`) | `ShardMaintenance` |

So `Delivery` hands the messages of every catch-up job to the `CatchUpProcess` created
last, and every shard maintenance request to the `ShardMaintenanceProcess` of the context
built last. Their collaborators belong to their own repository or context:
`dispatchOperation`, `eventStore`, and the event bus that receives their reactions.

## What the code says

The suspected chain, checked step by step:

1. Each `ProjectionRepository` creates its own `CatchUpProcess` with
   `setDispatchOp(::sendToCatchingUp)` — **holds**.
2. `AbstractStatefulReactor` builds an `Inbox` for `CatchUp` in its constructor, with
   `EventEndpoint::new` bound to that very instance — **holds**.
3. `Inbox.Builder.build()` → `Delivery.register()` → `InboxDeliveries.register()` keys by
   the type URL only, so the last `CatchUpProcess` receives all `CatchUp` messages — **holds**.
4. That instance resolves the right repository through `CatchUpRepositories`, but
   `dispatchAll()` uses its own `dispatchOperation` — **holds**: the `CatchUpStarted`
   signals of the first repository go to the inbox of the second one.
5. "Possibly deleting the second repository's records" — **does not happen**.
   `CatchUpStation` matches a message to a job by the inbox type
   (`CatchUpMixin.matches()`). The misdirected signals sit in the inbox of the other
   projection type, so no job matches them. They are neither dispatched nor deleted.
   Instead, no `EntityPreparedForCatchUp` ever arrives, and the job stays `STARTED`.
6. "The history read from the wrong `EventStore`" — true of the code (`readMore()` uses
   its own `eventStore`), but **not reached** on `master`: the job never leaves `STARTED`.

What a job stuck in `STARTED` does: `CatchUpStation.JobFilter.started()` removes every
live `TO_DELIVER` message to the targeted instances. So the live events of those
instances are **lost** for as long as the job lives. It never ends, and
`CatchUpStarter.checkNotActive()` refuses any further catch-up of the type
(the last point is inferred from the code, not run).

**An extra finding of the same cause.** A `ShardMaintenanceProcess` reacts to
`ShardProcessingRequested` with `ShardProcessed`, posted to the bus of its own context —
the context built last. If that context has no projection repository, nothing there
dispatches `ShardProcessed`, and the job stays `FINALIZING` for good — even with only
**one** projection repository in the JVM. The live events held back while it finalizes are never
delivered. The diagnostic recorded `ShardProcessingRequested` in the context of
the projection and `ShardProcessed` in the empty context built after it.

## Reproduction

`server/src/test/kotlin/io/spine/server/delivery/SeveralRepositoriesCatchUpIgTest.kt`,
with the fixtures in `given/Tallies.kt`: `FirstTally` (`DCounter`) and `SecondTally`
(`ConsecutiveNumberView`). Both count `NumberAdded` by calculator, each with a weight
of its own, so their instances share IDs. One shard, synchronous delivery. Failures are
recorded through `DeliveryMonitor.onReceptionFailure()` and a wrapping shard observer.
History events are posted live; then the test waits past the 500 ms turbulence.

Each case emits 3 events per ID at weight 1, sets the weight of the projection
under catch-up to 100, catches it up, and posts one more live event.

| Case | Contexts, in build order | Result on `master` |
|---|---|---|
| repository registered last | one: `first`, `second`; catch up `second` | passes |
| repository registered before another | one: `first`, `second`; catch up `first` | job `STARTED`; totals `{3, 3}`, expected `{400, 300}`; 2 `CatchUpStarted` stranded in the `ConsecutiveNumberView` inbox |
| selected instances, same order | as above; catch up `first` for `alpha` | job `STARTED`; `alpha` 3, expected 300; 1 stranded `CatchUpStarted` |
| context built before another | `First` [`first`], `Second` [`second`] | as the second row |
| empty context built before | `Empty`, `First` [`first`] | passes |
| empty context built after | `First` [`first`], `Empty` | job `FINALIZING`; the live event held as `TO_CATCH_UP` |

The records of the second repository are intact in every case. No failure reached
the monitor or the observer: the jobs hang silently.

## Plan

Approved on 2026-10-06. Deliver each message to the reactor that owns its catch-up job,
so that the job runs on its own repository's process, in its own context. The owner of
a `CatchUp` message is the process of `CatchUpId.projection_type`. The owner of a shard
maintenance request is the maintenance process of that same catch-up process.

1. **`AbstractStatefulReactor`** (`server/.../event/AbstractStatefulReactor.java`).
   - An `@Internal` protected hook, `reactorFor(I targetId)`. Returns `this` by default,
     and may throw `IllegalStateException` when no reactor serves the target.
   - `EventEndpoint.dispatchTo()` keeps holding its own `endpointLock`. With one inbox per
     state type, that stays one lock for all catch-up messages, as now. A lock per owner
     could deadlock: a receptor delivers nested runs of other shards synchronously while
     it holds the lock.
   - Under the lock, it loads the state, reacts and flushes on the reactor the hook
     returns. The body moves to a private method of the reactor, with `loadIntoBuilder()`.
   - If the hook throws, the endpoint returns an error outcome and logs a warning, as
     `AbstractMessageEndpoint.onFailedDispatch()` does. The `DeliveryMonitor` gets
     a failed reception, and the page goes on. A job never runs on a process that does
     not own it.
2. **`ProjectionRepository`**. `initCatchUp()` publishes the process (now `@Volatile`,
   nullable) only after `registerEventDispatcher()` returns. So no message reaches
   the process before it is on the buses of its context. An `internal` accessor,
   `catchUpProcess()`, with `@JvmName`, as `eventStore()` does.
3. **`CatchUpProcess`**.
   - A static `processOf(CatchUpId)`: from `CatchUpRepositories`, which stays unchanged,
     to the repository, then to its process. Throws `IllegalStateException` when the type
     has no repository, its process is not registered yet, or the repository is closed.
   - `reactorFor(CatchUpId)` returns `processOf(id)`.
   - Owns a `ShardMaintenanceProcess` for the repository's state type. Creates it in
     the constructor, and registers it in its own context in `registerWith()`.
4. **`ShardMaintenanceProcess`**: one per catch-up process, instead of one per context.
   - `canDispatch()` accepts only the requests of its own catch-up process. The buses
     honor it (`DispatcherRegistry.java:127`), so each request is still stored once.
   - The inbox endpoint hands each request to the maintenance process of the job's
     catch-up process, which reacts to it, so `ShardProcessed` is posted in the job's
     context. When no process serves the job, the endpoint returns an error outcome and
     logs a warning.
5. **`Delivery.registerDispatchersIn()`** (`@Internal`) and its call in
   `BoundedContextBuilder.build()` are removed: a context without projections needs no
   maintenance process. The "Shard maintenance" section of the `Delivery` Javadoc is
   rewritten in prose, without naming package-private classes.
6. **`delivery.proto`**: the comment of `ShardMaintenance` no longer says it is
   represented in each of the Bounded Contexts. A proto change, so verification
   runs `clean`.

Considered and rejected:

- Keeping the last instance as the receiver and resolving its collaborators by the job.
  That still posts the reactions of a job into the context of another repository, and
  it fixes nothing for the shard maintenance.
- Falling back to `this` when no process serves the job. That runs the job on a process
  of another repository — the defect itself.

## Tests

`SeveralRepositoriesCatchUpIgTest`, 20 cases. Each of the first 8 runs with 1 and 3 shards;
several shards produce several maintenance requests per job.

- The 6 cases of the reproduction.
- Both repositories of one context caught up, one after the other.
- The signals of a catch-up are posted only in the context of its repository: a witness
  reactor in each context records `ShardProcessed` and `CatchUpCompleted`.
- A shard maintenance request is stored once in a context of several repositories.
- Failed receptions, with the other messages of the run delivered:
  - a catch-up signal of a projection type no repository serves;
  - a catch-up signal of a repository known to the `Delivery`, but not registered yet;
  - a shard maintenance request of a closed repository.

Elsewhere:

- `AbstractStatefulReactorSpec` (new): the default `reactorFor()` dispatches to the reactor
  itself.
- `ProjectionRepositoryCatchUpSpec`: a catch-up of a repository not registered in
  a Bounded Context is rejected.

## What was run

Mutation checks. Each mutation broke one guard of the fix, the spec ran, and the file was
restored from a copy:

| Mutation | Failed cases |
|---|---|
| `CatchUpProcess.reactorFor()` returns `this` | 11 of 19 |
| the shard maintenance endpoint reacts on itself | 3: the witness case, the closed repository |
| no projection type filter in `ShardMaintenanceProcess.canDispatch()` | 1: stored once |
| `processOf()` ignores a closed repository | 1: the closed repository |
| the endpoint lets the hook's exception through | 1: the unknown projection type |

Verification, with Corretto 17, after the reviews:

- `./gradlew clean build dokkaGenerate`: green. 2689 tests in 7 modules, 0 failures; the one
  skipped test is `@Disabled` on `master` too. `:server:test` executed, not restored from
  the cache.
- Kover: the 64 executable lines this change adds or modifies in `server` are all covered.
  The only partial branch is the `instanceof` guard of
  `ShardMaintenanceProcess.canDispatch()`, which is unreachable: the dispatcher receives
  shard processing requests only.

## Coordination

- `CatchUpProcess.java` is edited on all three branches. Here, only the fields and
  the constructor (lines ~291–305) and `registerWith()` (~313–321) change, and the new
  members are added near `route()` (~782). The other branches edit
  `handle(CatchUpRequested)`, `handle(EntityPreparedForCatchUp)`, the class Javadoc
  and the area after `recordAffectedShards()`. The copyright header is rewritten
  the same way on all three.
- `CatchUpStation.java` is not touched here.
- `funny-shannon` reads the history right away when no instance is caught up; after this
  fix, that read runs on the job's own process too.
- Version: `master` is at `.563`, and `dazzling-noyce` bumps to `.564`. This branch is at
  `.565`, and bumps again if a sibling merges a greater or equal version first.

## Observations, not in this change

- Each `CatchUpProcess` of a context stores every catch-up signal posted there. With N
  projection repositories, that is N copies of a signal, and N − 1 are dropped as
  duplicates. Before commit `638a43fb900` (2021), `canDispatch()` accepted only
  the signals of the process's own projection type; that commit dropped the filter, as
  the process created last had to take every job. With the routing of this change,
  the filter would be safe again. Left out: the duplication was not reproduced as
  a failure.
- A persistent storage may keep the `STARTED` jobs and the stranded `CatchUpStarted`
  signals of this defect after the upgrade. The signals sit in the inbox of the other
  projection type, so a later catch-up of that type would match and dispatch them.
  This is inferred from `CatchUpStation`, not run. It needs a release note.
- Leftover `ShardMaintenance` messages in a JVM without projection repositories now fail
  their page: no inbox serves that type any more, and `InboxDeliveries.get()` throws
  outside the `try` of `GroupByTargetAndDeliver.executeFor()`.
