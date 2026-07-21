---
name: feedback-no-internal-api-in-public-docs
description: Public-API documentation must not reference `@Internal` API; describe the behavior in prose instead of linking the internal type
metadata:
  type: feedback
---

Documentation attached to public (or protected) API must not reference `@Internal`
API. A KDoc/Javadoc link such as `[DoubleDispatchGuard]` or
`{@link DoubleDispatchGuard}` from a public member couples the published contract to
framework-internal code.

**Why:** User feedback (2026-07-21). `@Internal` API exists for the framework's own
code; it may later become Kotlin-`internal` or be obfuscated, at which point the
public documentation reference breaks — and until then it leaks an implementation
detail into the published API surface. The public
`SignalDispatchingEntity.DEFAULT_HISTORY_DEPTH` had linked the `@Internal`
`DoubleDispatchGuard` in its KDoc.

**How to apply:** In documentation for public/protected members, describe the
behavior in prose rather than linking the `@Internal` type — e.g., "the opt-in check
that rejects a signal already dispatched" instead of `[DoubleDispatchGuard]`. Linking
non-`@Internal` public/protected API is fine. When about to write a doc link, check
the target's annotation first. Related: [[feedback-no-javadoc-only-imports]],
[[feedback-proportionate-api-docs]].
