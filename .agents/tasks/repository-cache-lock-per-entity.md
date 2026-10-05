# `RepositoryCache`: lock per entity instead of per repository

Issue: [SpineEventEngine/core-jvm#1678][issue-1678]

## Status

Plan approved on 2026-10-05. Implemented on the branch
`repository-cache-lock-per-entity`.

- [x] `RepositoryCacheSpec` written and shown to fail against the old class:
      4 of 16 cases failed — both "serve an entity while another one is …" cases
      timed out behind the repository-wide monitor, and both "stop caching an
      entity …" cases found the entity still cached. The other 12 passed.
- [x] `KeyLocks` + `KeyLocksSpec`
- [x] `RepositoryCache` switched to per-key locks
- [x] Javadoc notes in `Repository`
- [x] Verification — see "Verification" below
- [x] Version bump to `2.0.0-SNAPSHOT.561`; dependency reports regenerated
- [x] Independent reviews of the concurrency logic and of the documentation;
      the test gaps the former found are closed

## Problem

`RepositoryCache` (`server/src/main/java/io/spine/server/entity/RepositoryCache.java`)
declares `load()`, `store()`, `startCaching()` and `stopCaching()` as `synchronized`.
There is one cache per repository, and `loadFn` / `storeFn` do storage I/O **inside**
that monitor. Shards are delivered concurrently by different threads, so every shard
touching the same repository queues behind whichever thread is in a socket read.

Reporter's staging numbers (Pure Photos, 2026-09-30): 238 of 1,018 thread samples
blocked on the cache monitor, the owner in a socket read in 235 of them; delivery
p99 35.8 s → 7.2 s after a temporary 256-stripe override.

Goal: operations on **different** (tenant, ID) keys never wait for each other;
operations on the **same** key stay mutually exclusive; batch caching (one read +
one write per batch) is unchanged.

## What the code says

- **Threading model.** `Delivery.deliverMessagesFrom(shard)` holds an exclusive
  shard session, and an entity always lands in one shard, so two threads do not
  deliver to the same entity at once. The cache lock is therefore uncontended
  per key in normal delivery. All the measured contention is cross-key.
- **The repository-wide lock was never a designed guarantee.** The class was
  introduced in August 2019 with concurrent collections and no method-level
  lock. `synchronized` arrived on 2019-12-31 (`3bff3582fa4`) together with
  `System.err` debug output, while chasing the "cached entity not found" failure
  in `stopCaching()`. The debug code was removed three weeks later, but
  the `synchronized` stayed.
- **The lock is held across more than storage I/O.** `loadFn` is
  `Repository.doLoadOrCreate()` → `create(id)`, and `AggregateRepository`,
  `ProcessManagerRepository` and `ProjectionRepository` all post the
  `EntityCreated` system event from `create()`. That posting is synchronous in
  tests and whenever parallel posting is off, and every domain event dispatcher
  also listens on the system bus (`BoundedContext.registerEventDispatcher`). So
  code under the cache lock can reach an inbox write and, with a synchronous
  shard observer over several shards, a nested delivery that takes the cache
  lock of **another** key.
- **Latent bug in `stopCaching()`.** When no entity was cached, it logs a warning
  and returns **without** removing the ID from `idsToCache`. Reachable:
  `AbstractMessageEndpoint.dispatchTo()` turns a failing `loadFn` into an error
  outcome, so a batch during a storage outage ends with nothing cached. From then
  on single-message deliveries for that ID (which never call `onStart`/`onEnd`)
  load into the cache and "store" into memory only; nothing reaches the storage
  until a later multi-message batch ends. A throwing `storeFn` in `stopCaching()`
  leaves the same stuck state, plus the unflushed entity.
- **Nothing relied on the repository-wide serialization.** Checked the code
  reached from `doLoadOrCreate()` / `doStore()`: the in-memory storage
  synchronizes itself (`TenantRecords`, `MultitenantStorage`); lazily created
  state is `volatile` or synchronized (`EntityClass`, `AbstractEntityFactory`,
  `eventStorage()`, `stateHistoryStorage()`); entity record and journal writes
  carry no monitor of their own. Not checkable from here: user overrides of
  those two methods and third-party `RecordStorage` implementations — hence the
  Javadoc note below.

## Design

Java, in place. Converting the class to Kotlin would bury the locking change
in a rewrite diff.

### 1. `KeyLocks<K>` — new, package-private, `io.spine.server.entity`

A lock per key that exists only while a call holds or awaits it.

- The user count of a lock changes only inside `ConcurrentHashMap.compute()`,
  atomic per key, so a lock cannot be dropped while another call holds or
  awaits it. That is the "per-ID lock cleanup race" the Pure Photos override
  sidestepped with stripes.
- No I/O runs inside `compute()`; it only bumps a counter.
- `ReentrantLock` keeps the reentrancy `synchronized` had, and does not pin
  a virtual thread's carrier during I/O on JDK 21–23 the way a monitor does.
- Live locks ≤ calls in progress. Nothing to tune, nothing to leak.
- The lifecycle of a lock — count the caller, lock, unlock, discount the
  caller — lives in `acquire()` and `release()`, which `run()` and `evaluate()`
  share. `lock()` and `unlock()` therefore sit in different methods, and the IDEA
  inspection asking for them to share one `try`/`finally` is suppressed on
  `acquire()`.

### 2. `RepositoryCache` — same public API, same logic, narrower lock

- `cache` → `ConcurrentHashMap`, `idsToCache` → `ConcurrentHashMap.newKeySet()`
  (different keys now touch them concurrently).
- Drop `synchronized`; each method runs its existing body under the lock of
  its (tenant, ID) key.
- `load()` under caching keeps the explicit get → `loadFn` → put. **Not**
  `computeIfAbsent`: that would run I/O inside the map, blocking the bin, and
  would fail if `loadFn` came back to the cache.
- `stopCaching()` always ends the caching span for the ID — also when nothing
  was cached, and when the flush throws.
- Class Javadoc gains a short paragraph on the concurrency contract.

### 3. `Repository.java` — Javadoc only

One sentence each on `doLoadOrCreate()` and `doStore()`: they may now be called
concurrently for different entities.

## Relation to the Pure Photos override

Reference: `RepositoryCache.java` and `RepositoryCacheOverrideTest.java` as merged
in Neurogenesio/purePhotos#2506.

**Adopted**

- The semantics: every operation on a (tenant, ID) key is exclusive; different
  keys proceed in parallel; batch caching untouched.
- Its `stopCaching()` cleanup when nothing was cached.
- Its test scenarios — each maps to a case in `RepositoryCacheSpec`. Two are not
  carried over: `useBackendOverride` (classpath check, specific to an override)
  and `reloadAfterNullResult` (needs a loader returning `null`; the framework's
  loader is find-or-create and the package is `@NullMarked` — the real-world
  equivalent, a loader that *throws*, is covered instead).

**Deliberately different: exact per-key locks instead of 256 stripes**

| | 256 stripes (override) | per-key |
|---|---|---|
| Unrelated IDs sharing a lock | with N threads inside one repository's cache at once, up to ≈ (N − 1)/256 of operations queue behind another entity's I/O — about 6% at 16 threads, 22% at 64 | never |
| A tuning constant baked into the framework | yes | none |
| Lock ordering when code under the lock reaches another key | two threads can hold two colliding stripes in opposite order — needs a double collision plus a synchronous multi-shard observer, so very unlikely, but possible | a thread only locks keys of shards it owns, so no two threads want the same lock |
| Idle memory per repository | 256 stripes, each with a `HashMap` and a `HashSet`, allocated up front | nothing |
| Code | smallest; already proven in staging | +1 small class, +1 spec |

## Behavior changes (all intended)

1. Operations on different (tenant, ID) keys run concurrently — the fix.
2. `doLoadOrCreate()` / `doStore()` of one repository may run concurrently for
   different IDs. Worth a line in the release notes.
3. `stopCaching()` with nothing cached now ends the caching span instead of
   leaving the ID in caching mode forever (the override does the same).
4. `stopCaching()` whose flush throws now also ends the span; the exception still
   propagates. This goes beyond the override, which keeps the entity cached
   after a failed flush. Reason: the unflushed instance used to stay pinned in
   memory in caching mode, later single-message deliveries kept mutating it
   without ever writing, and the next batch flushed it over whatever another
   node stored meanwhile. Dropping it confines the damage to the failed batch.
   The cost: the batch's in-memory effects are gone on this node instead of
   being flushed by some later batch.

## Tests

`server/src/test/kotlin/io/spine/server/entity/` — `KeyLocksSpec` (8 cases) and
`RepositoryCacheSpec` (21 cases). "The second thread is waiting" is asserted by
polling `Thread.getState()` until the thread stays `BLOCKED` or `WAITING` over
several probes in a row. Timeouts only bound a failure. `KeyLocksSpec` also has
one free-running case, with many threads racing over a few keys.

`RepositoryCacheSpec` holds a thread inside each of the four places where the
cache calls the storage — a direct load, a load into the cache, a direct store
and a flush — and checks that another entity is served meanwhile. One of these
cases uses two IDs with the same hash code, which tells a lock per key from
a lock per hash.

## Verification

- Against the unchanged class, the first version of `RepositoryCacheSpec` failed
  in 4 of 16 cases, as listed under "Status".
- Both specs pass, 29 of 29, also in 20 consecutive runs made while another
  build was loading the machine:

  ```bash
  ./gradlew :server:cleanTest :server:test \
      --tests 'io.spine.server.entity.KeyLocksSpec' \
      --tests 'io.spine.server.entity.RepositoryCacheSpec'
  ```

- `./gradlew build dokkaGenerate` is green. It runs 2,073 tests of `server`,
  including the multi-thread, multi-shard suites with synchronous observers
  and synchronous system events — the nested-delivery configuration:
  `InMemoryDeliveryTest`, `InMemoryCatchUpTest` and `ReceptionFailureTest`.
- The code was broken on purpose in eleven ways, one at a time, in a scratch
  copy of the repository. Each break was caught, by the cases named below.

  | Break | Caught by |
  |---|---|
  | `KeyLocks` disposes a lock that is still awaited | "keep a lock awaited by another thread", the free-running case |
  | `KeyLocks` does not lock | "run actions on the same key one at a time", the free-running case |
  | `KeyLocks` never disposes a lock | the five cases checking `size()` |
  | The lock is not reentrant | "let an action lock its key again" |
  | One lock for all keys | "run actions on different keys in parallel" |
  | `load()` uses `computeIfAbsent()` | "… with the same hash code is loading into the cache" |
  | A lock per hash code | the same case |
  | A lock per ID, ignoring the tenant | "serve one entity while the other one is loading" |
  | `store()` outside the key lock | "when one thread flushes it and another one stores it" |
  | `startCaching()` outside the key lock | "when one thread flushes it and another one starts caching it" |
  | A shared lock around the direct store | "while another one is being stored" |

## Considered and not done

- **Lock only while an ID is being cached** (a slot created by `startCaching()`,
  removed by `stopCaching()`; no lock at all otherwise). Sound: the per-call
  lock gives no isolation across load → dispatch → store, so serializing
  uncached same-ID calls protects nothing — and the 2019 original had no such
  lock either. Not done because the issue asks that same-ID loads stay
  serialized and the override's test asserts exactly that for an uncached ID;
  keeping it makes this change a pure narrowing of lock scope.

## Noticed along the way — out of scope, read but not reproduced

- `InboxStorage.write()`, `writeBatch()` and `removeBatch()` are `synchronized`
  around storage I/O, and a `Delivery` has one `InboxStorage` — a JVM-wide
  monitor of the same kind as this issue.
- `SpecScanner.MemoizingUnpacker` keeps every distinct state `Any` it has seen in
  a `HashMap` that is never trimmed, for the lifetime of the record spec.
- A batch flushes an entity that was loaded but never stored; the non-batch path
  (`EntityMessageEndpoint.store()`) skips unmodified entities. Preserved as is.

[issue-1678]: https://github.com/SpineEventEngine/core-jvm/issues/1678
