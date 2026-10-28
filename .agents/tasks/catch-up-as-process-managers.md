# Catch-up and shard maintenance as process managers

The second of two PRs that replace PR #1694. The first one, "the check", forbids serving one
entity state type in two domain Bounded Contexts (see "Prerequisite").

All line numbers below are on `master` at `39ef520dc16`.

## Status

- [x] Defects reproduced on `master`. See "Problem", and `catch-up-of-several-repositories.md`
      on the branch of #1694.
- [x] Design agreed in chat with the owner and armiol on 2026-10-23. See "Decisions".
- [x] Facts the design rests on checked in the code, or by tests on a copy of `master`.
      See "Checked facts".
- [x] Work moved to the branch `claude/hopeful-hawking-244608`, which starts at `origin/master`
      `39ef520dc16`. The task file and the two checks are copied from the branch of #1694
      (2026-10-23).
- [ ] The check PR merged (it is a prerequisite).
      - 2026-10-23: not open yet. Its session runs in the worktree `awesome-joliot-2f6ff3`, with
        no commits so far. The names of its record and lookup are not known yet.
      - Later on 2026-10-23: opened as #1698 (`.578`). On the owner's request, this branch is
        stacked on it: fast-forwarded to `origin/claude/awesome-joliot-2f6ff3` at `1f5535b0328`.
      - 2026-10-26: #1698 merged (`2e597e36bf0`). On 2026-10-27, on the owner's request, this
        branch was fast-forwarded to `origin/master` at that commit. The three files that both
        sides changed were re-applied by hand: `ServedStateTypes` is Kotlin now.
      - Later on 2026-10-27, on the owner's request, this branch is stacked on #1701
        (`claude/one-context-per-name`, `.600`). It got #1701 by a merge, on 2026-10-28, after
        #1700 merged into `master` and GitHub rebased #1701. #1700 turns the check on in tests
        too, and #1701 allows one open domain context per name. So the lookup moved to
        `OpenContexts` and returns one context or `null`, and the test of two contexts that
        serve one projection type is gone: they cannot be open.
- [ ] PR #1694 closed without merging. Its team memory correction moves to the check PR.
- [ ] Plan mode: the implementation plan approved by the owner (CLAUDE.md gate).
- [ ] Implementation. See "Plan".
      - 2026-10-23: main code done, uncommitted. `:server:test` passes (2171 tests, 0 failures)
        with the existing suites adapted. Deviations from the plan:
        - `ShardMaintenance` moved with `ShardMaintenanceId` into `shard_maintenance.proto`:
          an ID-only file would make an import cycle with `delivery.proto`.
        - `writeJob()` lives in the test fixtures (`TestCatchUpJobs`), not in `CatchUpStorage`.
        - `CatchUpEventFactory` is removed: nothing uses it any more.
        - `CatchUpRequested` was posted at once, even with parallel posting, until
          2026-10-28. See item 9 of "Found during planning".
        - `CatchUpRepository` and `ShardMaintenanceRepository` are Kotlin, per the team rule
          from #1698 (`feedback_new_code_in_kotlin.md`): new classes are Kotlin.
      - 2026-10-27, after the merge: `build dokkaGenerate` ran all tests green
        (`:server:test` 2309, 0 failures); detekt flagged one long line, since fixed.
- [ ] Tests. See "Tests".
      - 2026-10-23: written, all green. `ParallelCatchUpsIgTest` (the matrix, 109 cases,
        about 2 min), `CatchUpRoutingIgTest`, `SeveralRepositoriesCatchUpIgTest` (the scenarios
        of #1694, on the new `Tally*` fixtures), `CatchUpEventsIgTest` (replaces
        `CatchUpEventsStoredProbeTest`), `InboxDeliveriesSpec`, and tenant cases in
        `CatchUpJobsSpec`. `ProposalChecksProbeTest` is deleted.
      - The matrix delivers one page per run, so the jobs of all shards interleave
        (`AbstractCatchUpIgTest.useDelivery(onePagePerRun = true)`). With whole shards per
        run, 13 cases had no overlap of the jobs.
