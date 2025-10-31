# Hand-Written Builders in core-jvm

This document lists all hand-written Builder classes found in the repository,
excluding generated code. It was created as part of the effort to unify the Builder API naming conventions.

## Purpose

This documentation serves as:
1. A comprehensive inventory of all Builder classes in the codebase
2. Analysis of current naming patterns and conventions
3. Identification of inconsistencies that need to be addressed
4. Reference for implementing the unified Builder API pattern

## Quick Stats

**Total Builders Found:** 37
- **Standalone Builders:** 14
- **Nested Builders:** 23
- **Builders with build() method:** 21

**Generated:** Fri Oct 31 20:49:41 UTC 2025

## Table of Contents

- [Client Module Builders](#client-module-builders)
- [Server Module Builders](#server-module-builders)
- [Observations and Patterns](#observations-and-patterns)
- [Summary](#summary)

---

## Client Module Builders

## io.spine.client.ActorRequestFactory.Builder

- **Type:** Nested Builder
- **File:** `client/src/main/java/io/spine/client/ActorRequestFactory.java`
- **Has build() method:** True

### Setter Methods (3)

- `setActor(...)`
- `setTenantId(...)`
- `setZoneId(...)`

### Getter Methods (1)

- `getActor()`

---

## io.spine.client.Client.Builder

- **Type:** Nested Builder
- **File:** `client/src/main/java/io/spine/client/Client.java`
- **Has build() method:** True

### Getter Methods (1)

- `tenant()`

### Validation Methods (1)

- `isOpen()`

### Other Builder Methods (1)

- `withGuestId(...)`

---

## io.spine.client.DeliveringEventObserver.Builder

- **Type:** Nested Builder
- **File:** `client/src/main/java/io/spine/client/EventConsumers.java`
- **Has build() method:** False

*No public setter/getter methods found (may have package-private or protected methods)*

---

## io.spine.client.DeliveringObserver.Builder

- **Type:** Nested Builder
- **File:** `client/src/main/java/io/spine/client/Consumers.java`
- **Has build() method:** False

*No public setter/getter methods found (may have package-private or protected methods)*

---

## io.spine.client.MultiEventConsumers.Builder

- **Type:** Nested Builder
- **File:** `client/src/main/java/io/spine/client/MultiEventConsumers.java`
- **Has build() method:** False

*No public setter/getter methods found (may have package-private or protected methods)*

---

## io.spine.client.QueryBuilder

- **Type:** Standalone Builder
- **File:** `client/src/main/java/io/spine/client/QueryBuilder.java`
- **Has build() method:** True

*No public setter/getter methods found (may have package-private or protected methods)*

---

## io.spine.client.TargetBuilder

- **Type:** Standalone Builder
- **File:** `client/src/main/java/io/spine/client/TargetBuilder.java`
- **Has build() method:** False

*No public setter/getter methods found (may have package-private or protected methods)*

---

## io.spine.client.TopicBuilder

- **Type:** Standalone Builder
- **File:** `client/src/main/java/io/spine/client/TopicBuilder.java`
- **Has build() method:** True

*No public setter/getter methods found (may have package-private or protected methods)*

---


## Server Module Builders

## io.spine.server.AbstractServiceBuilder

- **Type:** Standalone Builder
- **File:** `server/src/main/java/io/spine/server/AbstractServiceBuilder.java`
- **Has build() method:** False

*No public setter/getter methods found (may have package-private or protected methods)*

---

## io.spine.server.BoundedContextBuilder

- **Type:** Standalone Builder
- **File:** `server/src/main/java/io/spine/server/BoundedContextBuilder.java`
- **Has build() method:** True

### Setter Methods (3)

- `setAggregateRootDirectory(...)`
- `setOnBeforeClose(...)`
- `setTenantIndex(...)`

### Getter Methods (1)

- `tenantIndex()`

### Validation Methods (1)

- `isMultitenant()`

### Other Builder Methods (9)

- `addAssignee(...)`
- `addCommandDispatcher(...)`
- `addCommandFilter(...)`
- `addCommandListener(...)`
- `addEventDispatcher(...)`
- `addEventFilter(...)`
- `addEventListener(...)`
- `removeCommandDispatcher(...)`
- `removeEventDispatcher(...)`

---

## io.spine.server.CancellationImpl.Builder

- **Type:** Nested Builder
- **File:** `server/src/main/java/io/spine/server/SubscriptionService.java`
- **Has build() method:** True

*No public setter/getter methods found (may have package-private or protected methods)*

---

## io.spine.server.CommandServiceImpl.Builder

- **Type:** Nested Builder
- **File:** `server/src/main/java/io/spine/server/CommandService.java`
- **Has build() method:** True

*No public setter/getter methods found (may have package-private or protected methods)*

---

## io.spine.server.ConnectionBuilder

- **Type:** Standalone Builder
- **File:** `server/src/main/java/io/spine/server/ConnectionBuilder.java`
- **Has build() method:** False

*No public setter/getter methods found (may have package-private or protected methods)*

---

## io.spine.server.QueryServiceImpl.Builder

- **Type:** Nested Builder
- **File:** `server/src/main/java/io/spine/server/QueryService.java`
- **Has build() method:** True

*No public setter/getter methods found (may have package-private or protected methods)*

---

## io.spine.server.Server.Builder

- **Type:** Nested Builder
- **File:** `server/src/main/java/io/spine/server/Server.java`
- **Has build() method:** True

*No public setter/getter methods found (may have package-private or protected methods)*

---

## io.spine.server.ShutdownCallback.Builder

- **Type:** Nested Builder
- **File:** `server/src/main/java/io/spine/server/GrpcContainer.java`
- **Has build() method:** True

### Getter Methods (2)

- `port()`
- `serverName()`

### Validation Methods (1)

- `isShutdown()`

### Other Builder Methods (3)

- `addService(...)`
- `removeService(...)`
- `withServer(...)`

---

## io.spine.server.TypeDictionary.Builder

- **Type:** Nested Builder
- **File:** `server/src/main/java/io/spine/server/TypeDictionary.java`
- **Has build() method:** False

*No public setter/getter methods found (may have package-private or protected methods)*

---

## io.spine.server.aggregate.Registry.Builder

- **Type:** Nested Builder
- **File:** `server/src/main/java/io/spine/server/aggregate/ImportBus.java`
- **Has build() method:** True

*No public setter/getter methods found (may have package-private or protected methods)*

---

## io.spine.server.aggregate.given.dispatch.AggregateBuilder

- **Type:** Standalone Builder
- **File:** `server/src/testFixtures/java/io/spine/server/aggregate/given/dispatch/AggregateBuilder.java`
- **Has build() method:** False

*No public setter/getter methods found (may have package-private or protected methods)*

---

## io.spine.server.bus.BusBuilder

- **Type:** Standalone Builder
- **File:** `server/src/main/java/io/spine/server/bus/BusBuilder.java`
- **Has build() method:** False

### Getter Methods (2)

- `system()`
- `tenantIndex()`

---

## io.spine.server.commandbus.CommandAckMonitor.Builder

- **Type:** Nested Builder
- **File:** `server/src/main/java/io/spine/server/commandbus/CommandAckMonitor.java`
- **Has build() method:** False

*No public setter/getter methods found (may have package-private or protected methods)*

---

## io.spine.server.commandbus.CommandBus.Builder

- **Type:** Nested Builder
- **File:** `server/src/main/java/io/spine/server/commandbus/CommandBus.java`
- **Has build() method:** True

### Setter Methods (2)

- `setMultitenant(...)`
- `setWatcher(...)`

### Validation Methods (1)

- `isMultitenant()`

---

## io.spine.server.delivery.CatchUpProcessBuilder

- **Type:** Standalone Builder
- **File:** `server/src/main/java/io/spine/server/delivery/CatchUpProcessBuilder.java`
- **Has build() method:** True

### Getter Methods (1)

- `getRepository()`

---

## io.spine.server.delivery.CatchUpStarter.Builder

- **Type:** Nested Builder
- **File:** `server/src/main/java/io/spine/server/delivery/CatchUpStarter.java`
- **Has build() method:** False

*No public setter/getter methods found (may have package-private or protected methods)*

---

## io.spine.server.delivery.DeliveryBuilder

- **Type:** Standalone Builder
- **File:** `server/src/main/java/io/spine/server/delivery/DeliveryBuilder.java`
- **Has build() method:** True

### Setter Methods (8)

- `setCatchUpPageSize(...)`
- `setCatchUpStorage(...)`
- `setDeduplicationWindow(...)`
- `setInboxStorage(...)`
- `setMonitor(...)`
- `setPageSize(...)`
- `setStrategy(...)`
- `setWorkRegistry(...)`

### Getter Methods (8)

- `catchUpPageSize()`
- `catchUpStorage()`
- `deduplicationWindow()`
- `deliveryMonitor()`
- `inboxStorage()`
- `pageSize()`
- `strategy()`
- `workRegistry()`

---

## io.spine.server.delivery.DeliveryError.Builder

- **Type:** Nested Builder
- **File:** `server/src/main/java/io/spine/server/delivery/DeliveryErrors.java`
- **Has build() method:** False

*No public setter/getter methods found (may have package-private or protected methods)*

---

## io.spine.server.delivery.Inbox.Builder

- **Type:** Nested Builder
- **File:** `server/src/main/java/io/spine/server/delivery/Inbox.java`
- **Has build() method:** True

*No public setter/getter methods found (may have package-private or protected methods)*

---

## io.spine.server.enrich.EnricherBuilder

- **Type:** Standalone Builder
- **File:** `server/src/main/java/io/spine/server/enrich/EnricherBuilder.java`
- **Has build() method:** False

*No public setter/getter methods found (may have package-private or protected methods)*

---

## io.spine.server.entity.CompositeEventFilter.Builder

- **Type:** Nested Builder
- **File:** `server/src/main/java/io/spine/server/entity/CompositeEventFilter.java`
- **Has build() method:** True

*No public setter/getter methods found (may have package-private or protected methods)*

---

## io.spine.server.entity.EntityBuilder

- **Type:** Standalone Builder
- **File:** `server/src/testFixtures/java/io/spine/server/entity/EntityBuilder.java`
- **Has build() method:** True

*No public setter/getter methods found (may have package-private or protected methods)*

---

## io.spine.server.entity.EntityLifecycle.Builder

- **Type:** Nested Builder
- **File:** `server/src/main/java/io/spine/server/entity/EntityLifecycle.java`
- **Has build() method:** False

*No public setter/getter methods found (may have package-private or protected methods)*

---

## io.spine.server.entity.EventFieldFilter.Builder

- **Type:** Nested Builder
- **File:** `server/src/main/java/io/spine/server/entity/EventFieldFilter.java`
- **Has build() method:** True

*No public setter/getter methods found (may have package-private or protected methods)*

---

## io.spine.server.event.EventBus.Builder

- **Type:** Nested Builder
- **File:** `server/src/main/java/io/spine/server/event/EventBus.java`
- **Has build() method:** True

### Setter Methods (1)

- `setObserver(...)`

### Getter Methods (1)

- `enricher()`

### Validation Methods (1)

- `isRegistered()`

---

## io.spine.server.event.EventEnricher.Builder

- **Type:** Nested Builder
- **File:** `server/src/main/java/io/spine/server/event/EventEnricher.java`
- **Has build() method:** True

*No public setter/getter methods found (may have package-private or protected methods)*

---

## io.spine.server.procman.given.dispatch.ProcessManagerBuilder

- **Type:** Standalone Builder
- **File:** `server/src/testFixtures/java/io/spine/server/procman/given/dispatch/ProcessManagerBuilder.java`
- **Has build() method:** False

*No public setter/getter methods found (may have package-private or protected methods)*

---

## io.spine.server.projection.given.dispatch.ProjectionBuilder

- **Type:** Standalone Builder
- **File:** `server/src/testFixtures/java/io/spine/server/projection/given/dispatch/ProjectionBuilder.java`
- **Has build() method:** False

*No public setter/getter methods found (may have package-private or protected methods)*

---

## io.spine.server.stand.Stand.Builder

- **Type:** Nested Builder
- **File:** `server/src/main/java/io/spine/server/stand/Stand.java`
- **Has build() method:** True

### Setter Methods (1)

- `setMultitenant(...)`

### Validation Methods (2)

- `isMultitenant()`
- `isOpen()`

### Other Builder Methods (1)

- `withSubscriptionRegistryFrom(...)`

---


## Summary

- **Total Builders:** 37
- **Standalone Builders:** 14
- **Nested Builders:** 23
- **Builders with build() method:** 21


## Observations and Patterns

### Current API Patterns Identified

Based on the analysis above, several patterns are currently in use:

#### 1. Setter Method Patterns
- **`setXxx(...)`** - Most common pattern (e.g., `setTenantId`, `setStrategy`, `setMonitor`)
- **`addXxx(...)`** - Used for collection-like additions (e.g., `addAssignee`, `addService`)
- **`removeXxx(...)`** - Used for collection-like removals (e.g., `removeService`, `removeCommandDispatcher`)
- **`withXxx(...)`** - Alternative pattern (e.g., `withGuestId`, `withServer`)

#### 2. Getter Method Patterns
- **`getXxx()`** - Traditional getter pattern (e.g., `getActor`, `getRepository`)
- **`xxx()`** - Property-style getter returning `Optional<T>` or direct value (e.g., `tenant()`, `inboxStorage()`)

#### 3. Validation Method Patterns
- **`hasXxx()`** - Check if value is set (currently limited usage)
- **`isXxx()`** - Boolean property checks (e.g., `isMultitenant`, `isOpen`, `isShutdown`)

### Inconsistencies Found

1. **Mixed getter styles**: Some builders use `getXxx()` while others use `xxx()` returning `Optional<T>`
2. **Inconsistent setter prefixes**: Mix of `set`, `add`, `with` for similar operations
3. **Missing `has` methods**: Very few builders implement `hasXxx()` methods to check if values are set
4. **Getter return types**: Some use `Optional<T>`, others use nullable types, some throw NPE

### Recommended Unified API Pattern

Based on the issue description, the recommended pattern should be:

```java
FooBar.Builder builder = FooBar.newBuilder();

// Setters always use 'set' prefix
builder.setTheExpectation(myExpectation);

// Check whether the value has been set
if(builder.hasTheExpectation()) {
    // Getter that throws NPE if not set
    TheExpectation justSet = builder.getTheExpectation(); 
}
```

This means:
- **Setters**: Always use `setXxx(...)` prefix (unless adding/removing from collections)
- **Getters**: Use `getXxx()` pattern (throws NPE if not set)
- **Has methods**: Implement `hasXxx()` for all settable properties
- Return types should be consistent and predictable

### Builders Requiring Updates

Based on this analysis, the following builders have methods that deviate from the proposed pattern:

1. **DeliveryBuilder** - Uses `xxx()` pattern instead of `getXxx()` for getters
2. **BoundedContextBuilder** - Uses `tenantIndex()` instead of `getTenantIndex()`
3. **Client.Builder** - Uses `tenant()` instead of `getTenant()`
4. **ActorRequestFactory.Builder** - Has `getActor()` but missing `hasActor()`
5. Most builders are missing `hasXxx()` methods entirely

