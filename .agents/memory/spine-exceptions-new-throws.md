---
name: spine-exceptions-new-throws
description: "`io.spine.util.Exceptions.newIllegalStateException()` and its siblings THROW; never use them to build an exception you pass on"
metadata:
  type: reference
---

`Exceptions.newIllegalStateException(format, args)` (and the other `new…Exception()` helpers
of `io.spine.util.Exceptions` in `base`) throws the exception it builds. The return type only
lets you write `throw newIllegalStateException(…)` where the compiler needs a `throw`.

To hand an exception to a handler or an observer, create it with `new IllegalStateException(
format(…))` (Kotlin: `IllegalStateException("…")`).

**Why:** 2026-11-09, slice 4 of the `ErrorCase` work: `Subscriptions.CancellationObserver.onNext()`
passed `newIllegalStateException(…)` to the streaming error handler. The helper threw inside
the gRPC callback, and the client got `CANCELLED: Failed to read message.` instead.

**How to apply:** use the helpers only right after `throw`; flag any other use in a review.
