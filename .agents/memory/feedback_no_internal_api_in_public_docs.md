---
name: feedback-no-internal-api-in-public-docs
description: Documentation that users read must not mention `@Internal` API — neither as a link nor by name; describe the behavior in prose instead
metadata:
  type: feedback
---

Documentation that faces the users of the framework must not mention `@Internal` API:
not as a link (`{@link DoubleDispatchGuard}`, `[DoubleDispatchGuard]`), and not by name
in code font (`{@code Load}`) either. The API of an `@Internal` class is internal as
a whole — its members and its nested types included.

**Why:** User feedback, given twice. `@Internal` API exists for the framework's own
code and "should not be used by the users", so the documentation must not point them
at it. Such API may also become Kotlin-`internal` or be obfuscated later, at which
point the reference breaks.

- 2026-07-21. The public `SignalDispatchingEntity.DEFAULT_HISTORY_DEPTH` linked the
  `@Internal` `DoubleDispatchGuard` in its KDoc.
- 2026-10-05. A sentence on concurrency in the class Javadoc of the `@Internal`
  `RepositoryCache` named its nested `Load` and `Store` interfaces: first as
  `{@code Load}`, then — to make clear what the names referred to — as `{@link Load}`.
  Both were rejected. The accepted text names no API: "even while an entity is being
  loaded or stored".

**How to apply:**

- Describe the behavior in prose rather than naming the internal type or member — e.g.,
  "the opt-in check that rejects a signal already dispatched" instead of
  `[DoubleDispatchGuard]`.
- Before writing a link or a type name in a doc comment, check the annotation of the
  target *and of the class enclosing it*.
- Do not treat the Javadoc of an `@Internal` class as exempt. The class is still
  `public`, its documentation is still published, and the second case above was in
  such a Javadoc.
- If a reader asks what a name in a doc comment refers to, and the answer is an
  internal type, remove the name. Turning it into a link makes the problem worse.
- Linking non-`@Internal` public or protected API is fine.

Related: [[feedback-no-javadoc-only-imports]], [[feedback-proportionate-api-docs]].
