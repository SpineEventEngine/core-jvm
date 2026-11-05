---
name: feedback-visible-for-testing-meaning
description: `@VisibleForTesting` marks code whose visibility is higher than its production use needs because tests ALSO call it — not test-only code; a production caller is no reason to remove it
metadata:
  type: feedback
---

`@VisibleForTesting` does not mean "used only by tests". It marks code whose visibility is
higher than necessary because the code is also called from tests. Production callers are
expected.

**Why:** product owner (2026-11-05), correcting PR #1707: removing the annotation from
`MessageIdExtensions.causedError()` "because production code calls it" was wrong. The
annotation came back.

**How to apply:**
- Never remove `@VisibleForTesting` because a production caller exists, and do not flag
  production calls of such code in reviews.
- Remove it only when the visibility itself is reduced to what production needs, or when
  tests no longer need the wider visibility.
