# Migrate `ExtractId` off Guava's `Function`

**Status:** implemented and verified on `claude/guava-function-nullability-474d2d`
(2026-07-10); `./gradlew build dokkaGenerate` passes (3m 45s, all tests green).
**Goal:** remove the nullability noise forced on every `RecordSpec` call site
by Guava's `com.google.common.base.Function`.

## Root cause

`RecordSpec.ExtractId` (`server/src/main/java/io/spine/server/storage/RecordSpec.java`)
extends Guava's `Function<R, I>` and, to stay compatible with the parent's
nullness contract, declares its `apply` parameter `@Nullable`:

```java
public interface ExtractId<R extends Message, I> extends Function<R, I> {
    @Override
    @CanIgnoreReturnValue
    I apply(@Nullable R input);
}
```

The framework never passes `null`, yet the `@Nullable` parameter leaks into
every implementation site:

- Kotlin SAM lambdas see a nullable parameter and need
  `requireNotNull(...)` plus an explanatory comment
  (`EntityEventStorage.kt`).
- Java method references (`CatchUp::getId` etc.) trip IDEA's
  `ConstantConditions` inspection, suppressed with a
  "Protobuf getters do not return null" comment in five places.

This is the only use of Guava's `Function` in the repository.

## Plan

1. `RecordSpec.java`: extend `java.util.function.Function` instead;
   `apply(R input)` with no `@Nullable` (package is `@NullMarked`).
2. `SpecScanner.java`: anonymous `ExtractId` — drop `@Nullable` from the
   `apply` parameter, drop the now-dead `requireNonNull(input)`, drop the
   redundant `@NonNull` on `idFromRecord()`.
3. `EntityEventStorage.kt`: `{ event -> requireNotNull(event).id }` →
   `{ event -> event.id }`; delete the comment.
4. Remove the `@SuppressWarnings("ConstantConditions")` + comment tied to
   this in `CatchUpStorage`, `InboxStorage`, `MirrorStorage`,
   `DefaultTenantStorage`, `StgProjectStorage` (testFixtures).
5. Compile `server` (Java + Kotlin, all source sets), run storage-focused
   tests.

Out of scope: Guava `Supplier`/`Predicate` usages (mostly
`Suppliers.memoize`, a deliberate Guava API); `io.spine.query.Column.Getter`
(lives in spine-base, not this repo).

## Compatibility note

`ExtractId` no longer `instanceof` Guava `Function`. Lambda / method-ref
call sites (all known ones) are unaffected; only code assigning an
`ExtractId` to a Guava `Function`-typed variable would break, and none
exists in this repo or is known downstream.
