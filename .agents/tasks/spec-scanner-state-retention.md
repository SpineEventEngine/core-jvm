---
slug: spec-scanner-state-retention
branch: claude/silly-turing-5524ea
owner: claude
status: in-review
started: 2026-10-05
---

# `SpecScanner`: do not retain entity states across records

Issue: none filed. This is the second item of "Noticed along the way" in
`.agents/tasks/repository-cache-lock-per-entity.md` on the branch
`repository-cache-lock-per-entity` ([core-jvm#1678][issue-1678]).

## Status

Plan approved on 2026-10-05 with option 1a. The fix is implemented, verified and
committed on the branch `claude/silly-turing-5524ea`. Not pushed.

- [x] Call path verified by reading — see "What the code says"
- [x] `SpecScannerSpec` written. Against the unchanged class, 4 of its 13 cases fail,
      each on the assertion that states the defect. Both control cases pass —
      see "Reproduction"
- [x] Premises of the fix options checked by a throwaway probe — see "Premises"
- [x] Decision: option 1a, calling Protobuf directly — see "Design"
- [x] Fix in `SpecScanner` — see "As implemented"
- [x] Verification — see "Verification"
- [x] Reviews — see "Reviews"
- [x] Version bump to `2.0.0-SNAPSHOT.562`, as the branch of [core-jvm#1678][issue-1678]
      uses `.561`; dependency reports regenerated
- [x] Follow-up: `StateUnpacker` replaced with `AnyPacker.unpack()` of Base
      `2.0.0-SNAPSHOT.460` — see "As implemented"
- [ ] Pull request

## Problem

`SpecScanner.scan()` created one `MemoizingUnpacker` and captured it in the getter of
every state-based column of the `RecordSpec` it returns. The unpacker held
`Map<Any, S> cache`, which `synchronized process(Any)` filled and nothing trimmed.
`EntityRecordStorage` scans once, in its constructor, so the map lived as long as
the storage.

For an entity type that declares at least one `(column)` field, this gave:

1. **Unbounded heap growth.** Every distinct packed state written through the storage
   stayed reachable: the packed bytes as the key, the unpacked message as the value.
2. **A monitor per storage on the write path.** The state was parsed inside it.

The Javadoc called the class a "scoped cache". Its scope had been the lifetime of the
storage since the class appeared (`05ffa691ac4`, 2023-10-27), so this was not
a regression.

Goal: unpack the state once per record when several columns are read, keep no state
after the record, and take no shared lock.

## What the code says

Read at `d6a73060f96`, before the fix. Paths are under `server/src/main/`.

- **The map was never trimmed.** `SpecScanner.java:264` declared it; the only accesses
  were `cache.get()` (:271) and `cache.put()` (:276). The class was `private`, and its
  only users were `scan()` (:148) and `getter()` (:184, :192).
- **One unpacker per scanned class, shared by all its state-based columns** (:148–:156).
- **It lived as long as the storage.** The `EntityRecordStorage` constructor (:86)
  passes the scanned spec to `StorageFactory.createRecordStorage()`. `RecordStorage`
  keeps the spec in a `final` field (`RecordStorage.java:46`), both in the delegate
  and, through the `DelegatingRecordStorage` constructor (:65), in the
  `EntityRecordStorage` itself. A `Repository` keeps its storage until `close()`
  (`Repository.java:105`, :354, :602).
- **Every write computes every column.** `RecordSpec.valuesIn()` (:254) calls
  `valueIn()` of each column, and `RecordColumn.valueIn()` calls the getter
  (Base `2.0.0-SNAPSHOT.450`). `RecordWithColumns.create()` (:74, :87) calls
  `valuesIn()`. The callers on the write path:
  - `RecordBasedRepository.toRecord()` (:480), called by `doStore()` (:111) and by
    the bulk `store(Collection)` (:222);
  - `EntityRecordStorage.write(I, EntityRecord)` (:202).
- **All entity kinds were affected.** `AggregateRepository`, `ProcessManagerRepository`
  and `ProjectionRepository` extend `RecordBasedRepository` through
  `AbstractEntityRepository`. The `doStore()` overrides in
  `SignalDispatchingRepository.kt` (:342) and `AggregateRepository.kt` (:170) call `super`.
- **Only writes were affected.** The in-memory storage matches and sorts by the stored
  column values (`RecordQueryMatcher`, `RecordComparator`); it does not call the getters.
- **Two conditions.** The unpacker was reached only when the entity state declares
  a column, and the state of the record is not the default `Any` (:187).
- **The monitor was not contended on the main path in `master`.** `process()` was
  `synchronized` (:270), but `doStore()` is called only from `RepositoryCache.store()`
  and `stopCaching()` (`RepositoryCache.java:158`, :187), which are `synchronized`
  on the cache of the repository. The bulk `store(Collection)` and
  `EntityRecordStorage.write(I, EntityRecord)` do not pass through the cache.
- **Why the cache existed.** In Base `2.0.0-SNAPSHOT.450`, `AnyPacker.unpack(Any, Class)`
  parses the bytes on every call. It bypasses `Any.unpack(Class)`, which would look up
  the default instance reflectively, and with it the memo that an `Any` keeps for
  the unpacked message.
- **Not affected by the growth:** `MirrorToEntityRecord.apply()` (`migration/mirror/`,
  :87) scans anew for every record, so its unpacker died with the call.

## Reproduction

`server/src/test/kotlin/io/spine/server/entity/storage/SpecScannerSpec.kt`: 10 cases
of its own and 3 inherited from `UtilityClassTest`. A case passes 1,000 distinct states
of one entity through the code under test. Retention is observed in two independent
ways. Neither needs reflection.

- **Reachability.** The case keeps weak references to the packed states, runs a full
  garbage collection (`GcFinalization.awaitFullGc()`) while the specification, or
  the storage or the repository holding it, is kept alive, and counts the references
  that are not cleared. The cases that write through a storage or a repository leave
  out the latest state, because an in-memory storage holds the latest record.
  The case that reads through a specification checks all the states, and also their
  unpacked form: it keeps weak references to a `Timestamp` column value, which is
  a part of the unpacked state.
- **Identity.** A message unpacked anew has new instances of its `String` and message
  fields. Getting the same instance of a column value twice means that one unpacked
  state served both readings.

Result against the unchanged class:

| Case | Result |
|---|---|
| create a specification which › does not retain the states of the records it read | **fails:** 1,000 of 1,000 packed and 1,000 of 1,000 unpacked states still reachable |
| create a specification which › unpacks anew the state of a record equal to an earlier one | **fails:** 1,000 of 1,000 served from memory |
| not retain the states written earlier through › an `EntityRecordStorage` | **fails:** 999 of 999 still reachable |
| not retain the states written earlier through › a repository | **fails:** 999 of 999 still reachable |
| … › an `EntityRecordStorage` of an entity without state-based columns (control) | passes |
| … › a repository of an entity without state-based columns (control) | passes |
| create a specification which › reuses the unpacked state when reading the columns of the same record | passes |
| … › gives each of the concurrent readers the values of its own record | passes |
| … › fails on a record with a state of another type | passes |
| … › fails on a record with a state that cannot be unpacked | passes |
| the three cases inherited from `UtilityClassTest` | pass |

The controls run the same scenario for an entity type that needs no unpacking. They
show that neither the storage, nor the repository, nor the harness retains the states.

The `a repository` case stores through `Repository.store()`, so it exercises
`RecordBasedRepository.toRecord()`.

The first version of the spec had 7 cases of its own. Its specification-level case
watched only the packed states and left the latest one out, which gave 999 of 999.
The three other failing cases gave the numbers above from the start.

## Premises

A throwaway spec, deleted after the run, checked the facts the fix rests on.
All six cases passed with Protobuf `4.36.0` and Base `2.0.0-SNAPSHOT.450` on JDK 17.

- `AnyPacker.unpack(any, cls)` returns a new instance on every call for the same `Any`.
- `any.unpackSameTypeAs(exemplar)` returns the same instance on a repeated call.
- An equal `Any` of another instance is unpacked anew.
- The message that an `Any` remembers is collected together with that `Any`.
- `EntityRecord.getState()` returns the same `Any` instance every time.
- A wrong type gives `UnexpectedTypeException` in `AnyPacker` and the checked
  `InvalidProtocolBufferException` in `unpackSameTypeAs()`.

## Not verified

- **Other storage implementations.** Read in the local clones of `jdbc-storage` and
  `gcloud-jvm` (`master` of 2026-10-03), not run. Both hand the spec to the
  `RecordStorage` constructor. `jdbc-storage` also caches it in `TableSpecs.tables`,
  which the storage factory owns, so there the map outlived even a closed storage.
  Those storages keep no records in memory, so every distinct state written since
  the start stayed.
- **The size of the growth in a deployment.** Not measured. An entry held the packed
  bytes and the unpacked message of one state.
- **Contention on the monitor.** Not measured. None was expected on `master`; see
  above. It would have become a lock shared by all entities of a type once
  `RepositoryCache` locks per entity: the plan of [core-jvm#1678][issue-1678] says
  `doStore()` may then run concurrently for different IDs. Inferred from that plan;
  the two changes were not run together.
- **IntelliJ IDEA inspections** on the change. They cannot be run from this session.
  The profile was read instead.

## Design

The getters of the state-based columns are independent functions of an `EntityRecord`.
So the unpacked state must live somewhere between two column readings of one record.
There are three places.

| Where the unpacked state lives | How | What stays after the record | Shared lock |
|---|---|---|---|
| **1. In the `Any` of the record** | `Any.unpackSameTypeAs(exemplar)`: Protobuf keeps the unpacked message in the `Any` instance and needs no reflection | nothing in `SpecScanner`; the unpacked state lives exactly as long as that `Any` instance | none |
| 2. In the specification | the unpacker keeps only the latest (`Any`, state) pair in a `volatile` field and compares the `Any` by identity | one state per storage, until the next write | none |
| 3. On the call stack | `RecordSpec.valuesIn()` unpacks once and hands the state to the state-based columns | nothing | none |

**Chosen: 1.** It deletes the cache instead of repairing it. `SpecScanner` gets no
mutable state and no concurrency reasoning. Concurrent writes of different records
cannot disturb each other, because each record carries its own memo. It is also
the mechanism `AnyPacker.unpack()` adopts in the `base-libraries` task
`memoize-any-unpack`, started on 2026-10-05.

There were two ways to get there.

- **1a. Now, calling Protobuf directly — approved and implemented first.**
- **1b. After Base, through `AnyPacker`.** Not taken at first: the growth and
  the monitor would have stayed until the Base release. Done as the follow-up once
  Base `2.0.0-SNAPSHOT.460` shipped the memoizing `AnyPacker.unpack()`.

### As implemented

Only `SpecScanner.java` changes, and almost only by removal.

- `MemoizingUnpacker` is deleted.
- The getter of a state-based column calls `AnyPacker.unpack(state, stateClass)`,
  which `SpecScanner` already imported. Since Base `2.0.0-SNAPSHOT.460`, that method
  delegates to `Any.unpackSameTypeAs()`, and the class Javadoc of `AnyPacker`
  documents the memoization.
- A two-line comment at the call says why the getters need no cache of their own.

The first version, on Base `2.0.0-SNAPSHOT.450`, had a private `@Immutable`
`StateUnpacker` with the body that `AnyPacker.unpack()` got in `.460`:
`packed.unpackSameTypeAs(defaultState)`, wrapping the checked
`InvalidProtocolBufferException` in `UnexpectedTypeException`. It was removed after
the Base bump. With it went the compile-time guard against a cache field; the
retention cases of the spec remain the guard.

## Behavior changes

1. A specification holds no entity state — the fix.
2. The column getters take no lock.
3. The `Any` of a record whose columns were read keeps the unpacked state for as long
   as that `Any` instance lives. Storages that keep no records in memory see no change.
   The instance outlives the write only where something keeps it:
   - `InMemoryRecordStorage`: each stored record of an entity type with columns holds
     the unpacked state next to the bytes.
   - Messages sharing the instance with a stored record: `EntityQueryProcessor` puts
     the stored `Any` into a query response by reference, and `MirrorToEntityRecord`
     takes it from the `Mirror`. So with an in-memory storage, a query response and
     an in-memory `MirrorStorage` hold the unpacked state too.
   - A record the caller keeps after `EntityRecordStorage.write(I, EntityRecord)`.
4. A state of another type still gives `UnexpectedTypeException`. It now wraps
   the exception of Protobuf and no longer names the expected and the actual type.
5. The type URL prefix is no longer compared when the columns are read; Protobuf
   matches by the type name. A state packed under another prefix now passes the
   getters (verified by running), as it always did for an entity without columns.
   With Base `2.0.0-SNAPSHOT.450`, reading such a record back failed. With `.460` it
   does not: `TypeUrl` splits at the last slash, `getMessageClass()` resolves the class
   by the type name, and `AnyPacker.unpack(Any)` no longer compares the prefix either
   (read in the sources, not run). `Repository.store()` always packs with the right
   prefix, so only a hand-built record can be affected.

Changes 4 and 5 are the ones decided for `AnyPacker.unpack()` in the `base-libraries`
task, so the switch to `AnyPacker` in the follow-up changed nothing.

One theoretical exposure comes with change 3. `Any.unpackSameTypeAs()` and
`Any.unpack()` throw when the `Any` already remembers a message of another Java class,
for example a `DynamicMessage` of the same type. User code doing that to the state
of a query response, in the same JVM and over an in-memory storage, would fail. Nothing
in this repository, `jdbc-storage`, `gcloud-jvm` or `validation` calls these methods.

## Tests

`SpecScannerSpec` has the four retention cases and the two controls listed under
"Reproduction", and four more cases that pass against the unchanged class as well:

- "reuses the unpacked state when reading the columns of the same record" is the only
  case that notices if a new version of Base or Protobuf stops remembering
  the message. With the getters calling `AnyPacker`, its passing also proves that
  the `AnyPacker` on the classpath memoizes: the one of `.450` did not.
- "gives each of the concurrent readers the values of its own record": eight threads
  read 1,000 records each, twice in a row, for 100 rounds. The fix removes
  a `synchronized`, so this guards a later change that would share state again.
- "fails on a record with a state of another type" and "fails on a record with a state
  that cannot be unpacked" pin `UnexpectedTypeException` for both ways the unpacking
  can fail.

## Verification

### First version, with `StateUnpacker`, on Base `2.0.0-SNAPSHOT.450`

- Against the unchanged class the spec fails in 4 of 13 cases, as listed under
  "Reproduction". The same four failed in every earlier run.
- Against the fix the spec passes, 13 of 13, in 20 consecutive runs. The build cache
  must be off. With it, `cleanTest test` restores the passed task from the cache
  and runs nothing, yet ends with `BUILD SUCCESSFUL`.

  ```bash
  ./gradlew :server:cleanTest :server:test --no-build-cache \
      --tests 'io.spine.server.entity.storage.SpecScannerSpec'
  ```

- The fix was broken on purpose, one break at a time. Each break was caught.

  | Break | Caught by |
  |---|---|
  | The map is back: the unchanged class | the four retention cases |
  | No memo: `AnyPacker.unpack()` of Base `2.0.0-SNAPSHOT.450` in the unpacker | "reuses the unpacked state when reading the columns of the same record" |
  | A cache field in `StateUnpacker` | the compiler: ErrorProne `[Immutable]` |
  | `@Immutable` removed, a racy memo of two `volatile` fields | "does not retain the states of the records it read", 1 packed and 1 unpacked state, in 23 runs of 23; "gives each of the concurrent readers …", in 9 runs of 10 at 100 rounds |

  On the final code, the racy memo of this break also parses without checking the type,
  so "fails on a record with a state of another type" caught it as well, in 5 runs of 5.

  The concurrency case took three attempts. Reading each record once, it caught nothing
  in 3 runs. Reading twice for 10 rounds, it caught the racy memo in 1 run of 5.
  With 100 rounds it caught it in 9 runs of 10. It takes about 0.8 s against the fix.

- `./gradlew build dokkaGenerate` is green on the final code. The tests of `server`
  (2,057), `server-testlib` (159) and `server-otel` (22) were executed. Those of `core`,
  `client`, `core-testlib` and `client-testlib` were up to date: these modules do not
  depend on `server`, and their 396 tests passed in the previous full build.
  `:server:detekt`, `checkstyleMain`, `pmdMain` and both Dokka publications ran.
  ErrorProne reports nothing for `SpecScanner.java`.

### Final version, calling `AnyPacker.unpack()`, on Base `2.0.0-SNAPSHOT.460`

- `spine-base` resolves to `2.0.0-SNAPSHOT.460` for the tests of `server`
  (`:server:dependencyInsight`).
- The published `AnyPacker.unpack(Any, Class)` was read at the merge commit of
  `base-libraries` PR #966: its body is the one `StateUnpacker` had.
- Against the original class, under `.460`, the spec still fails the same four
  retention cases with the same numbers: 999, 999, 1,000 and 1,000, and 1,000.
- Against the final code, the spec passes, 13 of 13, in 20 consecutive runs with
  the build cache off.
- `./gradlew build dokkaGenerate --no-build-cache` is green. All seven test tasks
  were executed: 2,634 tests, of which `server` has 2,057. `:server:detekt`,
  `checkstyleMain`, `pmdMain` and both Dokka publications ran. ErrorProne reports
  nothing for `SpecScanner.java`.

## Reviews

**Before the implementation**, an independent agent was briefed to refute option 1a.
It found no code path that breaks. Its findings, and what was done:

| Finding | What was done |
|---|---|
| The spec watched only the packed states, so a design keeping the unpacked ones would pass | The specification-level case also watches the unpacked states, and checks all of them |
| The unpacked state lives as long as the `Any` instance, which query responses and mirrors share | Behavior change 3 says so |
| In `jdbc-storage` the spec is cached by the storage factory | "Not verified" corrected |
| No concurrent case | Added, then strengthened until it caught a racy memo |
| An eager lookup of the default state fails for a state class without `getDefaultInstance()`, even with no columns | The unpacker is created per getter |
| `unpackSameTypeAs()` ignores the type URL prefix | See "Considered and not done" |

**After the implementation**, three reviewers read the change.

- `spine-code-review`: approve with changes. The `catch` branch had no test; the case
  "fails on a record with a state that cannot be unpacked" covers it now.
- `kotlin-engineer`: approve. The reader threads are daemons now, and the helpers
  lost an unused type parameter and a misleading KDoc.
- `review-docs`: changes requested for this file, which pointed to a missing section
  and lagged the work. The wording corrections for the Javadoc and the KDoc are applied.

## Considered and not done

- **A comparison of the whole type URLs before unpacking**, as `AnyPacker` of Base
  `2.0.0-SNAPSHOT.450` did. It was implemented on the first reviewer's finding, and
  it kept behavior changes 4 and 5 away. Then removed: it was not in the approved plan,
  and the `base-libraries` task decided the opposite for `AnyPacker` itself, so the
  check would have made the follow-up of option 1b change behavior again.
- **A bounded or weak-keyed cache** (`maximumSize`, `weakKeys()`). Keeps the map and
  adds eviction to maintain. With weak keys, a stale entry holds its unpacked state
  until the next write cleans it up.
- **No memo at all.** One parse per column per record. It contradicts the goal.
- **A `ThreadLocal` slot.** Avoids the eviction of option 2, but pins one state per
  thread per storage.
- **Option 3.** It needs a new hook in the public `RecordSpec` and a second way to
  compute the same values. "Unpacked once" could not be observed by a test.

## Noticed along the way — out of scope, read but not reproduced

- `MirrorToEntityRecord.apply()` runs the reflective `SpecScanner.scan()` for
  every migrated record.
- `StateColumns`, in the package of `SpecScanner`, has no usages.
- `EntityRecordStorage.write(I, EntityRecord)` is `synchronized` around the write
  to the underlying storage.
- Test fixtures keep scanned specs in static fields (`EntityRecordStorageTestEnv`,
  `GivenStorageProject`, `ProjectionColumnTest`), so before this change they held
  the map for the life of the test JVM. Nothing else to do.
- The plan of [core-jvm#1678][issue-1678] quotes `cleanTest test` without
  `--no-build-cache` for its 20 consecutive runs. Whether those runs were executed
  or restored from the cache was not checked.
- `.agents/memory/kotlin-storage-spi-interop-traps.md` says that the properties of
  a Protobuf DSL receiver shadow function parameters. A throwaway spec showed the
  opposite: a parameter wins over the DSL property of the same name, and only
  a top-level declaration is shadowed.

## Log

- 2026-10-05 — Call path read. `SpecScannerSpec` written and run three times against
  the unchanged class, with the same result each time: 6 of its 10 cases passed,
  4 failed as intended. `:server:detekt` passed with the spec in place.
- 2026-10-05 — Premises checked by a throwaway spec, 6 of 6, then deleted.
- 2026-10-05 — `base-libraries` task filed for `AnyPacker.unpack()`.
- 2026-10-05 — Drafted, awaiting approval.
- 2026-10-05 — Plan approved with option 1a.
- 2026-10-05 — The independent review arrived: option 1a survives. The spec extended
  to 13 cases; 4 fail against the unchanged class.
- 2026-10-05 — `StateUnpacker` implemented, first with a comparison of the type URLs.
  Verified: 20 runs, four breaks, full build.
- 2026-10-05 — Three reviews. The comparison of the type URLs removed after reading
  the decisions of the `base-libraries` task. The spec adjusted to it.
- 2026-10-05 — Verified again on the final code: the unchanged class, three breaks,
  20 runs, full build.
- 2026-10-05 — Committed: the version bump to `.562`, the fix with its spec, and the
  dependency reports. After the bump, `./gradlew build --no-build-cache` is green:
  all seven test tasks executed, 2,634 tests.
- 2026-10-06 — Base bumped to `2.0.0-SNAPSHOT.460`, which ships the memoizing
  `AnyPacker.unpack()`. `StateUnpacker` removed; the getters call `AnyPacker`.
  Verified: the original class under `.460`, 20 runs, full build with Dokka.

[issue-1678]: https://github.com/SpineEventEngine/core-jvm/issues/1678
