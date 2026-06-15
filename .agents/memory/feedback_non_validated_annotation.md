---
name: feedback-non-validated-annotation
description: Annotate @NonValidated on all functions that return a buildPartial() proto message
metadata:
  type: feedback
---

Always annotate the return type of a function with `@NonValidated` (`io.spine.validation.NonValidated`)
when the function returns a proto message built with `.buildPartial()`.

**Why:** User's instruction: "Do use `@NonValidated` annotation when you created stub messages
that are created using `buildPartial()`."

**How to apply:**
- `private fun stubEvent(): @NonValidated Event { ... return Event.newBuilder()...buildPartial() }`
- `private fun bareContext(): @NonValidated EventContext = EventContext.newBuilder()...buildPartial()`
- Applies to ALL message types — `Event`, `EventContext`, `Command`, `CommandContext`, etc.
- Does NOT apply to functions that use `.build()` (Kotlin DSL or plain builder): those pass validation.
- Does NOT apply to functions that delegate to another annotated function — but annotate the
  intermediate helpers if they themselves call `buildPartial()`.
