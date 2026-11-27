# Subscriptions to an event type in every Bounded Context

This guide tells you what changes when you upgrade to `2.0.0-SNAPSHOT.820`. A subscription to
an event type now gets the events of every Bounded Context that posts the type. Clients need no
code changes. Server code that reads the removed API stops compiling, and needs the changes
below. A few topics that the server accepted before are now refused; see
[Before you deploy](#before-you-deploy).

## What changed, in one paragraph

`SubscriptionService` found the context of an event type by the types that each context declared
in the signatures of its receptors. A type that two contexts declared was served by one of
them only, so the subscribers missed the events of the other context. In an application of
a multi-tenant and a single-tenant context, a valid topic could even be refused, depending on
which context was added last. Yet several contexts may post one event type by design, and
a context may post a type that it does not declare: a receptor may return
`Iterable<EventMessage>`, and any command handler may throw a rejection that it does not
declare. So the declared types no longer decide anything. A target of an event type is served
by every Bounded Context of the application whose tenancy fits the topic: the multi-tenant
contexts for a topic with a tenant, and the single-tenant contexts for a topic without one.
Each context passes to the subscriber the events posted in it, so the subscriber gets each
event once. A target of an entity type is still served by the one context that has the entities.

## At a glance

| Area | Before | After |
|------|--------|-------|
| A target of an event type | served by the context that declared the type last, or by every context if none declared it | served by every context whose tenancy fits the topic |
| A topic of an event type that fits no context | depended on which contexts declared the type | refused with `TENANT_ERROR` |
| Activating or cancelling a subscription that fits no context | `UNSUPPORTED_REQUEST` or `TENANT_ERROR` | `TENANT_ERROR` |
| The warning "Unable to find a Bounded Context for the event types…" | logged for an undeclared type | **removed** |
| `Stand.exposedEventTypes()` | the event types declared in the context | **removed** |
| `Stand.registerTypeSupplier(EventProducer)` | public | **removed** |
| `Stand.registerTypeSupplier(Repository)` | registered the entity and the event types | registers the entity types |
| `EventProducer.producedEvents()` | implemented by receptors and entities | **removed** |
| `EventProducingRepository` | implemented by `AggregateRepository` and `ProcessManagerRepository` | **removed** |
| `outgoingEvents()` of `AggregateRepository` and `ProcessManagerRepository` | public | **removed** |
| `eventBus()` of `AggregateRepository` and `ProcessManagerRepository` | public | **removed**; use `context().eventBus()` |
| `postEvents()`, `postIfCommandRejected()`, and `filter()` of these repositories | public | no longer public |
| `AggregateClass.outgoingEvents()`, `ProcessManagerClass.outgoingEvents()` | public | **removed** |
| `rejections()` of `CommandHandlingClass`, its implementations, and `CommandReceptor` | public | **removed** |
| `reactionOutput()` of `ReactingClass` and its implementations | public | **removed** |
| `commandOutput()` of `CommandHandlingClass` and its implementations | public | **removed** |
| `CommandingClass`, and `outgoingCommands()` of `CommanderClass` and `ProcessManagerClass` | public | **removed** |
| `Receptor.producedMessages()`, `producedTypes()` of `ReceptorMap` and `EventReceivingClassDelegate` | public | **removed** |
| The type parameter of produced message classes in `Receptor`, `CommandReceptor`, `EventReceptor`, `ReceptorMap`, `EventReceivingClassDelegate`, `CommandHandlingClass`, `AbstractCommandHandlingClass` | e.g. `CommandReceptor<T, EventClass>` | **removed**; e.g. `CommandReceptor<T>` |

## 1. Subscribe as before

A client subscribes to an event type as it did. It now gets the events of the type from every
context of the application that fits its tenancy, whichever of them declares the type.

## 2. Stop overriding `producedEvents()`

`EventProducer` no longer has `producedEvents()`. Delete the method from the classes that
implement `EventProducer` or `EventReactor` directly. In Kotlin:

```kotlin
// Before.
override fun producedEvents(): ImmutableSet<EventClass> = ImmutableSet.of()

// After: nothing.
```

In Java:

```java
// Before.
@Override
public ImmutableSet<EventClass> producedEvents() {
    return ImmutableSet.of();
}

// After: nothing.
```

## 3. Watch the posted events, not the declared ones

The methods that listed the event types declared by receptors are removed:
`Stand.exposedEventTypes()`, `EventProducingRepository.outgoingEvents()`, the `outgoingEvents()`
of `AggregateClass` and `ProcessManagerClass`, `rejections()` of the command-handling classes and
of `CommandReceptor`, and `reactionOutput()` of the reacting classes. So are the methods that
listed the other messages that receptors declare, commands included: `commandOutput()`,
`outgoingCommands()` with the `CommandingClass` interface, `Receptor.producedMessages()`, and
the `producedTypes()` of `ReceptorMap` and `EventReceivingClassDelegate`.

None of them has a direct replacement: the declared types never told which contexts post a type.
To see the events that a context posts, add a listener to its `EventBus`, and skip the external
events: the bus also passes to its listeners the events that the context receives from other
contexts. In the examples below, `postedTypes` is a set of your own. In Kotlin:

```kotlin
add(singleTenantContext("Tasks") {
    add(TaskRepository())
    addEventListener { event ->
        if (!event.isExternal) {
            postedTypes.add(event.typeUrl())
        }
    }
})
```

In Java:

```java
BoundedContext.singleTenant("Tasks")
        .add(new TaskRepository())
        .addEventListener(event -> {
            if (!event.isExternal()) {
                postedTypes.add(event.typeUrl());
            }
        });
```

## 4. Post from a repository through its context

A subclass of `AggregateRepository` or `ProcessManagerRepository` that called `eventBus()` or
`postEvents()` posts to the `EventBus` of its context instead. Filter the events with
`eventFilter()` first, if the repository has a filter:

```kotlin
// Before.
postEvents(events)

// After.
context().eventBus().post(eventFilter().filter(events))
```

## Before you deploy

1. **Upgrade the servers.** The clients and the protocol do not change.
2. **Check the topics with a tenant sent to single-tenant contexts.** In an application with
   no multi-tenant context, a topic with a tenant for an event type that no context declared was
   accepted, and got the events of all the contexts. It is now refused with `TENANT_ERROR`, as
   a topic of an entity type is. Send such topics without a tenant.
3. **Check the topics without a tenant sent to multi-tenant contexts.** Such a subscription to
   an event type that no context declared was created, but got no events, because every event
   of a multi-tenant context has a tenant. It is now refused with `TENANT_ERROR` if no
   single-tenant context fits it.
