---
name: kotlin-storage-spi-interop-traps
description: Two traps when writing Kotlin over the Java storage SPI — `ExtractId` method refs fail under K2 (nullable Guava SAM), and proto-DSL receiver properties shadow function parameters
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

2. **Inside a proto-DSL block, receiver properties shadow outer parameters.**
   In `entityEventRecord { this.event = event }` the RHS `event` resolves to the
   builder's own property (default instance), silently self-assigning. Bind the
   parameter to a differently-named local before the block
   (`val emitted = event`) and assign from that. Applies to any generated
   `foo { }` DSL whose field names collide with locals/params in scope.

**Why:** both fail (1) or corrupt data (2) without any warning at the call site.

**How to apply:** when subclassing `MessageStorage`/building `RecordSpec` from Kotlin,
start from `server/src/main/kotlin/io/spine/server/entity/storage/EntityEventStorage.kt`
as the reference pattern (also shows the `@JvmField`/
`@JvmStatic` column-holder shape and `@JvmOverloads` for optional-parameter reads).