- [ ] Docs and release notes. See "Release notes".
      - 2026-10-23: the guide is `docs/migration/catch-up-in-system-contexts.md`.
      - 2026-10-27: three reviews (`spine-code-review`, `kotlin-engineer`, `review-docs`)
        applied. Changes beyond wording:
        - `DeliveryBuilder.setCatchUpStorage()` rejects a multi-tenant storage.
        - The lookup is `ServerEnvironment.findContextServing()`, to tell it apart from
          `contextsServing()`.
        - `DeliveryStrategy.determineIndex()` no longer accepts a shard-only ID: it runs only
          when a message is written, and nothing writes that form any more. This replaces
          "both forms of the ID" in the plan.
        - The tests close their contexts even if one close fails; the tallies are read
          through typed accessors, not descriptors.
      - **Open, for the owner to decide:**
        1. *Old job records.* Decided on 2026-10-27: the guide tells users to delete
           the job records before upgrading, and the storage keeps its context name.
           - Why it matters: the new job storage has the same context name
             (`__System_Delivery__`) and source type (`CatchUp`) as the old one, so
             a database storage likely uses the same table. Completed jobs are never deleted,
             so the `Delivery` would read old `CatchUp` messages as entity records and fail.
           - The alternative, not taken: give the new storage another context name, so that
             the old records are never read.
        2. *`CatchUpCompleted` is `@Internal`* (`internal_all`), but the KDoc of
           `ProjectionRepository.catchUp()` and the guide named it as the event to subscribe
           to. Decided on 2026-10-27: it stays internal. The KDoc and the guide now describe
           the completion event in prose.
        3. *The class Javadoc of `CatchUpProcess`* still names many internal events, as it
           did before. The new sentences name none. A full prose pass is a task of its own.
- [ ] Pre-PR checks, then the PR.

## Problem

- **The type alone picks the inbox.** The `Delivery` keeps one inbox per entity state type
  for the whole JVM, and the inbox registered last wins (`InboxDeliveries.java:70-73`). The
  address of a message is the type plus the entity ID, with no Bounded Context
  (`inbox.proto:49-56`).
- **The framework creates the catch-up components many times over:**
  - a `CatchUpProcess` for each projection repository (`ProjectionRepository.kt:118-123`);
  - a `ShardMaintenanceProcess` for each Bounded Context (`BoundedContextBuilder.java:583-585`).
- **So the instance created last handles every message of its type.** Catch-ups of
  a repository built earlier stay in `STARTED`, and the live events of their targets are lost.
  A job stays in `FINALIZING` for good when the context built last has no projections.
- **Two contexts with the same aggregate type mix their commands silently.** This is
  `SameTypeInTwoContextsProbeTest`, run on `master`. The check PR forbids this outside tests.
- **Tenancy.** A job does its work in one tenant, but it matches messages of every tenant: the
  match uses only the type and the IDs (`CatchUpMixin.java:46-60`). So a job of tenant A can
  drop or hold back live messages of tenant B. A running job of tenant A also blocks a new
  catch-up of the same projection in tenant B (`CatchUpStarter.java:134-143`).

## Decisions

Agreed in chat on 2026-10-23.

1. **Two PRs.** The check first, then this one.
2. **Proper process managers.** `CatchUpProcess` and `ShardMaintenanceProcess` become process
   managers with their own repositories.
3. **They live in the System contexts ("Place 2").** Every System context registers one
   repository of each.
4. **The exemption rule.** The check counts only domain contexts. Only the framework adds
   repositories to a System context, and each System context belongs to one domain context.
5. **Hidden from queries in two ways.** The `Server` never exposes System contexts, and both
   states have `NONE` visibility (the default for process managers).
6. **No Bounded Context in `InboxId`.** With the check, the type of a domain entity already
   tells its context, so a context field would repeat it. Data stored twice can disagree.
   Example: messages wait in an inbox during an upgrade that moves the type to another
   context.
7. **The address of the two catch-up types is found from the job.** The job's projection type
   leads to its domain context, and from there to the System context. See "Design / 3".
8. **The new ID of `ShardMaintenance` is (shard, job).** Proposed in chat, with no objection.
   See "Design / 2".
9. **Events are stored nowhere by default.** Users who need an audit trail turn on
   `persistEvents()` for the System context, which stores all of its system events too. In
   the check test, that was 24 system events for 6 domain events. Today the catch-up's events
   are in the domain event store, so this is a change for the release notes.
10. **`AbstractStatefulReactor` is removed**, together with `CatchUpRepositories`.
11. **Tests cover the matrix the owner asked for.** See "Tests / New scenarios".
12. **No conversion of old job records.** Records written before the upgrade are left as they
    are. The owner decided this on 2026-10-23.

## Prerequisite: the check PR

This PR uses three things from the check PR:

- **A record in `ServerEnvironment`** of which open domain context serves which state type.
  Contexts are added when they register repositories, at build time and through
  `internalAccess().register()`. They are removed when they close.
- **A lookup: "which open domain context serves type T?"** Here it gives the context of
  a job's projection type. In tests, where the check is off, two open contexts may serve one
  type. Then the lookup must say so, and the callers fail with an error that names both
  contexts.
- **The exemption rule:** System contexts are not counted.

## Design

### 1. Where everything lives

| Where | What |
|---|---|
| `ServerEnvironment` | The record from the check PR. |
| `Delivery` (one per JVM) | All inboxes; one storage for all catch-up jobs; the inbox rule for the two catch-up types. |
| A domain context, e.g. `Tasks` | Projection repositories, the event store, user code. No catch-up component. |
| Its System context, `Tasks_System` | A repository of `CatchUp` process managers and a repository of `ShardMaintenance` process managers. |

