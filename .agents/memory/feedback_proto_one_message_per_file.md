---
name: feedback-proto-one-message-per-file
description: New proto types go into their own .proto file — do not append to existing multi-message files
metadata:
  type: feedback
---

When introducing new Protobuf types, put them into a new, separate `.proto`
file (named after the leading message) instead of appending to an existing
multi-message file such as `entity.proto`.

**Why:** Product owner (2026-07-08, deciding where `EntityEventHistory`
lives): "Google Protobuf authors recommend to have one message per file
(which we don't follow yet). This makes the refactoring of proto types
easier."

**How to apply:**
- New type family → new file: e.g. `EntityEventHistory` and its record pair
  go to `spine/server/entity/event_history.proto`, not into `entity.proto`.
- Closely-coupled helper messages (a record and its ID type) may share the
  new file; the point is to stop growing legacy multi-message files.
- Do not reorganize existing multi-message files as a side effect — the rule
  targets new types; legacy layout changes are a task of their own.
