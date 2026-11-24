---
slug: async-standalone-handlers
branch: claude/issue-1735-analysis-plan-edeeba
owner: claude
status: in-progress
started: 2026-11-24
---

## Goal

Let a standalone handler run its handler methods outside the dispatch thread, so that a long
call to an external service does not keep `Delivery` waiting (issue #1735).

This PR covers the execution port with its default implementation, and the four handler types:
`AsyncAssignee`, `AsyncCommander`, `AsyncEventReactor`, `AsyncEventSubscriber`.

## Context

- `CommandBus.dispatch()` and `MulticastBus.callDispatchers()` call `dispatcher.dispatch()`
  on the thread that posts the signal and ignore the returned `DispatchOutcome`. A standalone
  handler invokes its receptor right there. So a process manager that posts a command while
  `Delivery` serves its inbox waits for the handler of that command.
- The four bases differ. `AbstractAssignee.dispatch()` and `AbstractEventReactor.dispatch()` can
  be overridden. `AbstractEventSubscriber.dispatch()` is final and calls the overridable
  `handle()`. `AbstractCommander.dispatch()` and `dispatchEvent()` are both final.
- The current tenant is a `ThreadLocal` (`CurrentTenant`). A worker thread does not have it
  unless the work is wrapped into `TenantAwareRunner`.
- Standalone handlers do not implement `ReceptorLifecycle`, so nothing in the receptor
  invocation depends on the dispatch thread.
- Nothing reads the `DispatchOutcome` of a standalone handler: the buses ignore it, and
  `DelegatingEventDispatcher` only passes it on. `DispatchOutcomeHandler` skips the cases it
  does not know, and `DispatchOutcomeMixin.readValue()` falls back to `getField()`.
- External events reach these handlers through the same `dispatch()`: `BusAdapter` posts them
  to the `EventBus`.
- `SystemSettings.defaults()` is the precedent for a default that depends on the environment:
  System events are posted in parallel in production and on the calling thread under `Tests`.
- `jvmDefault` is `NO_COMPATIBILITY` (`KotlinConfig.kt`), so a default method of a Kotlin
  interface is a real JVM default, and a Kotlin `fun interface` can be a Java lambda.

## Decisions (2026-11-24)

1. Scope: the port, its default implementation, and all four `Async*` types, with ordinary
   blocking handler methods.
2. Guarantee: accepted work lives in memory only. If the JVM stops before an async handler
   finishes, the signal is not handled again. The docs say so. The port receives the handler
   class and the handled signal, so an application can build a durable or retrying policy.
3. Hierarchy: each `Async*` type extends its synchronous counterpart. `Policy`, `Reaction`
   and `SingleCommandAssignee` stay synchronous.
4. Tests: under the `Tests` environment the default execution runs the handler on the calling
   thread. A test that wants real threads sets an execution on the context builder.
5. Closing: `BoundedContext.close()` stops accepting async work, then waits up to a timeout
   for running and waiting invocations. What remains is discarded with a warning.
   Invocations are refused from the moment the closing starts, also those caused by
   the handlers that finish during the wait; see "Closing and chained handlers" below.
6. API types: no `java.util.concurrent` types in the API (the strategy is to migrate to
   Kotlin and stay compatible with Java). `AsyncInvocation` has one method, `run()`, which
   handles the signal on the calling thread. Making the work asynchronous is the job of
   the `AsyncExecution` implementation. Two `run…` methods in one class were refused as
   prone to errors.

## Design

### The port: `io.spine.server.async` (new package, Kotlin)

```kotlin
public fun interface AsyncExecution : AutoCloseable {

    /** Accepts the invocation promptly and runs it outside the calling thread. */
    public fun submit(invocation: AsyncInvocation)

    /** Stops accepting invocations and waits for the accepted ones. Does nothing by default. */
    override fun close() {}

    public companion object {
        @JvmStatic public fun newBuilder(): Builder      // the default implementation
        @JvmStatic public fun onCallingThread(): AsyncExecution
    }
}

public class AsyncInvocation internal constructor(...) {
    public fun handlerClass(): Class<*>
    public fun signal(): Signal<*, *, *>                  // the command or the event
    public fun run()                                      // returns when the handling is over
}
```

- The rule for an implementation: an invocation is ongoing until `run()` returns. A limit
  counts the calls of `run()` that have not returned yet.
- `run()` handles the signal under its tenant and posts the produced messages before it
  returns.
- `run()` does not throw for a failure of the handler method: it becomes
  `HandlerFailedUnexpectedly`, as today. An exception of the framework itself (a failed
  posting) is logged as an error and rethrown to the execution. An `Error`, such as
  a `Mistake`, is thrown as it is, like the buses do; the default execution logs it in
  the handler of its threads.
- `run()` handles the signal once. A second call throws `IllegalStateException`.

### The default implementation (`internal class LimitingExecution`)

```java
AsyncExecution.newBuilder()
    .setLimit(AcmeRpcHandler.class, 2)     // at most two ongoing invocations of this class
    .setLimit(16)                          // for the classes without a limit of their own
    .setCloseTimeout(Durations.fromSeconds(10))
    .build();
```

- Each handler class has a counter of ongoing invocations and a queue of waiting ones.
  `submit()` queues the invocation, and passes it to the pool at once if the class has
  a free slot. It never blocks. The queue is not bounded.
- A worker thread calls `invocation.run()` and releases the slot when it returns. Then the
  first waiting invocation of that class takes the slot.
- If the pool cannot take an invocation (a thread cannot be created), the slot stays free,
  and the failure goes to the caller.
- With a limit of 1, a class handles its signals one by one in the order of submission.
- The pool is a cached pool of daemon threads named after the context. The limits bound
  the number of threads. The execution creates the pool and shuts it down.
  `java.util.concurrent` stays inside the implementation.
- Defaults: 8 ongoing invocations per handler class, 30 seconds of close timeout.
- `close()`: from this moment `submit()` throws `IllegalStateException`; wait until nothing
  is ongoing or waiting, or until the timeout; then discard the waiting invocations,
  interrupt the running ones, and log a warning with the number of lost invocations per
  handler class.
- A handler run by the execution may call `close()`, for example, by closing its context.
  `close()` does not wait for that handler and does not interrupt it.
- The execution tells its context from another one by the instance, not by the name.
  Several threads may call `close()` at once; each returns when the execution is closed.
- A close timeout too long to be counted in nanoseconds, such as `Durations.MAX_VALUE`,
  means waiting for as long as it takes.

### Closing and chained handlers

A handler that finishes during the wait may post a message for another asynchronous handler
of the same context. With "stop accepting, then wait", `submit()` refuses that message:
the posting fails with `IllegalStateException`, the worker thread logs the error, and
the message is not handled. The first implementation kept accepting during the wait to
avoid this. The owner chose to refuse at once (2026-11-24), so this is the behavior.
The tests of `AsyncCommanderSpec` give the generated command a synchronous handler,
because some of them end while the commanding method is running.

### The context

- `BoundedContextBuilder.setAsyncExecution(AsyncExecution)`, public.
- `BoundedContext` creates the default lazily, when the first async handler is registered:
  `onCallingThread()` under `Tests`, `newBuilder().build()` otherwise. A context without async
  handlers creates no pool.
- `BoundedContext.InternalAccess.asyncExecution()` gives it to the handlers.
- `closeComponents()` closes the execution first, while the buses are still open, so that
  the draining handlers can post their results.
- A context closes its execution. Two contexts must not share one instance.

### The handlers (Kotlin, next to their counterparts)

| New type | Extends | Overrides |
| --- | --- | --- |
| `io.spine.server.command.AsyncAssignee` | `AbstractAssignee` | `dispatch()` |
| `io.spine.server.command.AsyncCommander` | `AbstractCommander` | `dispatch()`, `dispatchEvent()` |
| `io.spine.server.event.AsyncEventReactor` | `AbstractEventReactor` | `dispatch()` |
| `io.spine.server.event.AsyncEventSubscriber` | `AbstractEventSubscriber` | `handle()` |

- Each override is final. It wraps the call to `super` into an `AsyncInvocation`, submits it,
  and returns at once. The synchronous code of the bases stays as it is.
- `AbstractCommander.dispatch()` and `dispatchEvent()` lose `final`, as in `AbstractAssignee`
  and `AbstractEventReactor`. This is the only change to the existing bases.
- The shared part (get the execution in `registerWith()`, build the invocation) lives in
  one internal class of `io.spine.server.async`. The tenant and the logging of framework
  failures are in `AsyncInvocation.run()`.
- The override returns a new case of `DispatchOutcome`: `bool sent_to_async_execution = 9`,
  next to `sent_to_inbox` and `published_to_remote`, with a factory in `DispatchOutcomes`.
- The KDoc of each type says: the handler method runs on another thread; one instance may run
  several invocations at once; the order of handling is not the order of posting unless
  the limit is 1; accepted work does not survive a restart.

## Plan

### Protobuf

- [x] `dispatching.proto`: add `sent_to_async_execution = 9` to `DispatchOutcome.result`.

### Production code

- [x] `io.spine.server.async`: `AsyncExecution` with `Builder`, `AsyncInvocation`,
      `LimitingExecution`, the calling-thread execution, the shared internal helper.
- [x] `DispatchOutcomes.sentToAsyncExecution(signal)`.
- [x] `BoundedContextBuilder.setAsyncExecution()`; `BoundedContext`: the lazy default,
      `InternalAccess.asyncExecution()`, closing before the buses.
- [x] `AbstractCommander`: remove `final` from `dispatch()` and `dispatchEvent()`.
- [x] `AsyncAssignee`, `AsyncCommander`, `AsyncEventReactor`, `AsyncEventSubscriber`.

### Tests (Kotlin, Kotest assertions, no sleeps: latches)

- [x] `LimitingExecutionSpec`: a third invocation waits while two are ongoing and starts when
      one returns; classes do not share a limit; the default limit; the order of submission
      with a limit of 1; an invocation that throws releases its slot; `close()` drains, times
      out with a warning and interrupts the handler, and rejects later invocations.
- [x] `AsyncInvocationSpec`: `run()` handles the signal on the calling thread and returns
      after the results are posted; a second call fails.
- [x] `AsyncAssigneeSpec`, `AsyncCommanderSpec`, `AsyncEventReactorSpec`,
      `AsyncEventSubscriberSpec`: `dispatch()` returns while the handler is held; the handler
      runs on another thread; its events or commands are posted after it is released;
      a thrown rejection is posted; an exception gives `HandlerFailedUnexpectedly`; the tenant
      is set on the worker thread of a multi-tenant context; external events are handled.
- [x] `AsyncHandlersIgTest`: a process manager posts a command to an `AsyncAssignee` held on
      a latch; the inbox message of the process manager is delivered before the latch opens;
      after it opens, the process manager gets the resulting event.
- [x] Context specs: under `Tests` the handler runs on the calling thread and `BlackBox`
      asserts at once; `setAsyncExecution()` is used; no execution is created without async
      handlers; `close()` closes the execution before the buses.
- [x] `AsyncExecutionJavaSpec`: a Java handler class, a Java lambda as the execution, and
      the builder used from Java.

### Wrap-up

- [x] KDoc of the new types and the package.
- [x] Bump the version (not breaking): `.782` (#1736 took `.781` on 2026-11-24).
- [x] `./gradlew check dokkaGenerate`; read the test counts; mutation checks (the slot
      released before `run()` returns; the tenant wrapping removed).
- [x] The full `./gradlew build` at PR prep, after the version bump.
- [x] Reviewers: `spine-code-review`, `kotlin-engineer`, `review-docs`.

Not in this PR: handler methods that wait without a thread (`suspend` methods, or methods
returning `CompletionStage<T>` as the issue mentions) — a separate decision, nothing is
reserved for it here; async variants of `Policy`, `Reaction` and `SingleCommandAssignee`;
durable storage of accepted work.

## Log

- 2026-11-24 — issue analysed, questions answered, plan written after two revisions of
  the port (no `CompletionStage`; one `run()` method).
- 2026-11-24 — implemented (uncommitted). Fixtures: the Shipping test context
  (`server/src/testFixtures/proto/.../shipping`, `server/src/test/kotlin/.../async/given`).
  70 new tests pass. Not covered by a test: "no execution is created without async handlers"
  (not observable without reflection); external events are tested for the reactor only,
  as all four types get them through the same `dispatch()`.
- 2026-11-24 — mutation checks. With the slot released before `run()` returns, 7 tests fail
  (the limit tests of `LimitingExecutionSpec`, the limit test of `AsyncHandlersIgTest`).
  With the tenant wrapping removed, 4 tests fail (assignee, commander, subscriber,
  invocation; the reactor passes, because `AbstractEventReactor` sets the tenant itself).
- 2026-11-24 — `check dokkaGenerate` green before the review fixes: `core` 174,
  `core-testlib` 20, `client` 183, `client-testlib` 2, `server` 2629 (2559 + 70),
  `server-testlib` 162, `server-otel` 22 tests, 0 failures, 0 skipped.
- 2026-11-24 — reviewers `review-docs` and `spine-code-review`: no must-fix items. Taken:
  the wording of the "kept in memory" caveat (it named only the waiting signals), examples
  for the three other handler types, the `Tests` paragraph removed from the handler docs;
  one place that starts invocations (`startWaiting`), so that a slot is not lost when
  a thread cannot be created, with a test; the race in `never exceeding the limit`;
  the timeout converted when the execution is built; the execution introduced to
  the context first in `init()`. `AsyncInvocation.run()` logs exceptions only; an `Error`
  is thrown as it is.
- 2026-11-24 — reviewer `kotlin-engineer`: no correctness bug. Taken: named arguments at
  the two constructor calls; a second, concurrent `close()` waits for the first one;
  an invocation passed to a thread but not started before the wait was given up is not run;
  the slot is taken after the pool has the invocation (no flag needed); the close timeout
  that overflows nanoseconds; the order test no longer imposes the order itself; a test for
  an `Error` thrown by an invocation; the largest number of calls at once in the stub
  service, asserted in `AsyncHandlersIgTest`. Also found by it: a context refused by
  a shared execution left its System context open, because `buildDomain()` was outside
  the `try` of `BoundedContextBuilder.build()`; fixed with a test. Not taken:
  `SCREAMING_SNAKE_CASE` for the constants (`kotlin-code-style.md` prefers
  `lowerCamelCase`).
- 2026-11-24 — owner's question: why keep the timeout in nanoseconds. The execution keeps
  the given `Duration` again, and converts it where the wait starts.
- 2026-11-24 — mutation checks repeated on the reworked `LimitingExecution`: 8 tests fail
  with the slot released early; the new test fails when a refused invocation keeps its slot.
- 2026-11-24 — found on the way, not changed: `AbstractCommander` reports a failed commanding
  method nowhere (its `DispatchOutcomeHandler` has no `onError`), unlike the other three
  bases, which post `HandlerFailedUnexpectedly`. `AsyncCommander` inherits this.
- 2026-11-24 — final `check dokkaGenerate --no-build-cache` green with all the review fixes:
  `core` 174, `core-testlib` 20, `client` 183, `client-testlib` 2, `server` 2635
  (2559 + 76 new), `server-testlib` 162, `server-otel` 22 tests, 0 failures, 0 skipped.
  Nothing is committed. Left: the version bump, the full build, the PR.
- 2026-11-24 — the owner chose "refuse at once" for the closing. `LimitingExecution` has
  two flags now: `open` turns `false` when `close()` is called, `closed` turns `true` when
  the wait is over. The test `accept the invocations submitted while it waits` became
  `refuse new invocations while it waits for the submitted ones`.
- 2026-11-24 — `check dokkaGenerate --no-build-cache` green after that change: `server`
  2635 tests (2559 + 76 new), the other modules as before, 0 failures, 0 skipped. Mutation:
  with `open = false` moved after the wait, the new test fails. Nothing is committed.
- 2026-11-24 — the owner asked to bump, commit and open the PR. `master` had moved to
  `.781` with #1736 (dependency versions only), so the branch, which had no commits, was
  fast-forwarded to it, and the version to take is `.782`.
- 2026-11-24 — committed; `./config/pull` committed (Base for the build script `.465`,
  CoreJvm `.781`, ToolBase `.460`, the `config` pointer, the publish workflow); version
  `.782`; `clean build dokkaGenerate` green; dependency reports updated.
- 2026-11-24 — second pass of the three reviewers on the committed code: no must-fix.
  Taken: `@throws` of `submit()` says "if `close()` has been called"; a test that
  an invocation which has not started when the wait is given up is not run. Not taken:
  logging the invocation dropped when a thread cannot be created in `release()`, which
  needs a JVM that is out of threads.
- 2026-11-24 — PR #1737 opened. Codex, two findings, both confirmed by tests that failed
  first: the execution compared the names of contexts, so two contexts with one name
  (two applications in one JVM) could share it; `close()` called by a handler of
  the execution waited for that handler, forever with a long timeout. Fixed: the context
  is compared by the instance; `close()` skips the invocation of the calling thread
  (`laneOfThread`), and `WorkerPool.shutDown()` puts back the interruption status of
  the caller. `server`: 80 tests of the feature.