Every System context registers both repositories, the way every domain context creates
a `ShardMaintenanceProcess` today. `Delivery.registerDispatchersIn()` goes away.

### 2. `ShardMaintenance` gets a new ID

**Why the old ID fails.** Jobs `job-T` (in `Tasks`) and `job-B` (in `Billing`) both ask
for shard 1 to be processed. With the shard as the ID, both requests have the address
"`ShardMaintenance`, ID shard 1". There are two `ShardMaintenance` inboxes in Place 2, and
the address does not say which one is meant.

```proto
// delivery.proto, which already imports catch_up.proto.
message ShardMaintenanceId {

    // The shard to process.
    ShardIndex shard = 1 [(required) = true, (validate) = true];

    // The catch-up job that asked for the processing.
    catchup.CatchUpId job = 2 [(required) = true];
}

message ShardMaintenance {
    ShardMaintenanceId id = 1;
}
```

- **The shard placement stays.** `DeliveryStrategy.determineIndex()` takes the shard from inside
  the ID instead of treating the ID as the shard (`DeliveryStrategy.java:63-68`). A delivered
  request still means that everything earlier in its shard has been delivered. The method
  must also handle the old form of the ID, which is only the shard (see "Release notes").
- **The answer reaches the right job.** `ShardProcessed` is posted on the bus of the System
  context that handled the request, which is the job's System context. The old bug, an
  answer posted in the context built last, cannot happen.
- **Many small instances.** There is one instance per job and shard, holding only the ID, as
  today.
- **Routing.** A custom event route builds the ID from the request's `index` and `process`
  fields. The default route uses the first field.
- **The station that stamps the run info needs no change.** It finds requests by their event
  type (`UpdateShardProcessingEvents.java:52-63`).
- **Rejected shapes:**
  - (shard, projection type) works, but adds a second way of finding the context;
  - (shard, context name) is the context in the address, rejected in decision 6.

### 3. How the `Delivery` finds the inbox

Domain types are unchanged: the type alone picks the inbox. The two catch-up types get one
more rule:

```java
ShardedMessageDelivery<InboxMessage> get(InboxMessage message) {
    var type = message.getInboxId().getTypeUrl();
    if (!isCatchUpType(type)) {                        // every domain type
        return byType.get(type);                       // as today
    }
    var job = jobIn(message.getInboxId());             // the `CatchUpId`, or the job inside `ShardMaintenanceId`
    var domain = contexts.serving(job.getProjectionType());   // the lookup from the check PR
    return bySystemContext.get(type, domain.toSystem());
}
```

- **Registration.** The two catch-up repositories register their inboxes together with the
  name of their System context. Every other repository registers by type only, as today.
  Unregistering on close follows the same keys.
- **The System context's name.** `BoundedContextName.toSystem()` already exists
  (`BoundedContextNameMixin.java:55-57`).
- **No open context serves the projection type.** This happens, for example, when the context
  closed while messages were waiting. The run fails with an error that names the type, as it
  does today for any type without an inbox (`InboxDeliveries.java:48-53`).
- **Two open contexts serve the projection type.** This is possible only in tests. The run
  fails with an error that names both contexts.

### 4. The path of one catch-up

`job-T` catches up `TaskView` in `Tasks`. `job-B` takes the same path through
`Billing_System` at the same time, and the two never meet.

| # | Message | Posted by | Bus | Address in the inbox |
|---|---|---|---|---|
| 1 | `CatchUpRequested` | `TaskView` repository, in `catchUp()` | `Tasks_System`, through the system write side | `CatchUp` / `job-T` |
| 2 | `CatchUpStarted` | the process, after `STARTED` is saved | `Tasks_System` | `CatchUp` / `job-T` |
| 3 | start signal | the process, reacting to step 2 | none, straight into the inbox | `TaskView` / instance |
| 4 | `EntityPreparedForCatchUp` | each instance, as today | `Tasks_System` | `CatchUp` / `job-T` |
| 5 | history events | the process, reading the event store of `Tasks` | none, straight into the inbox | `TaskView` / instance |
| 6 | `HistoryEventsRecalled`, `HistoryFullyRecalled`, `FinalizationStarted`, `LiveEventsPickedUp` | the process | `Tasks_System` | `CatchUp` / `job-T` |
| 7 | `ShardProcessingRequested` | the process | `Tasks_System` | `ShardMaintenance` / (shard 1, `job-T`), in shard 1 |
| 8 | `ShardProcessed` | `ShardMaintenance` (shard 1, `job-T`) | `Tasks_System` | `CatchUp` / `job-T` |
| 9 | `CatchUpCompleted` | the process | `Tasks_System`; subscribers in `Tasks` get it too | none |

