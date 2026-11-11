# `ServerEnvironment` and `Application` as instances

## Status

**Implemented, in review.** Continues the work of #1717 (`claude/application-tenant-index`,
`2.0.0-SNAPSHOT.670`), which was merged into `master` on 2026-11-10 while this work started, so
the PR targets `master`. The design was discussed on 2026-11-10 (questions 1–8), and the owner
asked to implement it the same day. See "As implemented" for what the implementation and its
pre-PR review added to the design.

Decisions:

- Q1 (2026-11-10): `ContextRegistry` stays a part of each `Application` instance, with both of
  its jobs: the list of the contexts of the application, and the guards of context names and
  entity state types. The guards are not moved to `ServerEnvironment`, and there is no JVM-wide
  registry. See section 4.
- Q2 (2026-11-10): the singletons are removed, not deprecated: `ServerEnvironment.instance()`,
  the static `ServerEnvironment.under()` and `when()`, the top-level Kotlin `under<E> {}`, and
  `Application.instance()`. `BoundedContextBuilder.build()` stops being public: contexts are
  built by `Application.Builder.build()`. See sections 1–3.
- Q3 (2026-11-10): the contexts of an application are the ones given to its builder; none joins
  later. `ThirdPartyContext` is added to the application builder like any other context.
  See section 6.
- Q4 (2026-11-10): an application owns its `ServerEnvironment`. `Application.close()` closes the
  contexts, then the tenant index, then the environment. `Application.Builder.build()` refuses
  an environment that another application owns, or that is closed. See section 2.
- Q5 (2026-11-10): the application sets the technologies of a context when it builds it. The
  technology setters and getters of `BoundedContextBuilder` added by #1717 become
  package-private. A `BoundedContext` keeps no reference to its `Application`; the code that
  reaches for the application from inside a context today is rewritten. See sections 3–5.
- Q6 (2026-11-10): tests build contexts through `Application`. `server-testlib` gets a public
  helper that builds an application of one context; `BlackBox` and the tests of core-jvm use
  it. See section 8.
- Q7 (2026-11-10): `Server.shutdown()` and `shutdownAndWait()` close the whole application they
  serve: its contexts, its tenant index, and its environment. This supersedes Q22 of
  `reject-unregistered-tenants.md`. See section 7. Superseded by Q10.
- Q8 (2026-11-10): the tenant index is set with `Application.Builder.setTenantIndex()`.
  `Application.use(TenantIndex)` and the internal `closeIndex()` are removed. Nothing else about
  tenants changes in this work. See section 9.
- Q9 (2026-11-10): a builder made by one of the `assumingTests()` factories is marked as made
  for tests. Its public `build()` builds the context as the only context of a new application
  over a new environment; for any other builder, `build()` throws `IllegalStateException`.
  `assumingTests(name)` and `assumingTests(name, multitenant)` serve the tests that need
  a particular name. A test that needs a particular environment adds the builder to an
  `Application`. `TestContexts.kt` with `buildForTests()` is removed. This revises Q6.
- Q10 (2026-11-11): `Server.shutdown()` and `shutdownAndWait()` stop the server only, and leave
  its application open, since several servers may serve one application. The usual sequence
  is to shut down the servers, and then to close the application. This supersedes Q7.

To confirm (assumed while writing, not asked):

1. `ServerEnvironment` is converted to Kotlin first, in a commit of its own (`git mv`), because
   its new `Builder` is new code, and a nested class must live in the file of its outer class.
2. A built `ServerEnvironment` cannot be changed: the settings are given to its builder only.
   It still keeps a value per `EnvironmentType`, and resolves the current type on each call,
   as today, so tests that switch `Environment.instance()` keep working.
3. Each `ServerEnvironment` instance has a `NodeId` of its own, generated as today.
4. Names: `Application.contexts()` (public), `Server.Builder.setApplication()`, and the helper of
   section 8.
5. A repository registered after the context is built, through the `@Internal`
   `InternalAccess.register()`, is not checked against the other contexts (section 4). Only
   tests do it (33 files); production code does not.
6. The work goes into one PR: `ServerEnvironment` cannot lose its singleton while
   `Application`, which needs an environment, still is one, and vice versa.

## Problem

After #1717, the framework reaches for two JVM-wide objects:

