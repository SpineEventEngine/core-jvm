---
name: feedback-new-code-in-kotlin
description: New production classes are Kotlin, even next to Java files in a mostly-Java package; edits to existing Java files stay Java
metadata:
  type: feedback
---

Write every **new** class, production or test, in Kotlin. This holds even when the package and
its neighbours are Java, as in `io.spine.server`. Existing Java files that you only change stay
Java; converting them is a separate task (see the `java-to-kotlin` skill).

The rule is in `.agents/guidelines/jvm-project.md` ("**Languages**: Kotlin (primary), Java
(secondary)"). `testing.md` states it for tests only, which is easy to misread as "only tests
must be Kotlin".

**Why:** User feedback (2026-10-23, PR #1698): `ServedStateTypes` and
`DuplicateEntityStateException` were first written in Java because the surrounding classes were
Java, and had to be rewritten in Kotlin.

**How to apply:**
- Put a new class under `src/main/kotlin/<package>/`, not next to the Java files.
- A class that only same-package Java code uses becomes an `internal class` with public members.
  Public members of an internal class keep their JVM names, so Java callers need no `@JvmName`
  (compare [[java-to-kotlin-visibility-traps]]).
- When two objects must be told apart by identity, use `===`. Java's `==` needs an Error Prone
  `ReferenceEquality` suppression.