Notes on the steps:

- **Step 1.** `CatchUpStarter` posts through `SystemWriteSide.postEvent()`
  (`SystemWriteSide.java:46-60`), the same path the reports of step 4 take today
  (`EntityLifecycle.java:395-402`).
  - In production this posts on another thread (`DefaultSystemWriteSide.java:61-75`).
  - That is harmless: the job is created when the request is delivered, as it is today.
- **Steps 3 and 5.** A System context has no link to its domain context
  (`SystemContext.java:53-55`). So the process uses the lookup from the check PR to find the
  `TaskView` repository and the event store of `Tasks`.
  - Today it uses the event store of the context it is registered in
    (`CatchUpProcess.java:310-318`).
  - The start signals and history events still go through the repository's
    `sendToCatchingUp()`.
- **Step 9.** Subscribers of the domain context get `CatchUpCompleted`, because every
  dispatcher of a domain context is also registered on its System bus
  (`BoundedContext.java:277-286`). That includes entity repositories
  (`EventDispatchingRepository.java:83`).
- **Projections ignore these events.** Posting `CatchUpStarted` on that bus does not reach
  projections as an ordinary event, because a projection repository dispatches only the
  events its projections subscribe to (`ProjectionRepository.kt:248-249`).

### 5. Jobs: one storage, with the tenant

- **One storage for every job.** The `Delivery` creates it, as it creates `CatchUpStorage`
  today (`DeliveryBuilder.java:439`). It holds the jobs of every System context and every
  tenant. Each `CatchUp` repository overrides `createStorage()` to use it
  (`RecordBasedRepository.java:207-212`).
- **Why one storage.** The `Delivery` reads all jobs once per page, and a page holds messages
  of every context and tenant (`Delivery.java:580-587`). A storage per tenant cannot do that:
  read with no tenant set, it fails (see "Checked facts").
- **The format changes.** Job records become entity records. Today `CatchUpStorage` is
  a `MessageStorage<CatchUpId, CatchUp>` and a public SPI (`CatchUpStorage.java:44-63`). This
  also affects `DeliveryBuilder.setCatchUpStorage()` (`DeliveryBuilder.java:382`). See
  "Release notes".
- **`CatchUp` gets a `tenant` field,** set when the request is handled.
  - Job IDs are random UUIDs (`CatchUpStarter.java:101-104`), so two tenants never share
    a job. The tenant is needed for matching, not for uniqueness.
  - `CatchUpMixin.matches()` and the index in `CatchUpJobs` compare the job's tenant with
    `InboxMessageMixin.tenant()` (`InboxMessageMixin.java:50-53`).
  - `CatchUpStarter.checkNotActive()` looks only at the jobs of the current tenant.

### 6. Saving the status at the right time

A process manager saves its state after the handler, and then posts the handler's events
(`PmEndpoint.kt:62-75`). When the `Delivery` gives it several messages in a row, it saves
once, after the last of them (`TargetDelivery.java:238`).

Today three steps save the status in the middle of a handler: `flushState()` at
`CatchUpProcess.java:399`, `541` and `653`. Each of these becomes two handlers of the same
process, connected by an event the process emits for itself:

| Status saved first | Handler 1 reacts to | Handler 1 emits | Handler 2 then |
|---|---|---|---|
| `STARTED` | `CatchUpRequested` | `CatchUpStarted` | sends the start signal to the instances; sets `instances_to_clear` |
| `FINALIZING` | `HistoryFullyRecalled` | `FinalizationStarted` (new) | reads the rest of the history, sends it, and emits `LiveEventsPickedUp` |
| `COMPLETED` | the last `ShardProcessed` | `AllShardsFinalized` (new) | emits `CatchUpCompleted` |

Why each save matters:

- **`FINALIZING`.** A live event that arrives after the final read must not be dropped.
  1. While a job is `IN_PROGRESS`, the `Delivery` drops live events for its targets
     (`CatchUpStation.java:231-232`).
  2. While it is `FINALIZING`, the `Delivery` holds them back instead
     (`CatchUpStation.java:254-258`).
  3. If the status were saved after the read, an event arriving between the read and the
     save would be dropped and never replayed.
- **`STARTED`.** Without it, a delivery run could find a start signal for a job it cannot
  see yet. Such a signal waits in the inbox.
- **`COMPLETED`.** A user may start the next catch-up as soon as `CatchUpCompleted` arrives.
  That call fails with `CatchUpAlreadyStartedException` if the storage still says
  `FINALIZING`.

`CatchUpProcess` already uses this pattern with `HistoryEventsRecalled` (lines 467-474),
`LiveEventsPickedUp` (576-582) and `CatchUpCompleted` (642-649). So does the test process
manager `QuizProcess`. The status guards against repeated signals stay as they are, from
#1692 and #1695.

