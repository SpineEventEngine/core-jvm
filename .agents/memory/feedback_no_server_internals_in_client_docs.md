---
name: feedback-no-server-internals-in-client-docs
description: Docs of client-facing types (`spine.core.Status`, `ErrorCase`, client services and handlers) must not tell how or where the server keeps error details — it exposes server internals and is a security issue
metadata:
  type: feedback
---

Docs that clients read must not describe how the server handles a failed request: where it
keeps the details (an event of the System context, a log), or how a developer finds them.
Say only what the client gets and what it means for the client.

**Why:** product owner (2026-11-05), on the doc of `Status.error_case`: "This documents (and
exposes!) implementation details of the server side. This is not a client's concern, and is
also a security issue." Telling clients less about server-side errors is the point of
`ErrorCase`: an attacker should not learn from the docs or the answers how the server works.

**How to apply:**
- Client-facing: `spine/core/response.proto`, `error_case.proto`, `ack.proto`, the
  `spine.client` service protos, and the Java client API (`ServerErrorHandler`, …).
- Server-facing API (e.g. `BusFilter`, migration guides for server developers) may say that
  the details stay on the server; still never name `(internal_all)` types there
  ([[feedback-no-internal-api-in-public-docs]]).
