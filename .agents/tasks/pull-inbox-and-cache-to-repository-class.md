# Task: Pull Inbox and Cache to the Repository class

## The problem

The classes `AggregateRepository`, `ProcessManagerRepository`, and `ProjectionRepository`
all have code that works with `Inbox` and `RepositoryCache`.
This code is duplicated in each class.

## Solution

Pull up the related code to the base class `Repository`, providing necessary
configuration callback methods so that the subclasses can override them performing
customisation which is currently done in-place.

`Repository` drives the initialization itself: its `registerWith()` creates the
cache and the inbox, and the subclasses only contribute the parts that differ, via
callbacks.

## Duplication inventory

What is actually triplicated today (beyond the two fields named in the brief):

| Element                          | `AggregateRepository` | `ProcessManagerRepository` | `ProjectionRepository`     |
|----------------------------------|-----------------------|----------------------------|----------------------------|
| `inbox` field                    | ✅                    | ✅                         | ✅                         |
| `cache` field                    | ✅                    | ✅                         | ✅                         |
| `initCache(boolean)`             | `doLoadOrCreate`      | `doFindOrCreate`           | `doFindOrCreate`           |
| `initInbox()` scaffolding        | ✅                    | ✅                         | ✅ (takes `Delivery`)      |
| `newCachingListener()`           | identical             | identical                  | identical                  |
| `inbox()`                        | `requireNonNull`      | `checkNotNull`             | `checkNotNull`             |
| `close()` → `inbox.unregister()` | via `attemptClose`    | `if (inbox != null)`       | `if (inbox != null)`       |
| "dispatches no messages" guard   | `checkNotVoid()`      | `checkNotDeaf()`           | `ensureDispatchesEvents()` |

Only the **inbox endpoints** and the **cache's load/store functions** genuinely differ.

## Constraints discovered

1. **`Repository` is the lowest common ancestor** of the three — `AggregateRepository`
   extends it directly, the other two arrive via
   `EventDispatchingRepository → DefaultRecordBasedRepository → RecordBasedRepository`.
   The brief's target is correct.
2. **…but `Repository` also roots repositories that have no inbox and no cache.**
   `DefaultRecordBasedRepository` is public API with concrete subclasses
   (`TestRepo`, `DefaultEntityFactoryTestEnv.*`, `DefaultConverterTest.*`, plus
   downstream user code). `Inbox.Builder.build()` throws when no endpoint was added.
   → `setupInbox()` must be a **no-op by default**, and the base must **skip
   `build()`** when the subclass added nothing.
3. **`doStore` / `doLoadOrCreate` on `AggregateRepository` are published API.**
   Both are `@VisibleForTesting protected` and are overridden in the *published*
   `testFixtures` variant (`DeliveryTestEnv.CalculatorRepository`) and in
   `StateHistoryTestRepository.kt`. `ProcessManagerRepository`/`ProjectionRepository`
   name their equivalents `doFindOrCreate`/`doStore` and both are `private`.
   → The base adopts **`doLoadOrCreate`** and PM/Projection rename inward; the reverse
   direction would break downstream overrides.
4. **The "dispatches no messages" guard must precede inbox construction.** In
   `ProcessManagerRepository` and `ProjectionRepository` it currently runs *after*
   `super.registerWith()`. Once the base builds the inbox, a failing guard would leave
   an `Inbox` registered in the JVM-wide `Delivery`. → Pull the guard up as a
   `checkDispatchesMessages()` callback invoked by `Repository.registerWith()` **before**
   the context is even assigned.
5. **Drive from `registerWith()`, not `onRegistered()`.** `onRegistered()` would
   preserve ordering perfectly, but `ProjectionRepositoryTest.createRepository()` calls
   `repository.registerWith(context)` directly, bypassing `BoundedContext.register()` —
   an `onRegistered()`-based design would hand those tests a null cache.

### Checks that clear the way

- `ServerEnvironment.delivery()` is `synchronized` and memoizes into an `EnvSetting`,
  so `ProjectionRepository` can obtain the *same* `Delivery` for `initCatchUp()` after
  the base has already used it for the inbox.
- `io.spine.server.entity` already imports `io.spine.server.delivery`
  (`EntityLifecycle`, `EntityMessageEndpoint`) → no new package cycle.
- `RepositoryCache<I, E extends Entity<I, ?>>` has bounds *identical* to
  `Repository<I, E extends Entity<I, ?>>` → the field type-checks with no variance work.
- `Delivery.newInbox(TypeUrl)` is public (`Inbox.newBuilder` is not) and
  `Inbox.unregister()` is public (`Delivery.unregister` is not) → the base can both
  build and unregister without widening `Delivery`.
