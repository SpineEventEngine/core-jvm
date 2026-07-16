# Task: Unify `AggregateRepository` with the record-based hierarchy

## Origin

Raised by the product owner right after the
[`SignalDispatchingRepository` extraction](signal-dispatching-repository.md)
(2026-07-16): *now that aggregates are no longer event-sourced, why do we need
`AggregateStorage` in principle — and why can't `AggregateRepository` descend
from `SignalDispatchingRepository`, becoming closer to
`ProcessManagerRepository`?*

The analysis below answers both: the remaining reasons are historical, and the
de-event-sourcing train has already dissolved almost all of them. This document
pins the findings — including the hazards — for Phase F planning.

## The two targets

**A. The descent.** `AggregateRepository` extends `Repository` directly and
*verbatim-duplicates* both routing clusters: `commandRouting` +
`doSetupCommandRouting` (now owned by `SignalDispatchingRepository`) and
`eventRouting` + `doSetupEventRouting` (owned by `EventDispatchingRepository`).
Target hierarchy:

```
Repository
 └─ RecordBasedRepository
     └─ DefaultRecordBasedRepository
         └─ EventDispatchingRepository           (event routing)
             └─ SignalDispatchingRepository      (+ command routing)
                 ├─ ProcessManagerRepository
                 └─ AggregateRepository           ← moves here
```

**B. The dissolution.** `AggregateStorage` is the residue of the mirror era.
Post-cutover it `extends EntityRecordStorage` and adds only:

1. the query gate — `enableStateQuerying()` / `readStates(...)`
   (`AggregateStorage.java:110`, `:130`). Its rationale was "the mirror is an
   optional, derived read-side artifact". Now the state record *is* the
   load-path source of truth, written unconditionally; the gate no longer
   controls whether the data exists, only who may ask — which is what
   `VisibilityGuard` already does for every other entity kind at routing level
   (`VisibilityGuard.java`, `RepositoryAccess.get()`);
2. `writeState(Aggregate)` (`AggregateStorage.java:178`) — a hand-rolled
   entity→`EntityRecord` conversion; `RecordBasedRepository` does the same job
   generically through its `StorageConverter` (`toRecord`);
3. a distinct SPI type behind the `StorageFactory.createAggregateStorage` seam
   (`StorageFactory.java:132`), forwarded by `SystemAwareStorageFactory` —
   though physical allocation has been keyed by `RecordSpec.sourceType` via
   `createRecordStorage` since `simplify-aggregate-storage` (.460).

Retire the gate, let the converter write, deprecate the seam — and the class
dissolves into `EntityRecordStorage`.

## What has already fallen (verified 2026-07-16)

| Historical blocker | Status |
|---|---|
| `store()` visibility fork (`protected` aggregate vs `public` record-based) — [unify-store Problem 2](unify-store-and-find-or-create.md), then "deferred" | **Executed by the owner** in `be55da92c5`: `Repository.store(E)` is `public final` for *all* repositories, routing cache → `afterStore()`; aggregates already express their divergence via the shared hooks — `doStore` (journal → state, skips untouched instances) and `afterStore` (per-dispatch state history). Supersedes that doc's deferral. |
| Event import (`ImportBus`) special wiring | **Removed** in PR-B2 (locked decision 4 of the plan); zero references in `server/src/main`. |
| Event-sourced loading incompatible with record-based `find` | **Gone** — `doLoadOrCreate` loads from the latest `EntityRecord` (`AggregateRepository.java:790`), the exact shape of `RecordBasedRepository.findOrCreate`. |
| Dispatcher-shape inversion (aggregate = direct `CommandDispatcher` + `EventDispatcherDelegate`; PMR = the mirror image) | **Symmetric plumbing exists both ways** — `BoundedContext` wraps either delegate direction (`DelegatingEventDispatcher`, `BoundedContext.java:329`; `DelegatingCommandDispatcher` on the PMR path). PMR proves the target shape end-to-end. |
| No shared home for command routing | **Built**: `SignalDispatchingRepository` (`cc766a4206`) — the prepared landing zone. |

