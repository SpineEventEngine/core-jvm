# Per-context isolation for `EventStore` persistence

## Status

Investigation complete; verified against `jdbc-storage` and `gcloud-jvm` (Datastore).
Implementation not started.

## Problem

All Bounded Contexts of an application share one `StorageFactory`
(`ServerEnvironment.instance().storageFactory()`). `EventBus.registerWith(context)` calls
`StorageFactory.createEventStore(context.spec())`, which builds
`new DefaultEventStore(context, factory)`. The Java objects are per-context, but
`DefaultEventStore` creates its record storage as:

```java
factory.createRecordStorage(context, spec())   // group == null
```

with `RecordSpec(idType = EventId, recordType = Event)`, so `sourceType == recordType == Event`.

`jdbc-storage` derives the physical table identity solely from
`(sourceType, recordType, group)` — see `TableSpecs.SpecKey` and `TableSpecs.tableName(..)`.
`ContextSpec` is accepted by `JdbcRecordStorage` but never participates in table naming.
For the ungrouped event-store spec the name is `TableNames.of(Event.class)` =
**`spine_core_Event`** — one table shared by every Bounded Context of the application
(and by any System context with `SystemSettings.persistEvents()` enabled).

`InMemoryStorageFactory` is unaffected only by accident: every `createRecordStorage(..)`
call returns a fresh `InMemoryRecordStorage` with its own data map, so contexts are
isolated by object identity. This is why tests never catch the issue.

`gcloud-jvm` (Datastore) has the same flaw. `DatastoreStorageFactory` does not override
`createEventStore`, and for an ungrouped storage the Entity kind defaults to
`FlatLayout(domainType)` → the qualified type name, **`spine.core.Event`**, for every
context (`RecordLayouts.find(Class)` → `RecordLayout(Class)` → `Kind.of(domainType)`).
Datastore namespaces do not help: they serve multitenancy — the namespace comes from
the current tenant or the deployment-wide default — and `wrapperFor(context)` consults
only `isMultitenant()`, never the context name.

### Consequences (multi-context apps on JDBC or Datastore)

- Events of all contexts intermingle in one physical store — the `spine_core_Event`
  table / kind.
- `CatchUpProcess` reads via `context.eventBus().eventStore()` with an `EventStreamQuery`
  filtered by the `type` column. If two contexts use the same event proto type
  (shared event definitions), catch-up in one context replays the other context's events.
- Unfiltered `EventStore.read(..)` (replay/export tooling) returns foreign events.
- No physical boundary for per-context data lifecycle (deletion, GDPR erasure, backup).

## Fix scenario

### 1. core-jvm: group the event store by context

`DefaultEventStore` passes a `StorageGroup` derived from the context name:

```java
public DefaultEventStore(ContextSpec context, StorageFactory factory) {
    super(context, factory.createRecordStorage(context, spec(), groupOf(context)));
    ...
}

private static StorageGroup groupOf(ContextSpec context) {
    return new StorageGroup(context.name().getValue());
}
```

Rationale:

- `StorageGroup`'s documented purpose is exactly this: differentiating storages holding
  records of the same type, so a vendor mapping equal record specs to one physical storage
  does not conflate them.
- Fixes both group-honoring SPI implementations at once, with no backend changes for
  the core behavior:
    - `jdbc-storage` names the table via the existing `TableNames.of(Event.class, group)`
      → `Billing_Event` (System contexts → `Billing_System_Event`);
    - `gcloud-jvm` names the kind via `Kind.of(Event.class, group)` → `Billing-Event`
      (verified: `createRecordStorage` with a non-null group routes to
      `RecordLayouts.find(recordType, group)`, defaulting to a flat layout under the
      grouped kind; the dash separator is documented as collision-proof against
      type-name-derived kinds).
- `InMemoryStorageFactory` deliberately ignores the group → no behavior change in-memory.
- `TableSpecs` cache is keyed by `SpecKey` including the group name → per-context entries
  appear naturally.

Add `StorageGroup.of(BoundedContextName)` next to the existing
`StorageGroup.of(entityClass)` factory method, as the single source of truth for
deriving a group from a context. `DefaultEventStore` calls it with `context.name()`;
the jdbc-storage Builder overload (step 3) uses the same helper, so the two sides
can never drift apart in how the group name is spelled.

### 2. jdbc-storage: sanitize group names for SQL

Context names are validated only as non-blank (`BoundedContextNames.checkValid`), so they
may contain spaces, dashes, etc. `TableNames.of(recordType, group)` replaces only dots.
Extend sanitization to replace any character outside `[A-Za-z0-9_]` with `_`.

### 3. jdbc-storage: custom-name API for context-grouped tables

