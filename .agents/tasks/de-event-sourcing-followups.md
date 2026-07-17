# Follow-ups surfaced by the de-event-sourcing task stream

Small, deliberately-deferred items noted while closing out
`aggregate-repository-unification.md`, `unify-store-and-find-or-create.md`,
`event-storage-at-the-repo-level.md`, `pull-inbox-and-cache-to-repository-class.md`,
`signal-dispatching-repository.md`, `entity-state-history.md`, and
`introduce-event-history-type.md` — all archived to `archive/` as of 2026-07-17.
None of these block [`de-event-sourcing-plan.md`](de-event-sourcing-plan.md);
review and prioritize independently.

## Design/API nits

1. **Kotlin conversion of the repository hierarchy.** `Repository`,
   `RecordBasedRepository`, `AbstractEntityRepository`,
   `EventDispatchingRepository`, `SignalDispatchingRepository`,
   `AggregateRepository`, `ProcessManagerRepository`, `ProjectionRepository`
   are all still Java. Flagged as a dedicated `java-to-kotlin` task, out of
   scope for the unification work. (from `aggregate-repository-unification.md`)
2. **`recordRepositoryOf` doc is stale.** Still documents a
   `RecordBasedRepository` return type; the method actually returns
   `Optional<QueryableRepository<?, ?>>`. Fixing it also means dropping the
   now-unused `RecordBasedRepository` import at the call site. (from
   `aggregate-repository-unification.md`, surfaced during its review pass)
3. **`AggregateRepository`'s event-class accessors are non-`final`**
   (`domesticEventClasses()` / `externalEventClasses()`), unlike their
   `ProcessManagerRepository` twins. Left as-is deliberately (no semantic
   change during the hierarchy flip); owner called it a candidate for "a
   future breaking pass". (from `aggregate-repository-unification.md`)
4. **`loadOrCreate`/`findOrCreate` naming split.** `AggregateEndpoint`/
   `AggregateRoot` call the package-private `loadOrCreate(I)` forwarder;
   `PmEndpoint`/`ProjectionEndpoint` call the inherited `findOrCreate`.
   Unifying the name is cosmetic and was explicitly not taken in two places
   (`aggregate-repository-unification.md` item A7,
   `unify-store-and-find-or-create.md` reviewer nit #6) — both point at
   Phase F ("when these methods are being reshaped anyway") as the natural
   time to revisit.

## Test/vendor coverage gaps

5. **No shared `EntityEventStorage` test fixture for storage vendors.**
   Before the cutover, vendor repos (`jdbc-storage`, `gcloud-jvm`, …) got
   journal coverage for free by subclassing the now-deleted
   `AggregateStorageTest`. That path is gone and nothing replaced it — the
   plan's Phase G "Storage-vendor obligation" section covers the
   `AggregateStorage` *removal* (retarget at `AbstractStorageTest`/
   `DelegatingRecordStorageTest`) but does not mention a published
   `EntityEventStorage` fixture suite. Worth deciding, before Phase G lands,
   whether vendors need one or are expected to write their own.
   (from `event-storage-at-the-repo-level.md`, "Out of scope")

## Dead code / published-API cleanup (needs its own commit — testFixtures is published API)

6. **`ProjectAggregateRepository`'s dead storage-injection override.**
   `customStorage` / `injectStorage(...)` / `aggregateStorage()` in
   `server/src/testFixtures/.../aggregate/given/repo/ProjectAggregateRepository.java`
   has zero callers post-cutover but lives in a published `testFixtures`
   class, so removing it is an API change deserving its own review.
   (from `event-storage-at-the-repo-level.md`)
7. **Rename the `SensoryDeprived*` test fixtures.** `SensoryDeprivedPmRepository`,
   `SensoryDeprivedProcessManager`, `SensoryDeprivedProjection`,
   `SensoryDeprivedProjectionRepository` are published `testFixtures` API
   naming the guard that `pull-inbox-and-cache-to-repository-class.md`
   renamed to `checkDispatchesMessages()`. Suggested replacement names:
   `NoReceptorsPmRepository` / `NoReceptorsProjection` (drop the
   "disabled person" metaphor). Deliberately not done alongside the pull-up
   to keep that PR's diff scoped.

## Consistency check (low priority)

8. **`AbstractStatefulReactor` and `ShardMaintenanceProcess`** each build
   their own `Inbox` but are not `Repository` subclasses, so the
   inbox/cache pull-up to `Repository` didn't reach them. Not a defect —
   just unreviewed for whether the same duplication exists there.
   (from `pull-inbox-and-cache-to-repository-class.md`, "Out of scope")

## Reference for Phase F planning

`aggregate-repository-unification.md` (archived) has a section, "Interaction
with the Phase F decisions (2026-07-16)", reasoning about where the shared
journal/state-history configuration surface should live once PM journaling
and Projection state history land: the **journal** belongs on
`SignalDispatchingRepository` (only entities that emit signals need it —
aggregates unconditional, PMs opt-in); the **state history** belongs higher,
on `EventDispatchingRepository` or `Repository` itself, since it must also
serve `ProjectionRepository`, which sits outside the signal-dispatching
subtree. Re-read that section when scoping Phase F items 1–3 and 7.