- `AggregateStorage.enableStateQuerying()` only sets a flag read by
  `ensureStatesQueryable()` on the **query path** (`readStates`) — never on the write or
  load path. So building the inbox before `configureQuerying()` cannot affect dispatch.

## Design decisions

1. **`Repository.registerWith()` drives**, invoking `checkDispatchesMessages()` → context
   assignment → `open()` → `initInbox()`. One gate (`Inbox.Builder.hasEndpoints()`)
   governs *both* resources: every repository that has an inbox also has a cache, and no
   repository has one without the other, so the cache is created inside `initInbox()`
   right before `build()`.
2. **`setupInbox(Inbox.Builder<I>)` is a no-op callback**, not abstract — an abstract
   method would land on every `DefaultRecordBasedRepository` subclass and break them.
3. **New `protected` members are `@Internal`.** `Inbox` and `RepositoryCache` are both
   `@Internal`; exposing them on the public `Repository` widens the user-facing
   extension surface. Precedent: `Repository.onRoutingFailed` and `Repository.toModelClass`
   are already `@Internal protected`.
4. **`doStore()`'s base default is `store(entity)`** — the semantically correct
   "uncached store" for a repository whose `store()` is not cache-routed, and exactly
   right for `RecordBasedRepository`. It recurses only for a subclass that routes
   `store()` through the cache *without* overriding `doStore()`; all three do override
   it. Documented explicitly in the Javadoc.
5. **`doLoadOrCreate()`'s base default is `find(id).orElseGet(() -> create(id))`** —
   provably equivalent to `RecordBasedRepository.findOrCreate()`, which is
   `findRecord(id).map(this::toEntity).orElseGet(() -> create(id))` while
   `find(id)` is `findRecord(id).map(this::toEntity)`.
6. **Inbox unregistration moves into `Repository.close()`**, threaded through the
   existing `attemptClose()` accumulator. This *fixes* a latent leak: PM and Projection
   currently skip `inbox.unregister()` when `super.close()` throws. Consistent with
   `293aaa5897` ("Clear repository state even when the storage fails to close").
7. **The guard callback is named `checkDispatchesMessages()`**, replacing three
   divergent names for identical logic. It mirrors the two predicates its body calls
   (`dispatchesCommands()`, `dispatchesEvents()`) and the `CommandDispatcher` /
   `EventDispatcher` interfaces the repositories implement. `check…` is the house style
   for validators (~20 uses vs. 4 for `ensure…`). The previous names are retired: both
   `checkNotDeaf()` and `checkNotVoid()` assert a negated absence, so a reader parses a
   double negative to learn what is required — and "deaf" uses a disability as a
   pejorative metaphor. This retires "deaf" from the codebase; it occurs nowhere else.

## Changes

### 1. `server/.../delivery/Inbox.java` — expose the endpoint gate

- Add to `Inbox.Builder`:
  ```java
  /** Tells if at least one event or command endpoint was added to this builder. */
  public boolean hasEndpoints() {
      return !eventEndpoints.isEmpty() || !commandEndpoints.isEmpty();
  }
  ```
  No annotation needed — `Inbox` is already `@Internal`, which covers the nested builder.
  `Endpoints.isEmpty()` already exists (package-private, same package).

### 2. `server/.../entity/Repository.java` — own the inbox and the cache

- Imports: `io.spine.server.delivery.BatchDeliveryListener`, `io.spine.server.delivery.Inbox`.
- Add fields:
  ```java
  private @MonotonicNonNull Inbox<I> inbox;
  private @MonotonicNonNull RepositoryCache<I, E> cache;
  ```
- Add `@Internal protected final Inbox<I> inbox()` → `requireNonNull(inbox)`.
- Add `@Internal protected final RepositoryCache<I, E> cache()` → `requireNonNull(cache)`.
- Add callbacks:
  - `protected void checkDispatchesMessages()` — no-op default. Verifies the repository
    dispatches at least one kind of message to its entities, throwing
    `IllegalStateException` otherwise.
  - `@Internal protected void setupInbox(Inbox.Builder<I> builder)` — no-op default.
  - `@Internal protected E doLoadOrCreate(I id)` — default
    `find(id).orElseGet(() -> create(id))`.
  - `@Internal protected void doStore(E entity)` — default `store(entity)`.
