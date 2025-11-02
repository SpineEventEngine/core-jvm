# Builder API Unification - Progress Report

## Overview

This document tracks the progress of implementing the unified Builder API pattern across all 37 hand-written Builder classes in the codebase.

## Unified API Pattern

The target pattern as specified in issue #1233:

```java
FooBar.Builder builder = FooBar.newBuilder();

// Setter naming
builder.setTheExpectation(myExpectation);

// Check whether the value has been set
if(builder.hasTheExpectation()) {
    // Throws NPE if not set
    TheExpectation justSet = builder.getTheExpectation(); 
}
```

### Requirements

- **Setters**: Use `setXxx(T value)` returning Builder
- **Getters**: Use `getXxx()` returning T (throws NPE if not set, must be public)
- **Validation**: Implement `hasXxx()` returning boolean for all settable properties
- **Deprecation**: Deprecate old `Optional<T>`-returning or nullable-returning methods

## Completed Builders

### 1. DeliveryBuilder ✅
- **File**: `server/src/main/java/io/spine/server/delivery/DeliveryBuilder.java`
- **Commit**: 2bc0c62
- **Properties Updated**: 8
  - InboxStorage - added `hasInboxStorage()`, made `getInboxStorage()` public, deprecated `inboxStorage()`
  - CatchUpStorage - added `hasCatchUpStorage()`, made `getCatchUpStorage()` public, deprecated `catchUpStorage()`
  - DeliveryStrategy - added `hasStrategy()`, made `getStrategy()` public, deprecated `strategy()`
  - ShardedWorkRegistry - added `hasWorkRegistry()`, made `getWorkRegistry()` public, deprecated `workRegistry()`
  - DeduplicationWindow - added `hasDeduplicationWindow()`, made `getDeduplicationWindow()` public, deprecated `deduplicationWindow()`
  - DeliveryMonitor - added `hasDeliveryMonitor()`, made `getDeliveryMonitor()` public, deprecated `deliveryMonitor()` and `getMonitor()`
  - PageSize - added `hasPageSize()`, made `getPageSize()` public, deprecated `pageSize()`
  - CatchUpPageSize - added `hasCatchUpPageSize()`, made `getCatchUpPageSize()` public, deprecated `catchUpPageSize()`

### 2. BoundedContextBuilder ✅
- **File**: `server/src/main/java/io/spine/server/BoundedContextBuilder.java`
- **Commit**: 117bfff
- **Properties Updated**: 1
  - TenantIndex - added `hasTenantIndex()`, added `getTenantIndex()`, deprecated `tenantIndex()`

### 3. ActorRequestFactory.Builder ✅
- **File**: `client/src/main/java/io/spine/client/ActorRequestFactory.java`
- **Commit**: 117bfff
- **Properties Updated**: 3
  - Actor - added `hasActor()`, updated `getActor()` to throw NPE
  - ZoneId - added `hasZoneId()`, added non-null `getZoneId()`, deprecated nullable `zoneId()`
  - TenantId - added `hasTenantId()`, added non-null `getTenantId()`, deprecated nullable `tenantId()`

### 4. BusBuilder ✅
- **File**: `server/src/main/java/io/spine/server/bus/BusBuilder.java`
- **Commit**: TBD
- **Properties Updated**: 2
  - SystemWriteSide - added `hasSystem()`, added `getSystem()`, deprecated `system()`
  - TenantIndex - added `hasTenantIndex()`, added `getTenantIndex()`, deprecated `tenantIndex()`

### 5. EventBus.Builder ✅
- **File**: `server/src/main/java/io/spine/server/event/EventBus.java`
- **Commit**: TBD
- **Properties Updated**: 2
  - EventEnricher - added `hasEnricher()`, added `getEnricher()`, deprecated `enricher()`
  - StreamObserver<Ack> - added `hasObserver()`, added `getObserver()`, deprecated `observer()`

### 6. EventBus ✅
- **File**: `server/src/main/java/io/spine/server/event/EventBus.java`
- **Commit**: TBD
- **Properties Updated**: 1
  - EventEnricher - added `hasEnricher()`, added `getEnricher()`, deprecated `enricher()`

