# `ServerEnvironment` and `Application` as instances

This guide tells you what changes when you upgrade to `2.0.0-SNAPSHOT.690`. `ServerEnvironment`
and `Application` are no longer singletons: an application builds its server environment, and
then its application, which builds its Bounded Contexts. Every server-side application needs
code changes, and so do the tests that build contexts or configure the server environment.
`2.0.0-SNAPSHOT.700` also renames `multitenant` to `multiTenant` across the API, as section 7
describes.

## What changed, in one paragraph

Before, the technologies of the server (storage, transport, delivery, tracing) were configured
in the JVM-wide `ServerEnvironment.instance()`, and each context registered itself in the
JVM-wide `Application.instance()` when it was built. Now a `ServerEnvironment` is built with
a builder, and given to the builder of an `Application`, together with the builders of the
Bounded Contexts. The application builds the contexts with the technologies of its environment,
and owns them and the environment: closing the application closes them all. A `Server` serves
the contexts of the application it is given, and leaves the application open when it shuts down,
since several servers may serve one application.

## At a glance

| Area | Before | After |
|------|--------|-------|
| `ServerEnvironment.instance()`, `under()`, `when()`, `reset()` | public | **removed**; use `ServerEnvironment.newBuilder()` |
| `under<E> {}` (Kotlin, top level) | configures the singleton | **removed**; use `serverEnvironment { under<E> {} }` |
| `ServerEnvironment.scheduleCommandsUsing()` | on the instance | on `ServerEnvironment.Builder` |
| `ServerEnvironment.nodeId()`, `type()` | methods | Kotlin properties; Java calls stay the same |
| `Application.instance()`, `use(TenantIndex)`, `defaultMultitenantIndex()` | public | **removed**; use `Application.newBuilder()` and `setTenantIndex()` |
| `Application.contexts()` | internal | public: the contexts the application built |
| `BoundedContextBuilder.build()` | public | throws `IllegalStateException` for a builder of `BoundedContext.singleTenant()` or `multiTenant()`; add the builder to an `Application` |
| `BoundedContextBuilder.setStorageFactory()` and the other technologies | public | **package-private**; the application sets them |
| `Server.Builder` | serves the contexts of `Application.instance()` | `setApplication()` is required |
| `Server.shutdown()`, `shutdownAndWait()` | close the contexts | stop the server only; close the application after its servers |
| `ThirdPartyContext.singleTenant()`, `multiTenant()` | build the context | the context is built by the application it is added to |
| `BoundedContext.multitenant()`, `ContextSpec.multitenant()`, `ThirdPartyContext.multitenant()` | public | **renamed** to `multiTenant()` |
| `isMultitenant()` of `BoundedContext`, `BoundedContextBuilder`, `ContextSpec`, `CommandBus`, and `Stand` | public | **renamed** to `isMultiTenant()`; Kotlin reads `isMultiTenant` |
| `CommandBus.Builder.setMultitenant()`, `Stand.Builder.setMultitenant()` | public | **renamed** to `setMultiTenant()` |
| `Storage.isMultitenant()` | interface method | **renamed** to `isMultiTenant()`; affects every storage implementation |
| `MemoizingWriteSide.multitenant()`, `MultitenantCommandBusTestEnv` | test fixtures of `server` | **renamed** to `multiTenant()`, `MultiTenantCommandBusTestEnv` |
| `TenantAwareTest.createTenantIndex()` | public | **removed** |

## 1. Build the server environment

Configure the technologies for each type of environment on the builder. The values you do not
set keep their defaults: in-memory storage and transport under `Tests`, and a local `Delivery`.

```java
var environment = ServerEnvironment.newBuilder()
        .under(Production.class, env -> {
            env.use(productionStorageFactory);
            env.use(memoizingTracerFactory);
        })
        .build();
```

```kotlin
val environment = serverEnvironment {
    under<Production> {
        use(productionStorageFactory)
        use(memoizingTracerFactory)
    }
}
```

A built environment cannot be changed. It still resolves the current type of environment each
time a technology is requested.

## 2. Build the application with its contexts

Add the builders of the contexts to the builder of the application, instead of calling
`BoundedContextBuilder.build()`. An application needs a server environment and at least one
context. Before building any context, the builder checks that no two contexts have one name,
and that no two contexts serve one entity state type.