- `ServerEnvironment.instance()` provides the technologies: storage, transport, delivery,
  command scheduling, and tracing. #1717 moved most calls behind `BoundedContextBuilder`, but
  `BoundedContextBuilder.useEnvironmentForUnset()` and `Application.environment` still call it.
  Tests configure it with `ServerEnvironment.under(Tests.class).use(...)` and reset it with
  `reset()`.
- `Application.instance()` holds the registry of the open contexts and the tenant index.
  `BoundedContext.registry()`, `CatchUpProcess.domainContextOf()`, `Server.Builder`, and
  `TenantAwareTest` call it.

So the contexts of every test share one environment and one application. A context that a test
leaves open breaks later tests. An application cannot run two groups of contexts with different
technologies in one JVM.

Applications should build these objects themselves, and pass them where they are needed:

```java
var se = ServerEnvironment.newBuilder()
        .under(Production.class, env -> {
            env.use(myDelivery);
            env.use(myTracerFactory);
        })
        .build();
var app = Application.newBuilder()
        .setServerEnvironment(se)
        .add(contextBuilderOne)
        .add(contextBuilderTwo)
        .build();
```

```kotlin
val se = serverEnvironment {
    under<Production> {
        use(myDelivery)
        use(myTracerFactory)
    }
}
val app = application {
    serverEnvironment = se
    add(contextBuilderOne)
    add(contextBuilderTwo)
}
```

## Findings

### Where the singletons are used

| Call | Production | Tests and test fixtures |
|---|---|---|
| `ServerEnvironment.instance()` | `BoundedContextBuilder.useEnvironmentForUnset()`, `Application.environment` | 52 lines in 31 files |
| `ServerEnvironment.under(…)` | Kotlin `under<E> {}` in `ServerEnvironmentExts.kt` | 29 lines in 11 files (10 in `server`, 1 in `server-testlib`) |
| `Application.instance()` | `BoundedContext.registry()`, `CatchUpProcess.domainContextOf()`, `Server.Builder`, `TenantAwareTest` | 6 lines in 5 files |
| `BoundedContextBuilder.build()` | `ThirdPartyContext`, `BlackBox` | about 100 files build contexts |

### Downstream code

Repositories in `~/Projects/Spine` that use the affected API (files): `documentation` (28),
`examples` (19), `gcloud-jvm` (10), `chat-bot` (9), `jdbc-storage` (8), `compiler` (7),
`delivery` (6), `core-jvm-compiler` (2). Notable:

- `compiler`: `CodeGenerationContext` (`backend/.../Contexts.kt`) builds a context, and creates
  a `ThirdPartyContext` lazily, after it.
- `gcloud-jvm`: `DatastoreStorageFactory.configureTenantIndex()` called the removed
  `setTenantIndex()`; the follow-up of #1717 already plans to change it.
- `chat-bot` and the airport example of `documentation` use `ThirdPartyContext`.

### The catch-up machinery finds contexts through the registry

`InboxDeliveries.systemContextOf()` and `CatchUpProcess` call
`CatchUpProcess.domainContextOf(job)`, which asks `Application.instance().contexts()` for the
context serving the projection type of the job. Neither needs a registry:

- every inbox is registered in `Delivery` with the name of its context
  (`Repository.initInbox()` calls `withContext(context().name())`), so `InboxDeliveries` can take
  the name of the System context from the inbox of the projection type;
- the `CatchUpProcess` of a job lives in the System context of the domain context that serves
  the projection, so it can get the domain context from its own System context.

### Suspected: System projections replace each other's inboxes

Not verified by a test. Every System context has a `CommandLogRepository` and a
`ScheduledCommandRepository` (both `ProjectionRepository`). `InboxDeliveries.register()` keys
the inboxes of all types but the two catch-up types by the entity state type alone, and an
inbox of the same type replaces the earlier one. So in an application of two contexts, the
messages for `CommandLog` and `ScheduledCommandRecord` of both System contexts would go to the
repository of the System context built last. This also rules out a guard of state types in
`Delivery`. Out of scope; to verify in a session of its own.

## Proposed design

### 1. `ServerEnvironment` is built

`ServerEnvironment` gets a builder and loses its singleton:

- `ServerEnvironment.newBuilder()` returns a `ServerEnvironment.Builder`.
- `Builder.under(Class<? extends EnvironmentType<?>>, Consumer<TypeConfigurator>)` configures
  the values for an environment type. `TypeConfigurator` keeps its `use()` and `use…(Fn)`
  methods, which now set the values of the builder.
