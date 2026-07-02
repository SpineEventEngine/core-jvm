# Custom column types in ordering-comparison filters (issue #1217)

GitHub: https://github.com/SpineEventEngine/core-jvm/issues/1217

## Goal

Allow custom orderable types in `Filters.gt/lt/ge/le` (and `QueryFilter` /
`EntityStateFilter` / `EventFilter`). Replace the hardcoded allow-list in
`Filters.checkSupportedOrderingComparisonType` with the real orderability rule:
**a type qualifies iff it is `Comparable` (e.g. via the `(compare_by)` proto option) or
has a `Comparator` registered in `io.spine.compare.ComparatorRegistry`.**

"The proper way" for a user: mark the custom message type with `(compare_by)`, or
register a `Comparator` in `ComparatorRegistry`.

## Steps

1. `client/src/main/java/io/spine/client/Filters.java` — rewrite
   `checkSupportedOrderingComparisonType` to `Comparable || ComparatorRegistry.contains`;
   remove `isSupportedNumber`; add `import io.spine.compare.ComparatorRegistry;`; update
   the class-level "Supported Types" section + the method Javadoc.
2. `core/src/main/proto/spine/core/version.proto` — add
   `option (compare_by) = { field: "number" };` so `Version` implements `Comparable`.
3. Docs — `QueryFilter` / `EntityStateFilter` / `EventFilter` "supported types" notes;
   pointer in `AbstractColumnMapping`.
4. Tests — `client/.../FiltersTest.java`: flip `forEnums` / `forUnsupportedTypes`; keep
   numbers/strings/timestamps/versions/AtomicInteger; add `(compare_by)`/registry
   acceptance + a `Version`-is-`Comparable` guard.
5. Server end-to-end ordering test against in-memory storage (fills the coverage gap).

## Verification (status)

- [x] `./gradlew clean build` (Corretto 17) — BUILD SUCCESSFUL, 0 test failures (~1930+ tests).
- [x] Focused: `FiltersTest`, `RecordQueryMatcherTest`, `VersionMixinSpec`, `AggregateTest` — all green.
- [x] `spine-code-review` — APPROVE WITH CHANGES (addressed: clarifying comment on the
      registry `Duration` test; the proto trailing `//` is conventional and kept).
- [x] `review-docs` — APPROVE (addressed: restrictive "which" → "that" in `Filters` Javadoc).
- [ ] `./gradlew dokkaGenerate` — running.

## Fallout handled

- Making `Version` `Comparable` created one `assertThat(...)` overload ambiguity
  (`Truth` vs `ProtoTruth`) at `server/.../aggregate/AggregateTest.java:317`. A full
  `testClasses --continue` compile confirmed it was the ONLY site; fixed by casting the
  argument to `Message` to keep the prior `ProtoTruth` resolution.

## Notes / out of scope

- Runtime `ComparatorRegistry` wiring for registered-but-non-`Comparable` types
  (`Duration` in-memory filtering; base's `io.spine.query.ComparisonOperator`) — separate
  repo, not needed for `(compare_by)`/`Comparable` types.
- Behavior change: enums and `Calendar` (both `Comparable`) become accepted.

Delete this file on merge to master.
