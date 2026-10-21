---
name: system-events-synchronous-in-tests
description: Under `Tests`, system events (e.g. `EntityPreparedForCatchUp`) are posted synchronously, in production in parallel — delivery and catch-up races stay hidden in tests unless parallel posting is enabled
metadata:
  type: reference
---

`SystemSettings.defaults()` posts system events synchronously under `Tests`, and in
parallel (the common pool) in any other environment. A catch-up step that travels as
a system event — `EntityPreparedForCatchUp`, emitted by `CatchUpEndpoint` — is therefore
written into the shard during the same delivery run in tests, but may arrive after the run
ends in production.

**Why it matters:** the repeated delivery of `CatchUpStarted` (task
`catch-up-started-delivered-twice`, 2026-10-07) never reproduced with the default test
setup, and did with parallel posting: 1 run in 3 with the common pool, every run with the
events held. The repeated delivery ended in a livelock of the shard.

**How to apply:**
- To test a delivery or catch-up race, build the context with
  `systemSettings().enableParallelPosting().useCustomPostingExecutor(executor)`. A holding
  executor, which runs each task in the calling thread or queues it until released, keeps
  the test deterministic and single-threaded. See `KeptStartSignalIgTest` and
  `DuplicateEntityPreparedIgTest`.
- When probing such a race, bound the trace output and add a watchdog that makes
  the `DeliveryMonitor` stop the runs and the shard observer stop dispatching. Without one,
  a livelocked probe ran 4.5 h into an `OutOfMemoryError`.

Related: [[feedback_delivery_order_is_eventual]] — no frozen clocks in such tests either.
