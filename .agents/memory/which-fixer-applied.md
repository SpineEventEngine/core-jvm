---
name: which-fixer-applied
description: Records that the which-fixer bulk sweep was completed for this repo.
metadata:
  type: project
---

Bulk `which-fixer` sweep completed.

**Why:** Marks the transition from bulk to incremental mode so future
invocations only scan files modified on the current branch.

**How to apply:** The `which-fixer` skill reads this file on every
invocation. When it exists, the skill runs in incremental mode.