- Add private `initInbox()` and `initCache()`:
  ```java
  private void initInbox() {
      var delivery = ServerEnvironment.instance().delivery();
      Inbox.Builder<I> builder = delivery.newInbox(entityStateType());
      setupInbox(builder);
      if (!builder.hasEndpoints()) {
          return; // This repository does not use the `Inbox`.
      }
      initCache();
      inbox = builder.withBatchListener(newCachingListener())
                     .build();
  }

  private void initCache() {
      cache = new RepositoryCache<>(context().isMultitenant(),
                                    this::doLoadOrCreate, this::doStore);
  }

  private BatchDeliveryListener<I> newCachingListener() {
      return new BatchDeliveryListener<>() {
          @Override
          public void onStart(I id) {
              cache().startCaching(id);
          }

          @Override
          public void onEnd(I id) {
              cache().stopCaching(id);
          }
      };
  }
  ```
  `initCache()` needs no `multitenant` parameter — the base reads
  `context().isMultitenant()`, which the three subclasses pass explicitly today.
- `registerWith()` — insert `checkDispatchesMessages()` before the context assignment,
  and `initInbox()` at the end:
  ```java
  if (sameValue) {
      return;
  }
  checkDispatchesMessages();
  this.context = context;
  open();
  if (isTypeSupplier()) {
      context.stand().registerTypeSupplier(this);
  }
  initInbox();
  ```
- `close()` — unregister the inbox through the accumulator, outside the `isOpen()`
  guard so double-close keeps its current semantics:
  ```java
  if (inbox != null) {
      failure = attemptClose(failure, inbox::unregister);
  }
  ```

### 3. `server/.../aggregate/AggregateRepository.java`

- Remove: `inbox` + `cache` fields, `initCache(boolean)`, `initInbox()`,
  `newCachingListener()`, `inbox()`, the `inbox` branch of `close()`.
- `checkNotVoid()` → `@Override protected void checkDispatchesMessages()`; drop its call
  from `registerWith()` (the base invokes it, and *earlier* than today — before the
  context is assigned, matching the current pre-`super` position).
- Add:
  ```java
  @Override
  protected void setupInbox(Inbox.Builder<I> builder) {
      builder.addEventEndpoint(REACT_UPON_EVENT,
                               e -> new AggregateEventReactionEndpoint<>(this, e))
             .addCommandEndpoint(HANDLE_COMMAND,
                                 c -> new AggregateCommandEndpoint<>(this, c));
  }
  ```
- `doLoadOrCreate(I)` and `doStore(A)` — add `@Override`; keep
  `@VisibleForTesting protected` and their bodies unchanged.
- `store(A)` → `cache().store(aggregate)`; `loadOrCreate(I)` → `cache().load(id)`.
- `registerWith()` reduces to:
  ```java
  super.registerWith(context);
  setupRouting();
  context.internalAccess().registerCommandDispatcher(this);
  configureQuerying();
  ```
- Prune imports: `BatchDeliveryListener`, `ServerEnvironment`, `RepositoryCache`,
  `requireNonNull` (verify each — `ServerEnvironment` may still be referenced).

### 4. `server/.../procman/ProcessManagerRepository.java`

- Remove: `inbox` + `cache` fields, `initCache(boolean)`, `initInbox()`,
  `newCachingListener()`, `inbox()`, and the whole `close()` override (it only
  unregistered the inbox).
- `checkNotDeaf()` → `@Override protected void checkDispatchesMessages()` (was
  `private`); drop its call. This is the last use of "deaf" in the codebase.
- Add `@Override protected void setupInbox(Inbox.Builder<I> builder)` with
  `REACT_UPON_EVENT → PmEventEndpoint.of(this, e)` and
  `HANDLE_COMMAND → PmCommandEndpoint.of(this, c)`.
- `doFindOrCreate(I)` → `@Override protected P doLoadOrCreate(I id)`, body unchanged
  (`super.findOrCreate(id)`).
- `doStore(P)` → `@Override protected void doStore(P entity)` (was `private`), body
  unchanged (`super.store(entity)`).
- `findOrCreate(I)` → `cache().load(id)`; `store(P)` → `cache().store(entity)`.
- `registerWith()` reduces to `super.registerWith(context); doSetupCommandRouting();`.
- Prune imports: `BatchDeliveryListener`, `ServerEnvironment`, `RepositoryCache`,
  `MonotonicNonNull`, `InboxLabel` (keep — still used by `setupInbox`).

### 5. `server/.../projection/ProjectionRepository.java`

- Remove: `inbox` + `cache` fields, `initCache(boolean)`, `initInbox(Delivery)`,
  `newCachingListener()`, `inbox()`, the whole `close()` override.
