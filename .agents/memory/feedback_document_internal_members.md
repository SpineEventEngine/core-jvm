---
name: feedback-document-internal-members
description: Project convention — every Kotlin `internal` method gets KDoc, even when the Java original had none
metadata:
  type: feedback
---

Document all `internal` methods in Kotlin with KDoc, the same way as public API.

**Why:** User feedback (2026-07-06) on the `TransactionalEntity` conversion:
"Please document this method and other `internal` methods in this changeset. ...
This is a project convention." Java package-private methods often lack Javadoc;
when they become Kotlin `internal`, that gap must be closed, not preserved.

**How to apply:** When writing or converting `internal` functions and properties,
add KDoc stating the contract (plus `@throws` where relevant).
Related: [[java-to-kotlin-visibility-traps]].