## Key separability: the descent does NOT require the dissolution

`AggregateStorage` *is* an `EntityRecordStorage<I, S>`, and
`RecordBasedRepository.createStorage()` (`RecordBasedRepository.java:221`) is
an overridable seam. `AggregateRepository` can descend while keeping its
`createStorage()` override returning `AggregateStorage` — the storage keeps
working as the repository's record storage. This splits the work into two
independently shippable steps and keeps the vendor-facing change (B) out of
the hierarchy change (A).

## Work list A — the descent

1. **Routing dedup** (the payoff): delete `commandRouting`, `eventRouting`,
   both `doSetup*` methods and both accessors — inherited from the new
   parents. `setupCommandRouting` becomes a no-op `@Override` of the abstract
   hook, mirroring PMR (aggregates handling no commands are legal —
   `checkDispatchesMessages` requires commands *or* events).
2. **Dispatcher flip**: `dispatch(CommandEnvelope)` →
   `dispatchCommand(CommandEnvelope)` (interface changes from
   `CommandDispatcher` to the inherited `CommandDispatcherDelegate`; the body
   is already verbatim-identical to the one now in
   `SignalDispatchingRepository` — it disappears entirely). The event side
   flips from `EventDispatcherDelegate` to the inherited `EventDispatcher`:
   implement `dispatchTo(Set<I>, EventEnvelope)` + `canDispatch` on the
   aggregate's endpoints, drop `dispatchEvent`/`DelegatingEventDispatcher`
   wiring. Registration shrinks: command registration becomes the automatic
   side effect of event-dispatcher registration (the
   `instanceof CommandDispatcherDelegate` tail of
   `BoundedContext.registerEventDispatcher`) — exactly the PMR path.
