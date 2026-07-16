# Task: Unify `store()` / `findOrCreate()` / `loadOrCreate()`

## Origin

Deferred from [`pull-inbox-and-cache-to-repository-class.md`](pull-inbox-and-cache-to-repository-class.md)
(*Out of scope*), which noted:

> Unifying `store()` / `findOrCreate()` / `loadOrCreate()` themselves. Their signatures
> diverge (`protected final void store(A)` vs `public final void store(P)`) and
> `Repository.store(E)` is abstract while `RecordBasedRepository.store(E)` is the real
> write — a much larger change.

Now that the inbox/cache pull-up has landed (`6d072b19d6`), this is what remains.

## Finding: the item is two separable problems

Investigating turned up that the deferred item bundles a **duplication** problem with
an unrelated **shape** problem. They have different answers.

### Problem 1 — duplication (solvable, no API change)

`ProcessManagerRepository` and `ProjectionRepository` carry four cache methods that are
identical, modulo one `var result =` temp:

```java
@Override protected final P findOrCreate(I id)   { return cache().load(id); }
@Override protected       P doLoadOrCreate(I id) { return super.findOrCreate(id); }
@Override public    final void store(P entity)   { cache().store(entity); }
@Override protected       void doStore(P entity) { super.store(entity); }
```

Their common parent is `EventDispatchingRepository`, and **those two are its only
subclasses anywhere in the repo**. So this is a PM↔Projection duplication, not the
three-way one the item's wording implies.

`AggregateRepository` is *not* part of it. Its versions genuinely differ:
- `store(A)` also appends the state history when recording is enabled;
- `doStore(A)` journals the emitted events, then writes the state, then commits;
- `doLoadOrCreate(I)` posts an `onEntityCreated(AGGREGATE)` lifecycle event via
  `createNew(id)`;
- `loadOrCreate(I)` is package-private, not `protected` — it is reached by
  `AggregateEndpoint` and `AggregateRoot`, not by the `RecordBasedRepository` contract.

### Problem 2 — shape (blocked by a visibility fork)

`Repository.store(E)` is `protected abstract`, while `RecordBasedRepository.store(E)`
is `public` **and is the real write**. So the base declares an extension point that its
own main subtree implements as the terminal operation. Inverting that — base `store()`
routes through the cache, abstract `doStore()` performs the write — is the natural shape
and would also delete `RecordBasedRepository.store` as an override.

It is blocked, structurally, by visibility:

| Class                      | `store(E)` today     | Why                                                              |
|----------------------------|----------------------|------------------------------------------------------------------|
| `Repository`               | `protected abstract` | —                                                                |
| `RecordBasedRepository`    | `public`             | widened; the real write                                          |
| `AggregateRepository`      | `protected final`    | extends `Repository` **directly**, so free to stay narrow        |
| `ProcessManagerRepository` | `public final`       | inherits `RecordBasedRepository`; Java cannot narrow an override |
| `ProjectionRepository`     | `public final`       | same                                                             |

A single `store()` on `Repository` must therefore pick one visibility:

- **`public`** → widens `AggregateRepository.store` from `protected`. User code could then
  store an aggregate directly, bypassing the transaction and lifecycle that
  `AggregateEndpoint` wraps around it. This looks like a deliberate design property to
  preserve, not an oversight.
- **`protected`** → narrows `RecordBasedRepository.store`. Breaks every external
  `repo.store(entity)` call: **44 call sites across 14 files in this repo's own tests**,
  plus unknown downstream usage. A breaking change that reclassifies the PR and forces a
  version re-bump.

Nothing else forces the issue: no class outside the three repositories overrides
`store(E)` anywhere in the repo, and every production caller of the cache-routed pair is
an internal endpoint (`AggregateEndpoint`, `PmEndpoint`, `ProjectionEndpoint`,
`AggregateRoot`).

## Decision

**Problem 1: done** (see *Outcome* below). **Problem 2: deferred, with an owner.**

Problem 1 was pure duplication with a near-zero-cost fix. Problem 2 is not duplication at
all — it is an awkward shape whose fixes each cost more than the tidiness buys on their
own.