### 7. What is removed

- `AbstractStatefulReactor`, and with it `flushState()`.
- `CatchUpRepositories`, replaced by the lookup from the check PR.
- `ProjectionRepository.initCatchUp()` and `Delivery.newCatchUpProcess()`, together with the
  builder of the old process.
- `Delivery.registerDispatchersIn()`.

## Plan

This is the order inside the PR. The plan gate applies before step 1.

1. **Branch.** Create it from `master` after the check PR merges. Bump the version.
2. **Protos.**
   - `ShardMaintenanceId`, and the new type of `ShardMaintenance.id`.
   - `CatchUp.tenant`.
   - The events `FinalizationStarted` and `AllShardsFinalized`, as `CatchUpSignal`s.
3. **`Delivery`.**
   - The inbox rule and the registration by System context (Design / 3).
   - `determineIndex()` for both forms of the ID.
4. **Job storage.**
   - The shared storage, created by the `Delivery`.
   - The `Delivery` and `CatchUpStarter` read the jobs from it.
   - Replace `setCatchUpStorage()`.
5. **The `CatchUp` process manager and its repository.**
   - Convert `CatchUpProcess`, keeping its handlers and guards.
   - Split the three steps (Design / 6).
   - Add the routing, including `ShardProcessed` by its `process` field.
   - Find the domain context through the lookup.
6. **The `ShardMaintenance` process manager and its repository.**
7. **System contexts.** Every System context registers both repositories, next to
   `CommandLogRepository` (`SystemContext.java:70-94`).
8. **`ProjectionRepository` and `CatchUpStarter`.**
   - `CatchUpRequested` goes through the system write side.
   - The check for a running catch-up looks at the current tenant only.
9. **Matching by tenant** in `CatchUpMixin` and `CatchUpJobs`.
10. **Removals** (Design / 7).
11. **Tests** (see "Tests").
12. **Docs.**
    - The KDoc of `catchUp()`: `CatchUpCompleted` can still be subscribed to from the domain
      context, and the catch-up's events are no longer stored by default.
    - The release notes.

## Tests

### Existing scenarios must keep working

Each of these suites keeps what it checks:

- `CatchUpTest` and `InMemoryCatchUpTest`
- `ProjectionRepositoryCatchUpSpec`
- `CatchUpJobsPerPageIgTest`
- `EmptyRepositoryCatchUpIgTest`
- `InactiveInstancesCatchUpIgTest`
- `KeptStartSignalIgTest`
- `RepeatedCatchUpIgTest`
- `RepeatedCatchUpSignalIgTest`
- `CatchUpStationSpec` and `CatchUpJobsSpec`
- `DeliverySpec`

Only the plumbing changes:

- reading the jobs, which today goes through `delivery.catchUpStorage()`
  (`AbstractCatchUpIgTest.jobStatuses()`);
- posting `CatchUpRequested` the way `CatchUpStarter` does (`RepeatedCatchUpSignalIgTest`).

Reactors that act at a given step still get the catch-up's events through the System bus, as
in `EmptyRepositoryCatchUpIgTest.kt:161`.

Carry over the scenarios of `SeveralRepositoriesCatchUpIgTest` from the branch of #1694:

- several repositories, in either order of registration;
- selected instances only;
- catch-ups one after another;
- a context built before another one;
- a context with no projections built first or last.

Drop its cases that test only the routing fix of #1694.

### New scenarios, asked for by the owner

"In parallel" means separate jobs that are active at the same time. A single job for several
instances is covered by the existing tests.

- **S1.** Several jobs for the same projection `A`, each for its own instances, all active at
  once. Instances that are not caught up keep getting live events.
  - Today only `CatchUpTest.java:274-281` starts two by-ID jobs of one type, and nothing
    ensures that they overlap.
- **S2.** Jobs for some instances of `A`, active at the same time as a catch-up of all
  instances of `B`. Live events flow to both.

**Forcing the overlap.** A reactor starts the next job when the previous one reaches
`STARTED`, `IN_PROGRESS` or `FINALIZING`. Each start point is its own case. The test records
that at least two jobs were active at the same moment.

**Run every scenario for every combination of:**

| What varies | Values | How |
|---|---|---|
| Tenancy | single, multi | **Multi:** two tenants run the scenario at once, with the same instance IDs and different histories. **One more case:** only tenant X catches up while tenant Y gets live events. This case is the tenancy defect. |
| Bounded Contexts | 1, 2, 3 | Each context has its own `A` and `B`, which makes 6 projection types in the test fixtures. All contexts run the scenario at once. With 3 contexts, they are built first, in the middle and last. |
| Shards | 1, 3 | As in the existing suites. |

**Checks in every run:**

1. Every job ends `COMPLETED`.
2. Each instance holds the state built from its tenant's history plus its live events.
   Nothing is lost, and nothing is applied twice.
