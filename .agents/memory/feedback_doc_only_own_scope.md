---
name: feedback-doc-only-own-scope
description: A doc comment covers only what its element does — no restating what a field's type already implies, no lists of what the element does NOT do (e.g. rejections in `CommandService.post()`)
metadata:
  type: feedback
---

Keep a doc comment to the element's own contract:

- Do not restate what the type of a field already says. `Status.error_case` is an `ErrorCase`,
  so "the value tells only the kind of the problem" is redundant.
- Do not document what the element does not do, or behavior that belongs to other parts of
  the system. `CommandService.post()` must not explain that handler rejections come as events
  through a subscription: "There are many things that it does not do, and we should not
  document them here."

**Why:** product owner (2026-11-05), reviewing the docs of `CommandService.post()`.

**How to apply:** before adding a "note that…" or "X is not part of Y" paragraph, check that
it describes the element itself. Related: [[feedback-proportionate-api-docs]],
[[feedback-no-restating-system-context]], [[feedback-no-subtype-docs-in-supertype]].
