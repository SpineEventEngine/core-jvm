---
name: feedback-serial-annotation
description: Annotate `serialVersionUID` with `@Serial` (`java.io.Serial`) in every new exception type, Kotlin or Java
metadata:
  type: feedback
---

Every `serialVersionUID` of an exception type gets the `@Serial` annotation. New exceptions
are Kotlin ([[feedback-new-code-in-kotlin]]), where the field lives in the companion object:

```kotlin
import java.io.Serial

public class DuplicateEntityStateException(...) : RuntimeException(...) {

    private companion object {

        @Serial
        private const val serialVersionUID: Long = 0L
    }
}
```

In Java: `@Serial private static final long serialVersionUID = 0L;` with `import java.io.Serial;`.

**Why:** User feedback (2026-10-23, PR #1698): `DuplicateEntityStateException` was added
without it. The existing code already follows this: `EntityVisibility.kt` and `Just.kt` in
Kotlin, `CommandException` and `InvalidCommandException` in Java. With the annotation, the
compiler checks that the field is declared correctly for serialization.

**How to apply:** whenever you add an exception class, or another `Serializable` class,
annotate its `serialVersionUID`. Check the same in a review of a new exception type.