3. Instances that were not caught up got every live event while the jobs ran.
4. No message is left `TO_DELIVER` or `TO_CATCH_UP`.
5. No failures were recorded, in receptors or in delivery runs. Use the watchdog monitor of
   `AbstractCatchUpIgTest`.
6. The jobs overlapped.

Mark the suite as `@SlowTest`. Each run waits about a second for the turbulence period.

### The new routing

- A request of job `J` for shard `S` lands in shard `S`, and its answer reaches `J`. This runs
  with 3 contexts, and the one built last has no projections.
- A catch-up message whose projection type no open context serves fails with an error that
  names the type.
- A catch-up message whose projection type two open contexts serve fails with an error that
  names both. This needs the check switched off, as in tests.
- The `FINALIZING` story as a test: a live event emitted right after the final read still
  reaches its projection. Inject it from a reactor on `FinalizationStarted`.
- A maintenance message with a shard-only ID, the old form, is reported clearly.
- **Not runnable on `master`, so run these first:**
  - several `CatchUp` repositories writing into the one shared storage;
  - a process manager in a System context reading the event store of its domain context.

  On `master`, two repositories of one type collide in the `Delivery`.

## Release notes

- **`AbstractStatefulReactor` is removed.**
- **`CatchUpStorage` and `DeliveryBuilder.setCatchUpStorage()` change,** because jobs are now
  entity records.
- **The catch-up's events are no longer stored by default.** To keep them, turn on
  `BoundedContextBuilder.systemSettings().persistEvents()`, which also stores every other
  system event of the context.
- **`CatchUpCompleted` can still be subscribed to from the projection's context.** But
  `BlackBox.assertEvents()` no longer shows the catch-up's events, because it listens only to
  the domain bus (`BoundedContext.java:548`). No test in `core-jvm` relies on this.
- **Upgrade rule.** Finish all catch-ups, and let the delivery drain, before upgrading.
  - Job records written before the upgrade use the old format. They are not converted, and
    they are not read after the upgrade (decision 12).
  - Older maintenance messages use the old ID.
- **The tenancy defect is fixed:** jobs no longer touch the messages of other tenants.

## Checked facts

These were run on a copy of `master`, where no server code differs from `39ef520dc16`.
The tests are `ProposalChecksProbeTest.kt` and `CatchUpEventsStoredProbeTest.kt`, in
`server/src/test/kotlin/io/spine/server/delivery/`.

- **They are checks, not final tests.** They print their findings instead of asserting them,
  and they have no copyright header.
- **Before the PR,** turn them into specs that assert the behavior of this PR, or delete them.
  - `CatchUpEventsStoredProbeTest` asserts today's behavior. After this PR, it should show no
    catch-up events in the domain store (decision 9).
- **The third check,** `SameTypeInTwoContextsProbeTest.kt`, belongs to the check PR.

**When a process manager saves its state:**

| Case | Storage when the bus posts handler 1's event | Storage when the process reacts to that event |
|---|---|---|
| one command | saved (`CREATED`) | `CREATED` |
| two commands delivered in a row | nothing yet | `CREATED` |

**One storage for all tenants.** A process manager repository in a multitenant context,
whose `createStorage()` returns a single-tenant storage:

- no failures;
- a read with no tenant set returns the records of both tenants;
- tenant B finds the records of tenant A.

With the default storage:

- a read with no tenant set fails with `IllegalStateException: No current TenantId set in
  multi-tenant execution context.`;
- tenant B does not see the records of tenant A.

**What a catch-up stores today.** A catch-up of all instances, with 6 history events:

- **The domain event store held** `CatchUpRequested` 1, `HistoryEventsRecalled` 2,
  `HistoryFullyRecalled` 1, `LiveEventsPickedUp` 1, `ShardProcessingRequested` 2,
  `ShardProcessed` 2 and `CatchUpCompleted` 1.
  - `CatchUpStarted` is never stored, because it goes straight into the inboxes.
  - `EntityPreparedForCatchUp` is a system event, so it is not in the domain store either.
- **The System context, with `persistEvents()`, held** `EntityCreated` 4,
  `EntityStateChanged` 12, `EventDispatchedToSubscriber` 6 and `EntityPreparedForCatchUp` 2.

**Two contexts with the same aggregate type.** The command sent to the first context is
handled and stored by the second one, with no error.

## Found during planning

Read in the code on 2026-10-23, on `master` at `39ef520dc16`. The plan is
`.claude/plans/idempotent-gathering-gosling.md` in this worktree.

1. **A single-tenant context has a tenant.**
   - `CatchUpRequested`, and every catch-up event that follows it, carries the tenant
     `SINGLE_TENANT` (`TenantAware.java:89-94`, `SingleTenantIndex.java:47-50`).
   - Live events in the same context carry an empty `TenantId`.
   - So `CatchUp.tenant` is set only when the domain context is multitenant, and a job with
     no tenant matches messages of any tenant. This applies to `CatchUpMixin.matches()`,
     `CatchUpJobs` and `CatchUpStarter.checkNotActive()`.
