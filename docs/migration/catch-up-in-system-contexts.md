# Catch-up in the System contexts

This guide tells you what changes when you upgrade to `2.0.0-SNAPSHOT.580`, in which the
catch-up of projections runs in the System contexts. Most applications need no code changes.
Read the [upgrade rule](#upgrade-rule) before you deploy.

## What changed, in one paragraph

A catch-up used to run in an event reactor, which the framework created for each projection
repository. Now it runs in a process manager in the System context of the projection's
Bounded Context. The same goes for the shard maintenance that a catch-up uses. Before, if a JVM
had more than one projection repository or Bounded Context, the reactor created last handled
every catch-up message, and some catch-ups never completed. Now each catch-up is handled in the
System context of its own projection type. A catch-up also no longer touches the messages of
other tenants.

## At a glance

| Area | Before | After |
|------|--------|-------|
| Where a catch-up runs | an event reactor in the projection's context | a process manager in that context's System context |
| Catch-up events, the completion event included | posted on the context's event bus and stored in its event store | posted on the System context's event bus; **stored only with `persistEvents()`** |
| Subscribing to the completion event | in the projection's context | still in the projection's context |
| `BlackBox.assertEvents()` | showed the catch-up's events | no longer shows them |
| The record of a catch-up job | a `CatchUp` message | the entity record of a process manager |
| `CatchUpStorage` | a `MessageStorage` | an `EntityRecordStorage`; must be single-tenant |
| Tenancy, in a multi-tenant context | a job matched the messages of every tenant | a job matches the messages of its own tenant |
| `AbstractStatefulReactor` | public base class | **removed** |

## 1. The catch-up's events are no longer stored by default

The events of a catch-up are now system events. A System context stores its events only when
it is told to. To keep the catch-up's events, turn this on for the context of the projections:

```java
var builder = BoundedContext.singleTenant("Tasks");
builder.systemSettings()
       .persistEvents();
```

This stores every other system event of the context too, such as `EntityCreated` and
`EntityStateChanged`.

## 2. The completion of a catch-up can still be subscribed to

When a catch-up is completed, the framework emits an event that tells so, with the identifier
of the catch-up. Every event dispatcher of a Bounded Context is also registered in its System
context. So a subscriber in the context of the projections still gets this event.

`BlackBox.assertEvents()` listens only to the events of the domain context, not to those of
its System context. So it no longer shows the events of a catch-up.

## 3. `CatchUpStorage` stores entity records

Skip this section unless you customize the storage of the `Delivery`.

- `CatchUpStorage` now extends `EntityRecordStorage<CatchUpId, CatchUp>`. Its constructor and
  `StorageFactory.createCatchUpStorage(boolean)` keep their signatures.
- `readAll()` of `CatchUp` messages is now `readJobs()`, and `read(id)` is `readJob(id)`.
  `readByType()` stays.
- `write(CatchUp)` is removed. The process managers write the jobs through the inherited
  `write(RecordWithColumns)`; to write a record yourself, use `write(id, record)`.
- `CatchUpColumn` is removed: the jobs have no column of their own any more.
- The storage must be single-tenant, as the `Delivery` creates it by default. The process
  managers of every context and every tenant share it, and the `Delivery` reads the jobs of
  all of them at once. `DeliveryBuilder.setCatchUpStorage()` now rejects a multi-tenant one.

## 4. The System contexts store the shard maintenance processes

A catch-up asks a shard maintenance process to make sure that the `Delivery` has processed
each shard affected by the catch-up. These processes are now process managers of the System
context, and each of them is stored: one record per catch-up job and affected shard. A record
holds only the IDs of the shard and the job. With a database storage, expect one more table, or
kind, per System context.

## 5. Removed API

- `AbstractStatefulReactor`. It was the base of the catch-up reactor. Use a `ProcessManager`
  and its repository instead.
- `Delivery.newCatchUpProcess()` and `CatchUpProcessBuilder`. A projection repository no longer
  creates a catch-up process; the System context of its Bounded Context runs the catch-ups.

## Upgrade rule

Before you upgrade, let every catch-up complete, and let the `Delivery` deliver every message.
Then delete all the records of the `CatchUpStorage` of the `Delivery`: these are the jobs of
the catch-ups, completed ones included. Unless you set your own storage with
`DeliveryBuilder.setCatchUpStorage()`, it is the storage of the `CatchUp` records that your
`StorageFactory` created for the `Delivery`.

- The records of catch-up jobs written before the upgrade hold `CatchUp` messages, not entity
  records, and they are not converted. The jobs of completed catch-ups stay in the storage.
  The new storage is likely to use the same table, or kind, as the old one: the context and
  the type that name it are the same. Records of the old format there would fail the reads of
  the `Delivery`.
- A shard maintenance message written before the upgrade is addressed to the shard only.
  The `Delivery` cannot deliver it, and fails the delivery run with an error that states
  this rule.
