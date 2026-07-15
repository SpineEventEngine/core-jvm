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

## Recommendation

**Do Problem 1. Leave Problem 2 alone.**

Problem 1 is pure duplication with a zero-cost fix. Problem 2 is not duplication at all —
it is an awkward shape whose only fixes each cost more than the tidiness buys. If the
inversion is wanted later, it belongs in a deliberate breaking release alongside a
decision about whether `AggregateRepository.store` should be public.

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

### `server/.../procman/ProcessManagerRepository.java`
Delete `findOrCreate`, `doLoadOrCreate`, `store`, `doStore`. Keep the
`{@inheritDoc}`-worthy prose by moving the "Overrides to expose the method to the
package" note onto the pulled-up member (it is stale anyway — the method is `protected`
on `RecordBasedRepository` already).

### `server/.../projection/ProjectionRepository.java`
Delete the same four.

### Tests
`ProcessManagerRepositoryTest` and `ProjectionRepositoryTest` exercise both paths
already; expect no test changes. Add a case to `RepositoryInboxSpec` only if the
null-cache guard below is adopted.

## Open question — the null-cache risk

`EventDispatchingRepository` is plain `public`, not `@Internal`. Today a downstream
direct subclass of it (neither a PM nor a projection) inherits
`RecordBasedRepository.store` and works. After the pull-up it would inherit
`store()` → `cache()`, and since it adds no inbox endpoints, `cache()` throws
`IllegalStateException`.

Only `ProcessManagerRepository` and `ProjectionRepository` extend it in this repo, so
nothing breaks here — but the class is public API. Two options:

1. **Guard** — route only when a cache exists:
   ```java
   @Override
   public final void store(E entity) {
       if (hasCache()) {
           cache().store(entity);
       } else {
           super.store(entity);
       }
   }
   ```
   Needs a `protected final boolean hasCache()` on `Repository`. Keeps direct subclasses
   working, at the cost of a branch that is dead for PM and projections.
2. **Accept** — declare that an `EventDispatchingRepository` is expected to configure
   inbox endpoints, and let the `IllegalStateException` (which now carries a diagnostic
   message naming the class) say so.

Recommend **1**: it is three lines, and `hasCache()` is independently useful.

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
