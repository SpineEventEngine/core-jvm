---
name: feedback-no-subtype-docs-in-supertype
description: Never document a sub-interface or subclass (its methods, its special cases) in the docs of its super-type — a hierarchy violation; document it on the subtype itself
metadata:
  type: feedback
---

The docs of a super-type describe only what the super-type itself offers. They do not
mention a sub-interface or a subclass, link to its members, or list a case that applies to
one subtype only. Such a case goes into the docs of the subtype.

**Why:** product owner (2026-11-05), removing a paragraph about `CommandFilter.deny()` from
`BusFilter.filter()`: "This doc is a violation of hierarchy. We document a sub-interface in
a super-interface. This is OOP nonsense!" The old `BusFilter` doc had the same flaw: it
listed a business rejection, "only applicable to commands", which was part of why the
filter-rejection API was called a hack.

**How to apply:**
- When adding a method to a subtype (e.g. `CommandFilter.deny()`), document it on the
  subtype; do not add a "subtypes can also…" note to the super-type.
- When reviewing, flag `{@link Sub#member}` or "for commands only" remarks in a super-type.