- `ensureDispatchesEvents()` → `@Override protected void checkDispatchesMessages()`; drop
  its call. Keep the projection-specific message ("neither domestic nor external event
  subscriptions") — only the name is shared, not the wording.
- Add `@Override protected void setupInbox(Inbox.Builder<I> builder)` with
  `UPDATE_SUBSCRIBER → ProjectionEndpoint.of(this, e)` and
  `CATCH_UP → CatchUpEndpoint.of(this, e)`.
- `doFindOrCreate(I)` → `@Override protected P doLoadOrCreate(I id)`.
- `doStore(P)` → `@Override protected void doStore(P entity)`.
- `findOrCreate(I)` → `cache().load(id)`; `store(P)` → `cache().store(entity)`.
- `registerWith()` reduces to:
  ```java
  super.registerWith(context);
  var delivery = ServerEnvironment.instance().delivery();
  initCatchUp(context, delivery);
  ```
  Safe: `ServerEnvironment.delivery()` memoizes, so this is the same instance the base
  used. `initCatchUp()` does not touch the inbox at construction — `sendToCatchingUp`
  is passed as a method reference and calls `inbox()` only when invoked later.
- Update the `registerWith()` Javadoc: the inbox/cache paragraph now describes a base
  responsibility.

### 6. Tests

Expected: **no test changes.** Verify during implementation:

- `ProjectionRepositoryTest.createRepository()` / `ProcessManagerRepositoryTest` build
  their repositories via a direct `repository.registerWith(context)`. Under this design
  `registerWith()` still initializes everything, so they keep working — this is the
  main reason to drive from `registerWith()` rather than `onRegistered()`.
- `notRegisterIfSubscribedToNothing` (both `ProjectionRepositoryTest:537` and
  `ProcessManagerRepositoryTest:549`) assert only the `IllegalStateException`; the guard
  now throws *earlier* (before the context is assigned), which is a cleaner failure.
  Their fixtures (`SensoryDeprivedPmRepository`, `SensoryDeprivedProjectionRepository`)
  are untouched — see *Out of scope*.
- `DeliveryTestEnv.CalculatorRepository` (published testFixtures) overrides
  `doStore`/`doLoadOrCreate` — signatures unchanged, now `@Override`s of inherited
  members.
- `StateHistoryTestRepository.kt` overrides `doStore` from Kotlin — unchanged.

## Verification

- `./gradlew :server:build` — no proto changes, so `build`, not `clean build`
  (per `.agents/guidelines/running-builds.md`).
- `./gradlew dokkaGenerate` — the Javadoc gains `{@link}`s across `entity` ↔ `delivery`.
- Reviewers: `spine-code-review`, `review-docs`. (`kotlin-engineer` is not applicable —
  the change is Java-only; the touched Kotlin is an unmodified test fixture.)
- Version gate (`version-bumped`) — branch is at `.471` vs master, so idempotent.

## Known consequences (accepted)

1. **The inbox is registered with `Delivery` before subclass routing setup.** Analyzed
   as safe: inbox endpoints are lazy, and delivery *from* an inbox uses the target ID
   already resolved at send time — routing is consulted only on the inbound
   `dispatch()`/`dispatchEvent()` path, which requires bus registration that happens
   later. `configureQuerying()` is likewise unaffected (flag read on the query path only).
2. **A registration that fails after `super.registerWith()` leaves the `Inbox` registered
   with the `Delivery`.** NOT A PROBLEM — reviewed and dismissed (owner, 2026-07-15).
   A failing `registerWith()` is a configuration error that fails the creation of the whole
   Bounded Context, which is the correct outcome. A stale entry in a map, in a JVM that is
   not going to finish starting, does not matter at that scale. No code change, and no
   Javadoc either: a caveat about it on `registerWith()` only buries the contract of the
   method under something of no consequence.
3. **`doLoadOrCreate` / `doStore` now appear on every `Repository`**, dead for
   non-caching ones — the accepted cost of driving from the base.

## Out of scope

- Unifying `store()` / `findOrCreate()` / `loadOrCreate()` themselves. Their signatures
  diverge (`protected final void store(A)` vs `public final void store(P)`) and
  `Repository.store(E)` is abstract while `RecordBasedRepository.store(E)` is the real
  write — a much larger change.
- `AbstractStatefulReactor` and `ShardMaintenanceProcess`, which also build an `Inbox`
  but are not `Repository` subclasses.
- Deprecating or narrowing `AggregateRepository.doStore`/`doLoadOrCreate` visibility.
- Renaming the `SensoryDeprived*` test fixtures. `SensoryDeprivedPmRepository`,
  `SensoryDeprivedProcessManager`, `SensoryDeprivedProjection`, and
  `SensoryDeprivedProjectionRepository` are the fixtures for the very guard renamed here,
  and carry the same "entity with no receptors = disabled person" metaphor that motivated
  dropping `checkNotDeaf()`. They live in the *published* `testFixtures` variant, so
  renaming them is an API change deserving its own commit. Something like
  `NoReceptorsPmRepository` / `NoReceptorsProjection` would fit. Tracked separately.
