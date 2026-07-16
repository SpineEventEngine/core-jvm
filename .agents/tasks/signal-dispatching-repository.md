# Task: Introduce `SignalDispatchingRepository`

## The problem

`ProcessManagerRepository` mixes two responsibilities: event dispatching (inherited
from `EventDispatchingRepository`) and command routing/dispatching (declared locally).
The command-routing cluster is generic — nothing in it is specific to
process managers — and `AggregateRepository` holds a near-verbatim
duplicate of the same cluster.

## Solution

Extract the command-routing machinery into a new abstract class,
`SignalDispatchingRepository` — a repository dispatching *signals*, i.e. both events
and commands — sitting between `EventDispatchingRepository` and
`ProcessManagerRepository`:

```
Repository
 └─ RecordBasedRepository
     └─ DefaultRecordBasedRepository
         └─ EventDispatchingRepository           (event routing)
             └─ SignalDispatchingRepository      (+ command routing)  ← NEW
                 └─ ProcessManagerRepository
```

The new class lives in `io.spine.server.entity`, extends
`EventDispatchingRepository<I, E, S>`, and implements `CommandDispatcherDelegate`.

## Members pulled up from `ProcessManagerRepository`

- `commandRouting` field (memoized supplier) and its constructor initialization.
- `registerWith(BoundedContext)` override calling `doSetupCommandRouting()`.
- `doSetupCommandRouting()` — applies `CommandRoutingSetup`, then the user callback.
- `setupCommandRouting(CommandRouting<I>)` — **becomes `protected abstract`**:
  every concrete repository type must take an explicit stance on command routing.
- `commandRouting()` private accessor.
- `dispatchCommand(CommandEnvelope)` — route → send to the inbox.
- `route(CommandEnvelope)` and `onCommandTargetSet(...)` private helpers.

`ProcessManagerRepository` re-declares `setupCommandRouting` as a no-op `@Override`:
a Process Manager may handle only events, so PM repositories are not forced to
customize command routing.

## Deliberately staying in `ProcessManagerRepository`

- `commandClasses()` — PM model lookup (`processManagerClass().commands()`).
- `checkDispatchesMessages()` — PM-specific error wording.
- `setupInbox(...)` — registers the PM-specific endpoints.
- `onRoutingFailed(...)` override — needs `postIfCommandRejected()` from
  `EventProducingRepository`, which stays a PMR interface.
- `postCommands(...)` — Commander support.

## Out of scope

`AggregateRepository`'s duplicate command-routing cluster cannot reuse the new class
yet — it extends `Repository` directly and is a `CommandDispatcher` (not a delegate).
Unifying it is future (Phase F) work.

## Verification

- `./gradlew build` — behavior is unchanged; existing `ProcessManagerRepositoryTest`
  covers the moved code (`setupOfCommandRouting`, `RoutingFailed` on unknown command,
  command dispatch throughout the suite).
- `./gradlew dokkaGenerate` — moved/updated doc links resolve.