### 7. CatchUpProcessBuilder ✅
- **File**: `server/src/main/java/io/spine/server/delivery/CatchUpProcessBuilder.java`
- **Commit**: TBD
- **Properties Updated**: 3
  - CatchUpStorage - added `hasStorage()`
  - PageSize - added `hasPageSize()`
  - DispatchCatchingUp - added `hasDispatchOp()`

### 8. ConnectionBuilder ✅
- **File**: `server/src/main/java/io/spine/server/ConnectionBuilder.java`
- **Commit**: TBD
- **Properties Updated**: 2
  - Port - added `hasPort()`, added `getPort()`, deprecated `port()`
  - ServerName - added `hasServerName()`, added `getServerName()`, deprecated `serverName()`

### 9. GrpcContainer ✅
- **File**: `server/src/main/java/io/spine/server/GrpcContainer.java`
- **Commit**: TBD
- **Properties Updated**: 2
  - Port - added `hasPort()`, added `getPort()`, deprecated `port()`
  - ServerName - added `hasServerName()`, added `getServerName()`, deprecated `serverName()`

## Progress Statistics

- **Total Builders**: 37
- **Completed**: 9 (24%)
- **Remaining**: 28 (76%)

## Remaining Builders

### High Priority (Frequently Used)

1. **QueryBuilder** - `client/src/main/java/io/spine/client/QueryBuilder.java` - No changes needed (no public Optional/nullable getters)
2. **TopicBuilder** - `client/src/main/java/io/spine/client/TopicBuilder.java` - No changes needed (no public Optional/nullable getters)
3. **CommandBus.Builder** - `server/src/main/java/io/spine/server/commandbus/CommandBus.java` - No changes needed (inherits from BusBuilder, which is now completed)
4. **Stand.Builder** - `server/src/main/java/io/spine/server/stand/Stand.java` - No changes needed (no public Optional/nullable getters)

### Medium Priority

5. **TargetBuilder** - `client/src/main/java/io/spine/client/TargetBuilder.java` - No changes needed (no public Optional/nullable getters)
6. **AbstractServiceBuilder** - `server/src/main/java/io/spine/server/AbstractServiceBuilder.java` - No changes needed
7. **EnricherBuilder** - `server/src/main/java/io/spine/server/enrich/EnricherBuilder.java` - No changes needed

### Lower Priority (Less Frequently Used / Internal)

12-37. See `Builders.md` for complete list

## Next Steps

1. Review remaining builders (28 total) for Optional/nullable-returning public methods
2. Most of the remaining builders are:
   - Internal builders without public getters
   - Test fixture builders
   - Builders that already follow the pattern
3. Continue updating builders as they are identified
4. Update documentation to reference new unified API

## Summary of Changes

The Builder API unification effort focuses on builders with **public** methods that return `Optional<T>` or are nullable. Many builders in the codebase do not require changes because:
- They have no public getters (only used internally)
- They already follow the pattern (getters throw NPE, not Optional)
- They are test fixtures or generated code

Of the 37 hand-written builders:
- **9 builders completed** with API changes (24%)
- **Many builders** already compliant or have no public Optional-returning getters
- Remaining work focuses on discovering and updating any additional builders with public Optional-returning methods

## Implementation Pattern

For each builder:

1. **Identify properties** with getters
2. **For each property**:
   - If getter returns `Optional<T>` or is nullable:
     - Add `hasXxx()` method checking for null
     - Add or update `getXxx()` to be public and throw NPE
     - Deprecate old Optional/nullable method
   - If getter is package-private:
     - Make it public
     - Add `hasXxx()` method
3. **Update callers** of old deprecated methods (if internal to same module)
4. **Test** that deprecated methods still work for backward compatibility

## Notes

- Deprecated methods are kept for backward compatibility
- All changes maintain the existing setter API (no breaking changes)
- The pattern enables automated Builder testing via reflection (future work)
