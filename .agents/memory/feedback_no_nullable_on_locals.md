---
name: feedback_no_nullable_on_locals
description: Never annotate a local variable with `@Nullable` — the annotation does not apply there; it belongs on parameters, return types, and fields
metadata:
  type: feedback
---

Do **not** put `@Nullable` on a local variable declaration:

```java
// ❌ Wrong — the annotation does not apply to a local.
@Nullable RuntimeException failure = null;

// ✅ Right.
RuntimeException failure = null;
```

Keep `@Nullable` on the locations where it *is* recognized — parameters, return types,
and fields:

```java
// ✅ Both are recognized locations.
protected static @Nullable RuntimeException
attemptClose(@Nullable RuntimeException failure, Runnable step) { ... }
```

**Why:** the nullness of a local is determined by flow analysis, not by declaration —
both JSpecify and the Checker Framework refine a local's type from its assignments, so
an annotation there is ignored and merely adds noise. This holds for
`org.jspecify.annotations.Nullable` and
`org.checkerframework.checker.nullness.qual.Nullable` alike.

**How to apply:** when writing or editing a method, annotate parameters/returns/fields
only. If you find `@Nullable` on a local in code you are already touching, drop it. If a
diff appears to have "lost" a `@Nullable` on a local, that removal is **correct** — do
not restore it, and do not attribute it to a stray formatter. Pre-existing instances in
untouched files (e.g. `GrpcContainer`, `SpecScanner`, `ArgumentFilter`, `CatchUpProcess`,
`Enricher`, `RecordQueryMatcher`) are not worth a dedicated sweep; fix them opportunistically.

Related: [[feedback_non_validated_annotation]].
