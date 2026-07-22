---
name: feedback-no-javadoc-only-imports
description: Never keep an import used only by a Javadoc/KDoc link; qualify the link instead (`{@link fqName shortName}` / `[shortName][fqName]`)
metadata:
  type: feedback
---

Do not introduce or keep an `import` whose only use is a documentation-comment
link. When a type is referenced solely from Javadoc `{@link}`/`{@linkplain}` or
KDoc `[...]` — and never in code — qualify the link rather than importing:

- Javadoc: `{@link io.spine.server.entity.DoubleDispatchGuard DoubleDispatchGuard}`
  — fully-qualified target plus a short label; renders as `DoubleDispatchGuard`.
- KDoc: `[DoubleDispatchGuard][io.spine.server.entity.DoubleDispatchGuard]`.

A same-package type needs neither an import nor qualification.

**Why:** User feedback (2026-07-21). A doc-only import is dead weight in the code
namespace: it reads as a real code dependency, and it silently outlives the last
*code* use of the type — exactly what happened when the guard config methods moved
out of `AggregateRepository`, leaving its `DoubleDispatchGuard` import used only by
`{@link}`. Qualified links keep the dependency where it belongs — in the prose.

**How to apply:** After deleting a type's last code use, check whether its import
now survives only for doc links; if so, drop the import and switch each link to the
qualified form. When adding a *new* doc reference to a type in another package, use
the qualified form from the start instead of importing for the link.
Related: [[feedback-proportionate-api-docs]].