2. **A repository closes its storage.**
   - `Repository.close()` closes the storage, and `isOpen()` is final
     (`Repository.java:599-612, 645`).
   - In-memory storages share no data between instances (`InMemoryStorageFactory.java:57-62`),
     so the shared job storage is one object.
   - So the job storage ignores `close()`. It lives as long as its `Delivery`. Nothing closes
     the `CatchUpStorage` of a `Delivery` today either.
3. **The storage SPI keeps its shape.**
   - `CatchUpStorage` keeps its name, its constructor `(StorageFactory, boolean)`,
     `StorageFactory.createCatchUpStorage(boolean)` and `DeliveryBuilder.setCatchUpStorage()`.
   - Its base class changes from `MessageStorage<CatchUpId, CatchUp>` to
     `EntityRecordStorage<CatchUpId, CatchUp>`.
   - `readAll()` and `write(CatchUp)` become `readJobs()` and a test-only `writeJob()`.
     `readByType()` filters in memory. `CatchUpColumn` goes.
4. **Both state protos need `option (entity).kind = PROCESS_MANAGER`.** `EntityClass` throws
   without it (`EntityClass.java:78-91`). `command_log.proto` has it next to `(internal_all)`.
5. **`ProcessManagerRepository.setupInbox()` is final** (`ProcessManagerRepository.kt:137`).
   - So `Repository.initInbox()` gives every inbox the name of its context.
   - `InboxDeliveries` uses the name only for the two catch-up types.
   - `Segment.groupByTargetType()` groups by the inbox picked for each message, not by type
     URL alone.
   - Unregistering removes an entry only if it is the same inbox.
6. **The processes cannot reach their projection repository today.**
   - `ProjectionRepository.sendToCatchingUp()` is `private`; it becomes `internal` with
     `@JvmName`.
   - `CatchUpStarter` is package-private; it becomes `@Internal public`, created by
     `ProjectionRepository.initCatchUp()`.
   - `CatchUpProcess` drops its type parameter.
7. **New proto types go in new files** (team memory `feedback_proto_one_message_per_file`).
   This replaces "delivery.proto" in "Design / 2":
   - `ShardMaintenanceId` goes in `spine/server/delivery/shard_maintenance_id.proto`;
   - `FinalizationStarted` and `AllShardsFinalized` go in
     `spine/server/catchup/catch_up_finalization_events.proto`.
   - Changed on 2026-10-28, on the owner's request: the two events moved into
     `catch_up_events.proto`, next to the other events of the catch-up. They belong to that
     family, and the files had the same options, so neither the Java names nor the type URLs
     changed.
8. **Stand.** Today both reactors skip Stand. The two repositories return `false` from
   `isTypeSupplier()`, which keeps that.
9. **`CatchUpRequested` becomes a system event, posted in parallel in production** (team
   memory `system-events-synchronous-in-tests`). The new suite gets one case with parallel
   posting through `HoldingExecutor`.
   - Review of #1699 (Codex and Copilot, 2026-10-27): posted in parallel, the request let
     `catchUp()` return before the job was stored. A second call for the same instances then
     passed the check, and two jobs rebuilt the same projections. On `master`, with the default
     local `Delivery`, the job was stored before `catchUp()` returned.
   - Fixed: `CatchUpStarter` posts the request with the new `SystemWriteSide.postEventNow()`,
     which does not use the posting executor. `CatchUpRequestIgTest` holds the executor and
     checks that the second call fails. The tests that hold system events no longer release
     the request themselves.
   - Reverted on 2026-10-28, on the owner's request: the case is too rare for a method of
     its own. `CatchUpStarter` posts the request with `postEvent()` again, and
     `CatchUpRequestIgTest` is removed. The `@throws` docs of `catchUp()` and `catchUpAll()`,
     and section 5 of the migration guide, say that the check misses a catch-up whose
     request is not processed yet.
   - Not fixed, also from that review: two open contexts with one name, such as two
     `AssumingTests` contexts, share the key of the catch-up inboxes. Keying them by the context
     object needs a way from a domain context to its System context, or the framework could
     forbid two open contexts with one name. Left for a task of its own.
   - Fixed since by #1701, which this branch now builds on: an open domain context must have
     a name of its own.
10. **An old-form maintenance message** fails the delivery run with an error that names the
    upgrade rule, as a type with no inbox does today (`InboxDeliveries.java:48-53`).
11. **A correction to "Tests".** Today the reactor of `EmptyRepositoryCatchUpIgTest` gets
    `LiveEventsPickedUp` from the domain bus. Afterwards it gets the event from the System
    bus. It is registered on both buses (`BoundedContext.java:277-286`), so the test keeps
    working.