Today the event table can be renamed via `TableSpecs.Builder.setTableName(Event.class, ..)`
(single-type lookup by `sourceType`). Once the event storage is grouped, that lookup no
longer applies, and grouped custom names are registered by *entity state type* only.
Add an overload addressing the table by the context it belongs to:

```java
Builder setTableName(BoundedContextName context, Class<R> recordType, String name)
```

`BoundedContextName` rather than a raw `String`, because:

- The type states what the parameter is; the name shortens to just `context`.
- `setTableName(String, Class, String)` would put two `String`s of different meaning
  in one signature — transposing the context name and the table name would compile.
- Users obtain the value via `BoundedContextNames.newName(..)`, which validates it,
  so a blank name fails at configuration time, not at table-creation time.
- It mirrors the existing `setTableName(Class<S> stateType, ..)` overload: the Builder
  accepts the *typed identity* of the group owner and derives the group-name string
  internally (via `StorageGroup.of(BoundedContextName)` from step 1). The `String`
  representation of a group stays an implementation detail of `TableSpecs`.

Note: to address a *System* context's event table, users spell the name directly
(`newName("Billing_System")`), since `BoundedContextName.toSystem()` is `@Internal`.
Mention this in the overload's Javadoc.

Release note: existing `setTableName(Event.class, ..)` customizations stop applying to
the event table.

### 4. gcloud-jvm: layout API for context-grouped kinds

Mirrors step 3. Grouped record layouts are registered via
`Builder.organizeRecords(Class<S> stateType, Class<R> recordType, RecordLayout)` —
keyed by entity state type, so a context-grouped kind cannot be addressed.
Add an overload:

```java
Builder organizeRecords(BoundedContextName context,
                        Class<R> recordType,
                        RecordLayout<I, R> layout)
```

deriving the group via `StorageGroup.of(BoundedContextName)` from step 1
(same rationale as the typed parameter in step 3).

No name sanitization is needed (unlike step 2): Datastore kinds are arbitrary UTF-8
strings, and the existing guard in `Kind` (no `__` prefix) covers the corner case.

Release note: `createRecordStorage` deliberately bypasses single-type configuration
for grouped storages, so existing `useRecordStorage(EventId.class, Event.class, ..)`
and single-type `organizeRecords(Event.class, ..)` registrations stop applying to
the event storage once it becomes grouped.

### 5. Migration for existing deployments

Existing data sits in `spine_core_Event` with no per-row context discriminator (that is
the root cause). The `type` column holds the qualified proto type name, so operators can
split by SQL using each context's event-type set:

```sql
INSERT INTO Billing_Event
    SELECT * FROM spine_core_Event
    WHERE type IN ('example.billing.InvoiceIssued', ...);
```

For Datastore, events live under the kind `spine.core.Event` (per namespace, for
multi-tenant apps). Migration is a copy of entities to the `<Context>-Event` kind,
split by the same `type` property (the event columns are storage-agnostic) — a small
utility or a Dataflow job; iterate namespaces for multi-tenant deployments.

Document in release notes. Decide: clean break (2.0 is SNAPSHOT) vs. an opt-out flag on
the storage factory builders (`JdbcStorageFactory`, `DatastoreStorageFactory`) for the
legacy shared table / kind.

### 6. Tests

- core-jvm: assert `createEventStore` passes the context-derived group
  (extend `MemoizingStorageFactory` in `server` testFixtures).
- jdbc-storage: integration test — two `BoundedContext`s over one `JdbcStorageFactory`
  (H2), distinct events posted in each; assert each context's `EventStore.read(..)`
  returns only its own events. Unit test for the `<ContextName>_Event` naming.
- gcloud-jvm: the same two-context test over one `DatastoreStorageFactory`
  (`TestDatastoreStorageFactory` from `testlib`, backed by the local emulator);
  unit test for the `Billing-Event` kind.

## Rejected alternative

Fixing only inside jdbc-storage by mixing `ContextSpec` into `SpecKey`/table names:

- prefixing all ungrouped tables renames every entity-state table (large migration blast
  radius) and breaks `MirrorStorage`, whose table must match the Spine 1.x layout;
- special-casing `Event` in one backend leaves other SPI implementations broken.

## Related follow-up (out of scope)

Entity-scoped storages derive their identity from the entity state type alone:
`EntityRecordStorage` is ungrouped and named by the state type, while
`EntityEventStorage` and `EntityStateHistoryStorage` are grouped by it. If one entity
class — or two classes sharing a state type — were registered in two contexts of the
same application, those storages would collide across contexts exactly like the event
store does. Unlike the event-store collision, which hits every multi-context app
unconditionally, this one needs a shared entity type — rare, and arguably a modeling
error. The same fix shape (a context-derived component in the group) would apply, but
it renames every entity table / kind — a far larger migration — so it stays out of
scope here.
