# Task: Cache-backed, writable `RecentHistory`

## Origin

Batch dispatch work (2026-07-20): when an entity handles several signals in
one batch, every dispatch re-reads its recent history from storage (e.g., the
idempotency guard scans up to 100 events per signal). `RecentHistory` is a
stateless read-through view today. This task makes the family cache what it
has already loaded and become writable (`append`), so repeated reads within
an instance's lifetime are served from memory, and freshly produced items
become readable before they are durable.

**Scope: history classes only.** No repository-side `append` wiring — that is
the follow-up batch-caching task. Loader interfaces and their four in-repo
implementers change because the loader signature must support continuation
reads.

## Design facts (verified in code)

1. `HistoryStorage.historyBackward(entityId, batchSize, startingFrom)` already
   reads items with versions **strictly below** `startingFrom` — the
   continuation primitive. Without it, a remainder read of `depth − cached`
   would re-fetch the newest (already cached) items.
2. **All events of one dispatch share one version** (the pre-dispatch version;
   see `AggregateTransaction` KDoc and `EventEmitter.toSuccessfulOutcome`).
   A strict-less continuation from a mid-group boundary would drop
   same-version siblings ⇒ the cache tail must always end on a **complete
   version group**.
3. **State-history records never share a version** (`Phase.java`) — groups
   there are singletons.
4. `read()` must invoke the loader **eagerly** (lazy only in consumption):
   `AggregateRepositoryStateHistorySpec` asserts `IllegalStateException` from
   the `read` call itself, without traversing the iterator.
5. Entity dispatch is single-threaded per instance; no synchronization —
   documented assumption.

## Shape

- `RecentHistory<R : Any, T : Any, L : HistoryLoader<R>>`; abstract
  `toItem(record: R): T` and `versionOf(record: R): Version`; the abstract
  `load(loader, depth)` bridge is gone (base calls the typed loader directly).
- Cache: `ArrayDeque<Cached<T>>` newest-first (`Cached` = converted item +
  version); invariant: contiguous newest-first run, tail ends on a complete
  version group. `exhausted` flag skips the loader once storage below the
  tail is proven empty; reset by `useLoader`.
- `append(records)` (`@Internal`): chronological input, prepended to the
  head; contract — one call per dispatch with the complete version group.
- `read(depth)`: serve cache snapshot, then continue below it via
  `loader.load(depth − cached, oldestCachedVersion)`; loaded items populate
  the cache group-by-group under an identity guard (sibling iterators cannot
  corrupt the run). No-loader reads serve appended items now.
- `HistoryLoader.load(depth, startingFrom)` — no default value; implementers:
  `AggregateRepository` lambda, `AbstractEntityRepository` anonymous class,
  two specs.

Full plan: `.claude/plans/toasty-wibbling-leaf.md` (session-local).

## Verification

- `:server:test` for `RecentEventHistorySpec`, `RecentStateHistorySpec`,
  `AggregateRepositoryStateHistorySpec` (fail-fast contract), then full
  `./gradlew build`.

## Status

- [x] Loader interfaces extended
- [x] `RecentHistory` reworked (cache, append, group-aligned population)
- [x] Subclasses adapted
- [x] Repository loader implementations adapted
- [x] Specs extended
- [x] Build green (`./gradlew build`, 2026-07-20)