Per the maintainer, Problem 2 is **larger than documented above**, and it has a natural
home: the `store` methods will have to unify when **event and state history is added for
Process Managers** (Phase F of the de-event-sourcing plan). That change gives
`ProcessManagerRepository.store()` the same journal-and-history shape
`AggregateRepository.store()` already has, which is what makes the unification worth its
cost — and it forces the `AggregateRepository.store` visibility question to be answered
deliberately rather than as a side effect of a tidy-up. Do not attempt Problem 2 before
then.

## Proposed change (Problem 1 only)

### `server/.../entity/EventDispatchingRepository.java`

Add the four methods, moved verbatim from the two subclasses:

```java
@Override
protected final E findOrCreate(I id) {
    return cache().load(id);
}

@Override
protected final E doLoadOrCreate(I id) {
    return super.findOrCreate(id);
}

@Override
public final void store(E entity) {
    cache().store(entity);
}

@Override
protected final void doStore(E entity) {
    super.store(entity);
}
```

`super.findOrCreate(id)` and `super.store(entity)` resolve through
`DefaultRecordBasedRepository` to `RecordBasedRepository` — the same targets they hit
from the subclasses today, so behavior is unchanged.

(As landed, `findOrCreate` and `store` route through the cache unconditionally — see
*Dissolved — the null-cache risk* below.)

### `server/.../procman/ProcessManagerRepository.java`
Delete `doLoadOrCreate`, `store`, `doStore`. **Keep `findOrCreate`** — see the correction
below.

### `server/.../projection/ProjectionRepository.java`
Delete the same three; keep `findOrCreate`.

### Correction — `findOrCreate` cannot be fully pulled up

The first draft of this plan said to delete `findOrCreate` from both subclasses. **That
would not compile.** `PmEndpoint` (package `io.spine.server.procman`) calls
`repository().findOrCreate(id)`, and `ProjectionEndpoint` (package
`io.spine.server.projection`) does the same. A `protected` member declared in
`io.spine.server.entity` is not reachable that way: cross-package `protected` access
works only through `this`/inheritance, never on another object's reference.

That is exactly what the existing `<p>Overrides to expose the method to the package.`
note on `ProcessManagerRepository.findOrCreate` means — the re-declaration is
**load-bearing**, not redundant, and the note is not stale. The same pattern already
appears on `ProjectionRepository.recordStorage()` and `Aggregate`.

So `findOrCreate` keeps a one-line re-declaration in each subclass:

```java
/**
 * {@inheritDoc}
 *
 * <p>Overrides to expose the method to the package.
 */
@Override
protected final P findOrCreate(I id) {
    return super.findOrCreate(id);
}
```

and the cache routing moves to `EventDispatchingRepository.findOrCreate`, which therefore
**cannot be `final`** (the subclasses must be able to re-declare it). Its `@implNote`
records why.

### Tests
`ProcessManagerRepositoryTest` and `ProjectionRepositoryTest` exercise both paths
already; expect no test changes.

## Dissolved — the null-cache risk

This section originally weighed two ways to survive a repository that has no cache: a
`hasCache()` fallback to the direct read/write, or accepting the
`IllegalStateException`. A `hasCache()` fallback was implemented.

**The maintainer then removed the premise** (2026-07-15). `Repository.registerWith()` now
calls `initCache()` *before* `initInbox()` and independently of it:

```java
if (!hasCache()) {
    initCache();
}
initInbox();          // builds an `Inbox` only if `setupInbox()` added endpoints
```

So the cache is **always on** and only the inbox is optional. There is no such thing as a
registered repository without a cache, and a missing one is an *initialization error* —
`cache()` fails rather than inviting callers to work around it.

The fallbacks are gone: `EventDispatchingRepository.store()` is `cache().store(entity)`
and `findOrCreate()` is `cache().load(id)`, unconditionally. `hasCache()` survives with a
single job — guarding the init call above; the rest of the code is expected to reach for
`cache()` and let it fail fast.

This is a better answer to the original risk than the fallback was. A downstream direct
subclass of the public `EventDispatchingRepository` still works, but now because the
resource it needs is always there, not because two call sites branch around its absence.

## Outcome

Landed. `EventDispatchingRepository` now owns `store`, `doStore`, `doLoadOrCreate`, and
the cache-routing half of `findOrCreate`; `ProcessManagerRepository` and
`ProjectionRepository` keep only the one-line `findOrCreate` re-declaration and mention
the cache nowhere at all. Eight methods across the two subclasses became four on the
parent plus two one-liners.