- Kotlin: `serverEnvironment { … }` creates and builds a builder; inside it,
  `under<E> { … }` is an inline reified member of the builder, with `TypeConfigurator` as the
  receiver of the block.
- `build()` returns the environment. Values not set have the defaults of today: in-memory
  storage and transport under `Tests`, a local `Delivery`, `ExecutorCommandScheduler`.
- Removed: `instance()`, the static `under()` and deprecated `when()`, `reset()`, the
  top-level Kotlin `under<E> {}`, and the trick in `TypeConfigurator` that reopened a closed
  singleton.
- An internal "owned" flag lets `Application.Builder.build()` refuse an environment that another
  application owns (Q4). The environment keeps no reference to the application.
- The Javadoc loses the section on the singleton. It says that the environment belongs to the
  application built with it, which closes it.

### 2. `Application` is built

```kotlin
public class Application private constructor(builder: Builder) : Closeable {

    /** The contexts of the application, in the order of building. */
    public fun contexts(): List<BoundedContext>

    /** The index of tenants of the multitenant contexts. */
    public fun tenantIndex(): TenantIndex

    /** Closes the contexts, then the tenant index, then the environment. */
    override fun close()

    public class Builder {
        public fun setServerEnvironment(environment: ServerEnvironment): Builder
        public fun setTenantIndex(index: TenantIndex): Builder
        public fun add(context: BoundedContextBuilder): Builder
        public fun add(context: ThirdPartyContext): Builder
        public fun build(): Application
    }

    public companion object {
        @JvmStatic
        public fun newBuilder(): Builder
    }
}

public fun application(block: Application.Builder.() -> Unit): Application
```

- `build()` throws `IllegalStateException` if no environment is set, if it has no contexts, or
  if the environment is closed or owned by another application. It then checks the names and
  the entity state types of all the contexts (section 4), and only then builds them, in the
  order they were added. If building one fails, it closes the ones already built.
- Kotlin DSL: `serverEnvironment = se` is a property of the builder. Java gets the fluent
  `setServerEnvironment()`. Both cannot have the same JVM signature, so the property setter
  gets `@set:JvmSynthetic` and a JVM name of its own.
- `contexts()` replaces the internal registry getter for callers that need the built contexts,
  for example the helper of section 8, and the Compiler, which queries its context.
- `close()` keeps the behavior of #1717 (reverse order, every part closed even if another one
  fails, the first failure thrown with the others suppressed), and adds the environment last.
- Removed: `instance()`, `use(TenantIndex)`, `closeIndex()`, and the internal `environment`
  property, which becomes private.

### 3. The application gives a context its technologies

- `Application.Builder.build()` sets the storage factory, `Delivery`, the factory of command
  schedulers, the transport factory, and the tracer factory of its environment on each
  `BoundedContextBuilder`, and then calls its `build()`.
- The setters and getters of these technologies, added by #1717, become package-private, and
  so does `BoundedContextBuilder.build()`. `useEnvironmentForUnset()` is removed: a builder
  without the technologies fails with `IllegalStateException`.
- The System context still gets the technologies of its domain context
  (`shareTechnologiesWith()`).
- The cases of `BoundedContextBuilderSpec` for the setters move to `ApplicationSpec`.

### 4. Guards checked when the application is built

A `BoundedContext` keeps no reference to its application (Q5). So the guards are checked once,
by `Application.Builder.build()`, before any context is built. This works because the contexts
of an application are known when it is built (Q3):

- no two contexts have one name, or the name of the System context of another one
  (`DuplicateContextNameException`);
- no two contexts have repositories of one entity state type
  (`DuplicateEntityStateException`).

Consequences:

- `BoundedContext.registry()` is removed. A context does not report the state types of its
  repositories, and does not unregister when it closes: the list of the contexts of an
  application is fixed, and a closed context stays in it (`close()` uses `closeIfOpen()`).
- `ContextRegistry` keeps the list and the checks. Its runtime API (`register`, `unregister`,
  `serve`, `forget`, `contextServing`) is removed if nothing else uses it.
- Two applications in one JVM are not checked against each other. They have environments of
  their own (Q4), so they share no `Delivery`, transport, or storage factory, unless the
  applications configure their storage factories over one database.
- Item 5 of "To confirm": `InternalAccess.register()` after the build is not checked.

### 5. Catch-up without the registry

- `InboxDeliveries` finds the System context of a catch-up message by the inbox of the
  projection type: `byType.get(type).inbox.contextName().toSystem()`. If the projection type
  has no inbox, it throws `IllegalStateException`, as `domainContextOf()` does today.
