---
name: feedback-no-screaming-mutable-static-fields
description: A mutable `static final` field (e.g. an `AtomicLong` counter) is not a constant; name it in `lowerCamelCase`, not `SCREAMING_SNAKE_CASE`
metadata:
  type: feedback
---

A `static final` field whose object can change, such as an `AtomicLong` counter, a map,
or a set, is not a constant. Name it in `lowerCamelCase`:

```java
private static final AtomicLong testContextCounter = new AtomicLong();
```

No lint rule here asks for `SCREAMING_SNAKE_CASE`: PMD checks only class, method and type
parameter names, and Checkstyle checks no names. The existing code agrees:
`CommandScheduler.scheduledCommandIds`, `Model.models`. For Kotlin, the style guide prefers
`lowerCamelCase` even for real constants (`.agents/guidelines/kotlin-code-style.md`).

**Why:** User feedback (2026-10-27, branch `claude/one-context-per-name`): a counter named
`TEST_CONTEXT_COUNTER` in `BoundedContextBuilder` was renamed to `testContextCounter`.
"There is no need to SCREAM about it. It is not a constant."

**How to apply:** when adding a mutable `static final` field in Java, use `lowerCamelCase`.
Keep `SCREAMING_SNAKE_CASE` for deeply immutable values only, as the surrounding code does.