```java
var application = Application.newBuilder()
        .setServerEnvironment(environment)
        .add(BoundedContext.singleTenant("Users").add(new UserRepository()))
        .add(BoundedContext.singleTenant("Tasks").add(new TaskRepository()))
        .build();
```

In Kotlin, `singleTenantContext(name) {}` and `multiTenantContext(name) {}` configure a context
builder in a block and return it, so a context with several repositories does not need a chain
of calls. `BoundedContext.singleTenant(name)` and `multiTenant(name)` stay, and Java uses them.

```kotlin
val application = application {
    serverEnvironment = environment
    add(singleTenantContext("Users") {
        add(UserRepository())
    })
    add(singleTenantContext("Tasks") {
        add(TaskRepository())
        add(TaskViewRepository())
    })
}
```

`application.contexts()` gives the built contexts. An environment belongs to one application:
building a second application with it throws `IllegalStateException`. Contexts that need other
technologies form another application with an environment of its own.

## 3. Serve the application

```java
var server = Server.atPort(8484)
                   .setApplication(application)
                   .build();
server.start();
```

`Server.shutdown()` leaves the application open, since several servers may serve one
application. Shut down the servers first, and then close the application, which closes its
contexts, its tenant index, and its server environment:

```java
server.shutdown();
application.close();
```

## 4. Add third-party contexts to the application

`ThirdPartyContext.singleTenant(name)` and `multiTenant(name)` no longer build a context.
Add the third-party context to the application, which builds it. It can emit events once
the application is built.

```kotlin
val calendar = ThirdPartyContext.singleTenant("Calendar")
val application = application {
    serverEnvironment = environment
    add(BoundedContext.singleTenant("Scheduling").add(MeetingRepository()))
    add(calendar)
}
calendar.emittedEvent(event, actor)
```

## 5. Set the tenant index on the builder

Replace `Application.instance().use(index)` with `Application.Builder.setTenantIndex(index)`.
Without it, the application creates the default index with the storage factory of its
environment. An application registers a tenant with `application.tenantIndex().keep(tenantId)`.

## 6. Tests

- Build an application for a test with `testApplication { add(…) }` from `server-testlib`
  (`TestApplications.newBuilder()` in Java). Its server environment is a new one of the `Tests`
  type, unless the block sets another. The test closes the application when it completes:

  ```kotlin
  val environment = serverEnvironment { under<Tests> { use(delivery) } }
  val application = testApplication {
      serverEnvironment = environment
      add(BoundedContext.singleTenant("Tasks").add(TaskRepository()))
  }
  application.use {
      val context = it.contexts().single()
      // …
  }
  ```
- `BlackBox.from(BoundedContextBuilder)` builds such an application, and closing the black box
  closes it. `BlackBox.from(Application)` takes an application of one context, and closing
  the black box closes it too. `BlackBox.from(BoundedContext)` closes only the context.
- Tests no longer reset the server environment: each test builds its own.
- Contexts that receive the external events of each other must share a transport: put them into
  one application, or build their environments with one `TransportFactory`.
- Storage implementations that run the test suites of `server` override `storageFactory()` of
  `AbstractStorageTest` and `AbstractDeliveryTest`, instead of configuring
  `ServerEnvironment.under(Tests.class)`. `AbstractDeliveryTest.use(Delivery)` sets the delivery
  of the contexts that a delivery test builds afterwards.

## 7. Spell `multiTenant` as two words

`2.0.0-SNAPSHOT.700` spells the word the way the Kotlin DSL `multiTenantContext()` does:
`multitenant()` becomes `multiTenant()`, `isMultitenant()` becomes `isMultiTenant()`, and
`setMultitenant()` becomes `setMultiTenant()`. The old names are gone, with no deprecated
aliases, so the compiler points at each call to update. In Kotlin, `context.isMultitenant`
becomes `context.isMultiTenant`.

`Storage.isMultiTenant()` is a renamed interface method, so the rename reaches every storage
implementation, not only the code that calls it. An implementation that overrides
`isMultitenant()` must rename its override. The compiler reports a Kotlin `override` and a Java
method marked `@Override`. A Java method without `@Override` in a subclass of `AbstractStorage`
still compiles, but nothing calls it any more.