- A System context knows its domain context: `BoundedContextBuilder` tells it when the domain
  context is built. `CatchUpRepository` passes the domain context to each `CatchUpProcess`, as
  it passes `Delivery` with `injectDelivery()`. `CatchUpProcess.domainContextOf()` is removed.

### 6. `ThirdPartyContext` is added to the application

```kotlin
val calendar = ThirdPartyContext.singleTenant("Calendar")   // Not built yet.
val app = application {
    serverEnvironment = se
    add(scheduling)
    add(calendar)
}
calendar.emittedEvent(event, actor)
```

- `singleTenant()` and `multitenant()` return a context that is not built yet. It holds its
  `BoundedContextBuilder`, with the System settings of today (no command log, events not
  stored).
- `Application.Builder.build()` builds it like any other context and gives it the built
  `BoundedContext`. Until then, `emittedEvent()` throws `IllegalStateException`.
- `close()` and `isOpen()` stay. The application closes it with the other contexts.

### 7. `Server` serves an application

- `Server.Builder.setApplication(Application)` sets the application to serve. `build()` throws
  `IllegalStateException` without it. It replaces the package-private `serving(Application)`.
- `Server.shutdown()` and `shutdownAndWait()` stop the server, and leave the application open
  (Q10). So a `main()` builds the environment, the application, and the server, starts
  the server, awaits its termination, shuts it down, and then closes the application.
- The doc of `Server` changes accordingly: the example builds an application first.

### 8. Tests

- **The helper.** `server-testlib` gets a public helper that builds an application of one
  context over a new test environment, and returns the context. An overload takes an
  environment, for tests that need their own `Delivery`, storage, or transport. A Kotlin and a
  Java form, for example:

  ```kotlin
  val context = testContext(builder)
  val context = testContext(builder, serverEnvironment { under<Tests> { use(delivery) } })
  ```

  ```java
  var context = TestContexts.build(builder);
  ```

  Closing the context leaves its in-memory environment to the garbage collector. A test that
  needs the application itself builds one with `application { }`.
- **`BlackBox`** builds its application with the helper, and closes it in `close()`.
  `BlackBox.from(BoundedContext)` stays, for a context of an application.
- **`TenantAwareTest.createTenantIndex()`** creates `DefaultTenantStorage` over an in-memory
  storage factory, instead of asking `Application.instance()`.
- **Migration.** About 100 test files replace `builder.build()` with the helper; 31 files that
  use `ServerEnvironment.instance()` and 11 that use `under()` build environments of their
  own; `ServerEnvironmentTest`/`ServerEnvironmentSpec` test the builder;
  `DuplicateContextNameSpec` and `DuplicateEntityStateSpec` test `Application.Builder.build()`;
  `ApplicationSpec` loses the singleton case.
- With an application per test, contexts left open by one test no longer affect other tests.

### 9. Tenants

Only the setting of the index changes in this work (Q8):

- `Application.Builder.setTenantIndex()` replaces `use(TenantIndex)`. Without it,
  `tenantIndex()` creates the default index on its first call, with the storage factory of the
  environment of the application.
- `closeIndex()` is removed: tests that changed the index or the storage factory of the shared
  application build applications of their own.

Effects on PR 2 of #1709 (`reject-unregistered-tenants.md`), for that PR to apply:

- The services get their application from `Server`, not from `Application.instance()`:
  `record()`, `TenantGate`, and the gate in `ServiceDelegate`.
- `UnregisteredTenantPolicy` and `persistEvents()` are properties of `Application.Builder`.
  The `@BeforeAll`/`@AfterAll` restoring of the defaults goes away.
- `MtBlackBox.withTenant()` registers the tenant in the index of its own application.
- The onboarding of an application calls `app.tenantIndex().keep(tenantId)`.
- Two applications with storage factories over one database share the records of the default
  index (`__SYSTEM_TENANTS__`), as two JVMs do today.

## Implementation plan

One PR, against `master` (item 6 of "To confirm"). Bump the version (`bump-version`; breaking),
regenerate the dependency reports with a full build, and pass
`./gradlew clean build dokkaGenerate`. Read the test count after every run.