3. **The bulk-store hole** (hazard, must not be missed):
   `RecordBasedRepository.store(Collection<E>)`
   (`RecordBasedRepository.java:235`) maps entities → `writeAll(records)`,
   **bypassing `doStore`** — inherited as-is it would silently skip journaling
   (and the untouched-instance guard). This is the exact generalization the
   [unify-store review noted](unify-store-and-find-or-create.md) ("a
   duplicated body silently skips any step a future `doStore` grows, the way
   `AggregateRepository.doStore` grew event journaling"). Options: reroute the
   bulk path through per-entity `doStore` at the base; or `final`-override it
   in `AggregateRepository`. Decide at implementation; a base-level fix also
   protects future PM journaling (Phase F items 1–3).
4. **`find`/`load` semantics reconciliation**: aggregate `find(I)` loads even
   archived/deleted instances (`AggregateRepository.java:856`), while the
   record-based API splits active-only vs any-lifecycle lookups. Align
   deliberately (likely: keep the aggregate behavior via an override, document
   the difference) — verify against `AggregateRepositoryTest` expectations.
5. **API widening**: `loadAll`, `iterator`, `findActive`, `store(Collection)`
   etc. become public contract on every aggregate repository (aggregate
   `find`, `findRecords`, `findStates` are public already). Additive but
   breaking-flavored → the round-up version treatment, same as `.480`.
6. **Generics check**: `Aggregate` is an `AbstractEntity`
   (`Aggregate → CommandHandlingEntity → TransactionalEntity →
   AbstractEntity`) and `S extends AggregateState<I> extends EntityState<I>` —
   both bounds of `SignalDispatchingRepository<I, E, S>` hold; the PM case
   proves transactional entities ride the record-based machinery
   (`PmTransaction` precedent).
7. `loadOrCreate` naming: the package-private `loadOrCreate(I)` forwarder
   (reached by `AggregateEndpoint`/`AggregateRoot`) vs the inherited
   `findOrCreate` — the [unify-store "Not taken"](unify-store-and-find-or-create.md)
   nit suggested revisiting exactly here, "when these methods are being
   reshaped anyway".

## Work list B — the dissolution

1. **The gate decision (OPEN — owner)**: retire `enableStateQuerying`,
   accepting guard-only visibility like PMs and projections, or re-home the
   refusal at the repository level. If retired, the inherited ungated
   `findRecords` replaces `readStates` (the aggregate's
   `QueryableRepository` overrides at `AggregateRepository.java:912-926`
   disappear), and with them the last behavioral reason for the class.
   *Not yet a locked decision — the 2026-07-16 locked decisions cover the PM
   journal and projection state history only.*
2. **`writeState` → converter**: an aggregate-aware `StorageConverter`
   (state + version + lifecycle flags), so `doStore` writes through the
   standard `toRecord` path; delete `writeState`.
3. **Vendor seam deprecation**: deprecate-then-remove
   `StorageFactory.createAggregateStorage` and the `SystemAwareStorageFactory`
   forwarding — breaking for storage vendors (JDBC, Datastore), same category
   as the `createHistoryStorage` seam removal; record as a Phase G obligation
   in the plan when scheduled.
4. `AggregateStorage` itself: deprecate as a thin `EntityRecordStorage` alias
   for one release, or remove outright (pre-GA) — owner's call at
   implementation time.

## Interaction with the Phase F decisions (2026-07-16)

- **PM journal confirmed wanted; projection state history added (opt-in, off
  by default)** — see the plan's Phase F locked decisions. Consequence for
  hoisting the shared configuration shape (Phase F item 3 says "hoist a
  configuration shape shared with `AggregateRepository`"):
  - the **journal** is meaningful only for repositories whose entities emit
    events — after the descent, exactly the `SignalDispatchingRepository`
    subtree (aggregates: unconditional; PMs: opt-in). Its natural shared home
    is `SignalDispatchingRepository` (machinery) with the default posture set
    per subclass.
  - the **state history** must serve Projections too, which live outside the
    signal-dispatching subtree (`ProjectionRepository` extends
    `EventDispatchingRepository`). Its shared home is therefore higher —
    `EventDispatchingRepository` or `Repository` (the per-dispatch write hook,
    `Repository.afterStore()`, is already at the top).
- The end state is the convergence Phase F steers toward from both sides: PMs
  gain journal + state history (aggregate-shaped), aggregates go record-based
  (PM-shaped), and the repositories differ only in what is genuinely
  different — the unconditional journal, `IdempotencyGuard`, and commanding.

## Suggested sequencing

1. **A first, B later.** The descent is now mostly mechanical (the routing and
   dispatch bodies are verbatim duplicates of code that already lives in the
   new parents) and unblocks the Phase F hoisting; the dissolution carries the
   vendor-facing breakage and the open gate decision.
2. Within A: land the hierarchy flip + dedup in one PR (atomic — the
   dispatcher interfaces change together), with the bulk-store fix and the
   `find` reconciliation in the same PR (both are correctness, not polish).
3. B can ride Phase G (vendors are being touched there anyway) once the gate
   decision is locked.

## Verification

- `./gradlew build` + `dokkaGenerate`; the aggregate suites
  (`AggregateRepositoryTest`, storage identity specs, mirror-migration
  fixtures) and `BlackBox` flows are the behavior proof.
- A dedicated test for the bulk-store path journaling (today nothing covers
  `store(Collection)` on an aggregate repository — it does not exist there).
- Round-up version bump (breaking: interface flip + API widening).
- After B: smoke-build the storage vendors (the Phase C/G list in the plan).

## Out of scope

- The Phase F feature work itself (PM journal, PM/Projection state history) —
  tracked in [the plan](de-event-sourcing-plan.md), Phase F items 1–7.
- Kotlin conversion of the repository hierarchy (dedicated `java-to-kotlin`
  task).
- Renaming `findOrCreate`/`loadOrCreate` to one name beyond what item A7
  requires.