The invariant they rest on: **every registered repository has a cache; only the inbox is
optional.** `store()` and `findOrCreate()` therefore route through `cache()`
unconditionally, and an absent cache is an initialization error rather than a case to
handle. See *Dissolved — the null-cache risk*.

Review (`spine-code-review`) verified there is no recursion and no behavioral change: the
`super.findOrCreate(id)` / `super.store(entity)` calls in `doLoadOrCreate` / `doStore`
compile to `invokespecial` and bind statically to `RecordBasedRepository`, so they never
dispatch back into the cache-routed overrides. `super` resolved to the same
`RecordBasedRepository` methods before the move, so every endpoint path is unchanged.

Fixes applied from that review:
- `{@link #store(Entity)}` → `{@link #store(AbstractEntity)}`: in
  `EventDispatchingRepository`, `E extends AbstractEntity<I, S>`, so the erasure is
  `AbstractEntity`. The `Entity` form was copy-pasted from `Repository`, where
  `E extends Entity<I, ?>` makes it correct, and it silently resolved to nothing —
  neither the compiler nor `dokkaGenerate` flagged it.
- `Repository.doLoadOrCreate`'s `@implSpec` now notes that `EventDispatchingRepository`
  declares it `final`, so its descendants customize `create()` instead — the old wording
  advised an override that half the hierarchy can no longer perform.
- The "an override must only call `super`" constraint moved from `@implNote` to
  `@implSpec` (it binds overriders, it is not maintainer commentary).
- A third fix — routing the cache-less branches through `doLoadOrCreate`/`doStore` rather
  than repeating their bodies — was **superseded**: the always-on cache removed those
  branches entirely. Its reasoning is still worth keeping in mind for Phase F, since it
  generalizes: a *duplicated* body silently skips any step a future `doStore` grows, the
  way `AggregateRepository.doStore` grew event journaling.

### Test

`RepositorySpec` covers the lifecycle in five cases: a repository with no endpoints
gets a cache but no inbox; one with an endpoint gets both; an unregistered repository's
`cache()` fails as an initialization error; the default `checkDispatchesMessages()` does
not block registration; and `NoInboxDispatchingRepository` — a direct
`EventDispatchingRepository` subclass adding no endpoints — stores and loads through its
cache. That last stub is the **only** way to exercise an inbox-less
`EventDispatchingRepository`: both framework subclasses add endpoints unconditionally from
a `final setupInbox`.

The cache-less test was verified by mutation while the fallback still existed: reverting
`findOrCreate` to an unguarded `cache().load(id)` failed it with the no-cache diagnostic,
confirming it did not pass vacuously. Worth repeating the technique on any test written to
cover a branch no production subclass can reach.

### Not taken

The reviewer's nit #6 — give `ProcessManagerRepository`/`ProjectionRepository` a
package-private `final loadOrCreate(I)` forwarder (the `AggregateRepository.loadOrCreate`
pattern) so that `EventDispatchingRepository.findOrCreate` could be `final`. It closes a
hazard the reviewer itself called "contrived" (a downstream subclass overriding both
`@Internal setupInbox` *and* `findOrCreate` without calling `super`), and it churns the
`PmEndpoint`/`ProjectionEndpoint` call sites. Worth revisiting during Phase F, when these
methods are being reshaped anyway.

## Verification
- `./gradlew :server:build` — no proto changes, so `build`, not `clean build`.
- `./gradlew dokkaGenerate`.
- Reviewers: `spine-code-review`, `review-docs`.
- Version gate: branch is at `.471` vs master `.470` — already satisfied, no re-bump
  (unless Problem 2 is taken up, which would reclassify the PR as breaking).

## Out of scope
- Problem 2, per the recommendation above.
- `AggregateRepository`'s `store`/`doStore`/`loadOrCreate`/`doLoadOrCreate` — genuinely
  divergent, see above.
- Renaming `findOrCreate` ↔ `loadOrCreate` to one name. `AggregateEndpoint` and
  `AggregateRoot` call `loadOrCreate`; `PmEndpoint` and `ProjectionEndpoint` call
  `findOrCreate`. Unifying the *name* is cosmetic and independent of this change.