1. Convert `ServerEnvironment.java` to Kotlin (`java-to-kotlin`, `git mv`), behavior unchanged.
2. `ServerEnvironment` builder and Kotlin DSL; remove the singleton API (section 1).
3. `Application` builder, `contexts()`, `setTenantIndex()`, closing the environment, Kotlin DSL;
   remove `instance()`, `use()`, `closeIndex()` (sections 2 and 9).
4. `BoundedContextBuilder`: package-private `build()` and technology accessors; guards moved to
   `Application.Builder.build()`; `BoundedContext.registry()` removed (sections 3 and 4).
5. Catch-up without the registry (section 5).
6. `ThirdPartyContext` (section 6) and `Server` (section 7).
7. `server-testlib`: the helper, `BlackBox`, `TenantAwareTest` (section 8).
8. Migrate the tests of `server`, `server-testlib`, `client`, and the other modules.
9. Docs: Javadoc/KDoc of the changed types; the note on Q22 in `reject-unregistered-tenants.md`;
   release notes.

Follow-ups in other repositories, once the PR is merged: `examples`, `documentation`,
`delivery`, `chat-bot`, `compiler`, `core-jvm-compiler`, `gcloud-jvm`, and `jdbc-storage` build
environments and applications.

## As implemented

Added while implementing, within the decisions above:

- `BoundedContextBuilder.build()` becomes the package-private `build(ServerEnvironment)`, which
  takes the technologies not set on the builder from the environment. The public `build()` is
  back for the builders made for tests (Q9). For any other builder it throws, so a leftover call
  fails when it runs, not when it compiles.
- `ThirdPartyContext` is converted to Kotlin, so that it gives the application its builder and
  takes the built context through `internal` members, with no public API for it.
- `server-testlib` has `TestApplications.kt`: `testApplication { add(…) }`, and
  `TestApplications.newBuilder()` for Java, whose server environment is a new one of the `Tests`
  type unless the block sets another.
  `BlackBox.from(BoundedContextBuilder)` builds such an application and closes it in `close()`.
  An extension `applicationForTests()` on the context builder was replaced by
  `testApplication {}` on 2026-11-10: a context builder should not produce an application.
  `TestContexts.kt`, with the shortcut `buildForTests(environment)` for one context, was removed
  the same day in favor of `build()` on a builder made for tests (Q9).
- A test closes each application it builds, together with its environment, when it completes.
  `AbstractDeliveryTest` keeps the applications of a test and closes them in `tearDown()`: their
  environments share the storage factory of the test, which closing an environment closes.
  `DefaultEventStoreTest` closes its application, and so its storage factory, after each test.
  `BlackBox.from(Application)` takes an application of one context and closes it with the black
  box. The application that `build()` creates for a test-only context is not reachable, so it
  is not closed; the context is.
- The test fixtures of `server` that storage implementations reuse take the storage factory from
  an overridable `storageFactory()` of `AbstractStorageTest` and `AbstractDeliveryTest`, instead
  of `ServerEnvironment.instance()`. `AbstractDeliveryTest` also has `nodeId()`, `use(Delivery)`,
  `delivery()`, `newEnvironment()`, `build()`, `blackBox()`, and `blackBoxWith()`: each context
  of a delivery test is the only context of an application of its own, and the environments of
  a test share its storage factory and `Delivery`. `InboxContents.get()` becomes
  `InboxContents.of(Delivery)`.
- The contexts of `IntegrationBrokerTest` share one in-memory transport per test.
- `TenantAwareTest.createTenantIndex()` is removed: it needed the default index of the
  singleton, and only its own test called it.
- `ServerEnvironment.nodeId()` and `type()` are Kotlin properties with the same Java names.
- `ServedStateTypes` and its spec are removed: the state types are checked once, in
  `checkUnique()` next to `ContextRegistry`.
- The migration guide is `docs/migration/server-environment-and-application-instances.md`.
- `BoundedContextBuilder.getTracerFactory()` stays public with `@Internal`: `SystemContext`, in
  another package, needs it. The other technology accessors are package-private (Q5).

Added after the pre-PR review:

- `Application.Builder` refuses a `ThirdPartyContext` that another application has built,
  before building anything. Otherwise the contexts of the second application would stay open,
  with no application to close them.
- `Application.contexts()` gives an immutable list, so Java callers cannot change it.
- The `internal` settings of `ServerEnvironment.Builder` end with `Setting`
  (`deliverySetting`, …, `schedulerFactorySetting`), so that inside `serverEnvironment {}` they do
  not hide the properties of the enclosing code.
