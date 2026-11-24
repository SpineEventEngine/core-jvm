---
name: enum-columns-stored-by-ordinal
description: "`jdbc-storage` and `gcloud-jvm` store enum columns by `ordinal()`; removing a Protobuf enum value from the middle shifts the stored values even when its number is reserved"
metadata:
  type: reference
---

The column mappings of `jdbc-storage` (`JdbcColumnMapping.ofEnum()`) and `gcloud-jvm`
(`DsColumnMapping.ofEnum()`) store an enum column by the `ordinal()` of its constant.
The in-memory storage keeps the constant itself, so the tests of core-jvm do not show the
difference.

`reserved` keeps the numbers of a Protobuf enum stable on the wire, but not the ordinals.
Removing a value moves the ordinals of the values declared after it. A query by such a column
then misses the records stored earlier, or finds the wrong ones.

**Why:** 2026-11-24, the removal of command scheduling: `InboxMessageStatus.SCHEDULED = 2` was
removed, so `DELIVERED` went from ordinal 3 to 2, and `TO_CATCH_UP` from 4 to 3. `TO_DELIVER`
did not move. The migration guide asks to drain the inboxes before the upgrade.

**How to apply:** before you remove or reorder the values of an enum, look for a `RecordColumn`
or an entity column of its type. If there is one, either keep the value as deprecated, or say in
the migration guide how to prepare the stored records. Add new values at the end only.
