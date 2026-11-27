---
slug: shared-storage-factory-close
branch: cursor/close-shared-storage-factory-ce2a
owner: cursor
status: done
started: 2026-11-27
---

## Goal

Each application of a delivery test is given its own `StorageFactory` and closes it.
The `Delivery` is given a factory of its own, which the test closes after the reads.
`storageFactory()` returns a new instance on every call. No wrapper suppresses `close()`.

## Context

`CatchUpTest` and `DeliveryTest` build one application per context. The applications share
the factory from `storageFactory()`. `ServerEnvironment.close()` closes the factory it was
given, and `BlackBox.close()` closes the application. The suite then reads the catch-up
or inbox storage of its `Delivery`, which is not closed with the application. An in-memory
factory survives that. A JDBC factory closes the pool, and the read fails.

`use(factory)` transfers ownership to the environment. `ServerEnvironmentTest` requires
`close()` to close that factory. The suite must not change that contract. The suite created
the shared factory and still uses it after an application closes, so the suite closes it.

## Plan

- [x] Confirm the close order in `BlackBox`, `Application`, and `ServerEnvironment`
- [x] Give each application a new factory and let the application close it
- [x] Give `Delivery` its own factory, closed by the test after the reads
- [x] Cover a factory that rejects reads after `close()`
- [x] Run the in-memory delivery suites and the fast storage suites

## Log

- 2026-11-27 — The environment still closes a factory passed to `use`. The suite keeps the
  shared factory and closes it after the applications.
- 2026-11-27 — In-memory catch-up, delivery, storage, and event-store suites passed.
  `AbstractDeliveryTestSpec` checks the factory is closed once.