- The `@ServerDsl` marker on `Application.Builder`, `ServerEnvironment.Builder`, and
  `TypeConfigurator` keeps a nested DSL block from calling the outer builder implicitly.

Added after the review of the PR (2026-11-10):

- The link to the domain context lives in `SystemContext`, not in `BoundedContext`. The base
  class had a `domain` field that only a System context ever set, with `isSystem()` guarding
  both ends of it. Now `BoundedContext.domainContext()` is `abstract`, so each kind of context
  answers for itself: `DomainContext` returns `this`, and `SystemContext` keeps the field and
  returns it. `BoundedContext.InternalAccess.domainContext()` only delegates.
  `attachDomain()` is `public` on `SystemContext`, because `BoundedContextBuilder` calls it
  from another package and is not a subclass.
  - This retires `checkState(isSystem(), …)`, the `((BoundedContext) system)` upcast in
    `buildDomain()`, and the test `refusing a domain context for a domain context`: attaching
    a domain context to a domain context no longer compiles.
  - `BoundedContext.isSystem()` is removed with its two overrides. Both of its call sites were
    the guards of the moved field, so it kept no caller at all, and `checkInheritance()` admits
    only `DomainContext` and `SystemContext`, so no subclass outside the framework could have
    implemented it. The anonymous subclass of the `prohibit 3rd-party descendants` test loses
    the override too.
  - `DefaultSystemClient.target()` and `SystemBoundedContexts.systemOf()` return
    `SystemContext` instead of `BoundedContext`: the field they expose is a `SystemContext`,
    and the test of `attachDomain()` needs that type.
- Considered and rejected: moving `SystemContext` to `io.spine.server`, so that `attachDomain()`
  could be package-private and `BoundedContext` could become a Kotlin `sealed class` in place of
  `checkInheritance()`. A sealed class in Kotlin 2.4 admits subclasses from its own package
  only, so the move is the only way to it — but `SystemContext` builds `SystemConfig`,
  `SystemEnricher`, `CommandLogRepository`, `ScheduledCommandRepository`, and
  `DefaultSystemClient`, all package-private, and `DefaultSystemWriteSide` reads its
  package-private `config()`. The move would widen six members to retire two, and taking the
  repositories along would widen the System entities, which `@BoundedContext("_System")` pins
  to that package.
- Also considered: converting `SystemContext` to Kotlin, to declare `attachDomain()` as
  `internal` rather than `public`. It works — `internal` is module-wide, and `@JvmName` keeps
  the name unmangled for the Java caller, as `Transaction.kt` does — but it buys one annotation,
  so it waits for a reason of its own. Converting `BoundedContext` and `BoundedContextBuilder`
  (1,868 lines, 16 accessors needing `@get:JvmName`, 17 wildcard-generic sites, ~190 files here
  and ~350 downstream) is a task of its own, justified by a `boundedContext { }` DSL rather
  than by this change.

## Questions and the options considered

1. ~~Where do the guards of names and state types live once `Application` is an instance?~~
   Decided: in `Application`. Options: in `ServerEnvironment`, with the shared technologies;
   in `Application` (chosen); in a JVM-wide internal object.
2. ~~Remove the singletons, or keep them deprecated as the default?~~ Decided: remove them.
   Keeping them would have kept `BoundedContextBuilder.build()` public, with two ways to build
   a context.
3. ~~Can a context join an application after it is built?~~ Decided: no; `ThirdPartyContext`
   is added to the builder like any other context. Options considered: third-party contexts
   join internally; a public `Application.add()`; a third-party context builds an application
   of its own.
4. ~~Does `Application.close()` close its `ServerEnvironment`?~~ Decided: yes, the application
   owns it. The other option: the creator of the environment closes it.
5. ~~Do the technology setters of `BoundedContextBuilder` stay public?~~ Decided: no; the
   application sets the technologies, and a context keeps no reference to the application.
6. ~~How do tests build a context?~~ Decided: through `Application`, with a helper in
   `server-testlib`. The other option: keep `BoundedContextBuilder.build()` public, marked
   `@VisibleForTesting`.
7. ~~What does `Server.shutdown()` close?~~ Decided: the whole application (Q7), revised to
   nothing but the server itself (Q10). Options: stop only the transport; close the contexts
   and leave the application open (Q22 of `reject-unregistered-tenants.md`).
8. ~~Set the tenant index on the builder, or keep `Application.use(TenantIndex)`?~~ Decided:
   on the builder.
