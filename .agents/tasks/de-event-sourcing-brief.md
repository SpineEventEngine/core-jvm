# Brief for planning migration of Aggregates to non-event-sourcing mode

## Current state

Currently, the `Aggregate` (and descendants) in Spine are designed to be event-sourced.
This means that the state of an `Aggregate` is reconstructed from a sequence of events,
and all changes to the state are recorded as events.
                                          
An aggregate emits events in the command handling methods annotated with `@Assign`.
We call these methods command handling or command handler methods.
These methods return one or several events, that are subsequently applied to
the state of the aggregate via methods annotated with `@Apply`.
We call these methods event appliers.

When an aggregate is loaded from the storage, a sequence of events is loaded and played
over the aggregate. Sometimes a snapshot of the aggregate state is involved.
In this case, the snapshot is used as the basis on over which events are played.

Event sourcing works in general and in simple cases. 
But the production usage history shows that event-sourcing is fragile.

1. If the validation logic of an aggregate state changes in a way that is not
   compatible with the event history, the aggregate may become un-loadable.

2. If an event applier fails (because of a new validation rules or a programming error)
   and there were more than one event emitted by the command handler, the aggregate
   becomes "partically valid" because some events were applied to the state, and
   some don't.

3. It is not possible to change the state of an aggregate in the way that is not
   compatible with the previous history (and a previous snapshot). We cannot say,
   "This event no longer applies." We have to handle even outdated events
   because they are recored in the aggregate history.

The second problem violates the general idea behind the aggregates.
The job of the aggregates is to protect business invariants. 
They cannot be "partically valid".
When an aggregate emit events, it is expected that they reflect the state of
the aggregate, and they are facts from which we can derive other data.
When something about aggregates breaks, the whole system becomes less truthful.

## Suggested solution in brief

1. Load aggregates from their latest state, not from the event history.
2. Update the state of the aggregates in the body of the command handler method, and
   emit events as the facts that the command was applied to the aggregate state.
3. Keep the event history only for the purpose of trackability of the aggregate history.
   The `AggregateStorage` seem to support storing the latest aggregate state via
   `EntityRecordStorage`, and this works (if memory serves) when aggregate state
   can be queried from the client side.
4. Optionally keep the history of recent aggregate states to the configured depth.
   This is to help with analyzing the history alogn with the stored events.
   We probably need an additional `EntityHistoryStorage` for this.
   We may want to store the histroy of `ProcessManager`s in the future too.
5. Deprecate `@Apply` annotation during the transition period and remove it in v2.0.0.
6. All the test fixtures and all the examples (spine-examples GitHub org.) must be migrated 
   to non-event sourced aggregates.

## Implementation languages

The code that is going to be updated should stay in Java. 
This is to minimize the diff in this big feature change.

New types should be written in Kotlin to minimise the future migration.

If a test suite needs to be updated and is in Java, it stays in Java.
New test suites should be written in Kotlin.
