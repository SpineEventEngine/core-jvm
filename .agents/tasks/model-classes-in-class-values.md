---
slug: model-classes-in-class-values
branch: claude/classvalue-migration-analysis-868972
base: claude/remove-deprecated-api-cad139
owner: claude
status: in-progress
started: 2026-11-23
---

## Goal

Associate model classes with Java classes through `ClassValue`s, one per kind, instead of
`ClassMap`. Move the lookup of the Bounded Context of a class into `BoundedContextNames`,
backed by a `ClassValue` too, and remove `Model`.

## Context

- The base branch removed the duplicate-handler check of `ClassMap`. What remained was one
  JVM-wide cache from a Java class to its `ModelClass`, split by context name for no reason.
- Every factory went through `Model.inContextOf`, which is `static synchronized`, and every
  new entity instance calls a factory once.
- The cache was keyed by the class name, so two classes of one name in two class loaders
  shared an entry, and it kept classes and their loaders forever.
- A more specific kind replaced the stored one, so the result of `asEntityClass(MyAggregate)`
  depended on which kind was built first. Now each kind has its own `ModelClassValue`:
  `asEntityClass(MyAggregate)` always gives a plain `EntityClass`.
- A `ClassValue` keeps a value as long as its class lives, gives every caller one instance,
  and keeps nothing when the computation throws. It is not a cache, so the names follow
  `LoggerClassValue` of Spine Logging: `ModelClassValue` (Kotlin), held in private static
  `classValue` fields; the enclosing model class names the kind.
- Without `ClassMap`, `Model` held only the lookup of the context of a class. The lookup
  implements the convention of `@BoundedContext`, which lives in `io.spine.core`, so it moved
  to `BoundedContextNames`. `core` now declares `implementation(Reflect.lib)` for
  `PackageInfo`; `spine-reflect` was already on its runtime classpath through `spine-base`.

## Plan

- [x] Replace `ModelClass.get(...)` with `ModelClassValue`, and give each of the 8 kinds
      its own instance.
- [x] Remove `ClassMap`.
- [x] Move the context lookup into `BoundedContextNames.contextOf(Class)`; remove `Model`;
      `StateSubscriberMethod` uses the new method.
- [x] Add `ModelClassValueSpec`; move the lookup cases and the `tres.quattro`/`uno.dos`
      fixtures to `core`'s tests; remove `ModelTest`.
- [x] Note the changes in `docs/migration/unregistered-tenants.md`.
- [x] Bump the version to `.750`: a breaking change, since a protected method of a public
      class is removed.
- [ ] Verify with `./gradlew build dokkaGenerate`.

## Log

- 2026-11-23 — planned; branch fast-forwarded to the base branch (`b7ef52008f9`).
- 2026-11-23 — the base branch merged into `master` as PR #1729 (`c0e7b766d16`, no file
  changes in the merge); the base of this branch is `master` now.
- 2026-11-23 — `ClassValue` caches verified with `check dokkaGenerate` and `build`;
  then the context lookup moved from `Model` to `BoundedContextNames`.
- 2026-11-23 — rebased onto `claude/remove-deprecated-api-cad139` (`b38561b4f2f`, `.740`),
  which removes `AggregatePartClass`; the `.730` bump was dropped for `.750`.
- 2026-11-23 — `ModelClass.cacheOf()` replaced with the Kotlin `ModelClassValue`; the
  `cache` fields renamed to `classValue`.
