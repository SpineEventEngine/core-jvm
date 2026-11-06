---
name: feedback-filters-are-anti-corruption-layer
description: Bus filters only validate a message before it is posted (an anti-corruption layer, like `ValidatingFilter`); never handle permissions or business rules in filters, and do not add filter APIs for that
metadata:
  type: feedback
---

The only meaningful reason to filter a message before it is posted to a bus is to validate it:
the filters form an anti-corruption layer, as `ValidatingFilter` does. Permissions and other
business rules are not handled on the level of filters.

**Why:** product owner (2026-11-05): "We don't want to handle permissions on the level of
filters. The only meaningful case for filtering messages before they are posted to a bus is
validation of those messages before they are posted. This is called 'Anti-corruption
Layer'." This removed `CommandFilter.reject(…, RejectionThrowable)` and the
`CommandFilter.deny(command, Error)` that briefly replaced it. The owner also noted that
`spine.base.Error` means a technical failure (like an exception), not the domain language,
so it must not carry a business reason.

**How to apply:**
- Do not propose filter shortcuts for refusing a command for a business reason; point to
  command handlers, which reject commands.
- `ValidatingFilter` is planned to move off `Error` (next session after the `ErrorCase` slice).