12. **`ShardMaintenance` stores a record per job and shard.** The plan expected no record,
    as a process manager is stored only if its state changed (`PmEndpoint.kt:50-55`). A test
    showed otherwise: a new process counts as changed. So each System context stores one
    small record per catch-up job and affected shard. This is in the release notes.
13. **The check PR's lookup returns names.** This was read in its uncommitted work, on
    2026-10-23: worktree `awesome-joliot-2f6ff3`, task file `one-context-per-entity-state.md`.
    - The record is the package-private `io.spine.server.ServedStateTypes`. It holds the
      context objects, compared by identity.
    - The lookup is `@Internal ServerEnvironment.contextsServing(TypeUrl)`, which returns
      `ImmutableList<BoundedContextName>`.
    - Two names mean `DuplicateEntityStateException(type, names)`, which is public.
    - Names are enough for the `Delivery`, because `toSystem()` gives the key of the inbox.
    - `CatchUpProcess` needs the domain context itself, to reach the repository and the event
      store. So this PR adds an `@Internal` lookup over the same record that returns the
      context. It fails with that exception when two contexts serve the type.
14. **Risk: contexts that tests leave open.** At the end of `:server:test`, 122 domain
    contexts are never closed (the check PR measured this).
    - The check is off in tests. So if a leaked context serves a projection type that a
      catch-up test uses, the lookup finds two contexts, and the catch-up fails.
    - If this happens, fix the leaking test: close its `BlackBox`.
    - Gone since #1700: the check is on in tests too, so a leaked context makes the next
      context that serves its types fail at once.
15. **Found while testing: the deduplication of a completed job works per page.**
    - `ParallelCatchUpsIgTest` with pages of 4 messages, one page per run, failed 69 cases
      of 109. A caught-up tally held a live event twice, or out of order, e.g.
      `[200, 600, 1000, 3800, 3000, 3800]`. All 109 cases pass with the default page of 500.
    - The cause, as read in the code: during `FINALIZING`, a live event is held, and the final
      read sends a copy of it. Once the job is `COMPLETED`, `CatchUpStation.completedWith()`
      deduplicates and sorts the held messages within one page only
      (`CatchUpStation.java:278-291`). With small pages, the two copies are on different
      pages, and both are delivered.
    - Not reproduced on `master`: there, the same cases break earlier, on the defects this
      PR fixes.
    - In production it can happen when a shard holds more than a page of messages.
    - Fixed in this PR on 2026-10-28, on the owner's request, rather than in a PR of its own:
      see `catch-up-held-messages-across-pages.md`.

## Review: the columns of the catch-up jobs (2026-10-28)

armiol's review (changes requested): `CatchUpColumn` is removed, so the job storage stores no
columns of `CatchUp`, and the jobs cannot be queried by them. Nothing was run on a real storage.

- **As found.** On `master`, `CatchUpColumn` stored `status`, `when_last_read` and
  `projection_type`, and `CatchUpStorage.readByType()` queried by `projection_type`. In this
  PR the jobs are entity records with no columns of their own, and `readByType()` reads all
  the jobs and filters them in memory. No test queried the jobs by a column, so nothing failed.
- **Agreed in chat with the owner:**
  1. `CatchUp` declares the three columns again: `(column)` on `when_last_read` and `status`,
     and a new `projection_type`, which the process copies from its ID on the request. A column
     must be a top-level field of the state, and the type is in the ID only.
  2. `readByType()` queries by the `projection_type` column.
  3. A new abstract suite, `CatchUpStorageTest`, in the test fixtures, which storage libraries
     extend as they extend `InboxStorageTest`. It checks the three columns, a query by each,
     `readByType()`, `readJobs()` and `readJob()`. A subclass runs it on the in-memory
     storage, which matches no record on a column the record does not store.
  4. The migration guide: on a SQL storage, drop the old job table, not only its records.
     `jdbc-storage` creates a table only if it is missing, so the old table would stay with
     the old columns.
- **Later, in `jdbc-storage`:** once it builds on this version, a `JdbcCatchUpStorageTest`
  and a MySQL one, behind `MysqlTests.enabled`, extend the suite. They also check that the old
  and the new tables collide, as item 4 assumes.

## Coordination

- **Order.** The check PR goes first, because this PR needs its record and lookup.
- **PR #1694.** Close it without merging. Its correction of
  `.agents/memory/kotlin-storage-spi-interop-traps.md` moves to the check PR.
- **Other PRs.** No other catch-up PR is open (checked on 2026-10-23): #1695 and #1696 are
  merged.
- **Older task file.** `catch-up-of-several-repositories.md` exists only on the branch of
  #1694. Keep what is still useful from it here: the reproduction and its history.
