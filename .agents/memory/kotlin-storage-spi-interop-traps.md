---
name: kotlin-storage-spi-interop-traps
description: Two traps when writing Kotlin over the Java storage SPI — `ExtractId` method refs fail under K2 (nullable Guava SAM), and proto-DSL builder properties shadow non-local names (outer-receiver members, top-level declarations), but not parameters or locals
metadata:
  type: project
---

Hit while writing `EntityEventStorage` (Phase D journal cleanup);
the planned `ProcessManager` journaling PR will meet both again.

1. **`RecordSpec.ExtractId` rejects Kotlin method references.**
   `ExtractId` extends Guava's `Function`, whose `apply` parameter is `@Nullable`, so
   under K2 `EntityEventRecord::getId` is "inapplicable" (nullable receiver). Use a
   lambda with an explicit guard instead:
   `RecordSpec(..., { record -> requireNotNull(record).id }, columns)` — and keep the
   one-line comment explaining the nullability comes from the SAM, not the framework.

2. **Inside a proto-DSL block, builder properties shadow non-local names, not parameters.**
   Kotlin resolves a bare name to locals and parameters first — of the function and of
   any enclosing lambda — and only then to the members of implicit receivers, innermost
   first, and to top-level declarations last.
   - Safe: `fun toRecord(event: Event) = entityEventRecord { this.event = event }`
     assigns the parameter, so no renamed local is needed.
   - The silent trap: a field name that collides with a non-local name. Such a name is
     a member of an outer receiver (the enclosing class, its companion, an outer `with`,
     `apply` or `alter` block) or a top-level declaration. Inside a class with
     a `seconds` property, `timestamp { this.seconds = seconds }` assigns the builder's
     own default back to it. Qualify the outer name (`this@Outer.seconds`), or bind it
     to a local before the block.

   An earlier version of this item said the opposite: that a parameter is shadowed.
   Two checks disproved it. A throwaway spec found that a parameter wins (see
   `.agents/tasks/spec-scanner-state-retention.md`). On 2026-10-07, a probe compiled
   with `kotlinc` 2.3.21 against a protoc-generated DSL (`protobuf-kotlin` 4.36.0)
   confirmed it: a function or lambda parameter wins, while a property of the enclosing
   class and a top-level property are both shadowed.

**Why:** both fail (1) or corrupt data (2) without any warning at the call site.

**How to apply:** when subclassing `MessageStorage`/building `RecordSpec` from Kotlin,
start from `server/src/main/kotlin/io/spine/server/entity/storage/EntityEventStorage.kt`
as the reference pattern (also shows the `@JvmField`/
`@JvmStatic` column-holder shape and `@JvmOverloads` for optional-parameter reads).
