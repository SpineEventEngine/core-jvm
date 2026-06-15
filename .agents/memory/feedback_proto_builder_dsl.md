---
name: feedback-proto-builder-dsl-in-tests
description: DSL vs plain Java builder chains in Spine test code — apply{} is rejected
metadata:
  type: feedback
---

Use Kotlin Protobuf DSL (`foo { field = value }`) ONLY when building proto messages completely (`.build()`, all required fields set).
For `buildPartial()`, use plain Java builder chains (`Foo.newBuilder().setXxx(...).setYyy(...).buildPartial()`).
Never use `.apply { }` with proto builders in test code.

**Why:** User's instruction: "The `apply` syntax here is silly. The instruction to use Kotlin Protobuf DSL to the cases when we don't need to call `buildPartial()`.
When we do, just use plain Java builders as before."

**How to apply:**
- Complete build → DSL: `actorContext { actor = userId; timestamp = ts; tenantId = tn }`
- Partial build → plain chain: `EventContext.newBuilder().setCommandContext(ctx).setTimestamp(ts).buildPartial()`
- NEVER: `EventContext.newBuilder().apply { setTimestamp(ts) }.buildPartial()` ← rejected
