# Requests of unregistered tenants, and the log of an application

This guide tells you what changes when you upgrade to `2.0.0-SNAPSHOT.710`. The gRPC services of
an application now refuse a request whose tenant is not registered in the application, and
report it to the new `ApplicationLog`. Multi-tenant applications must register their tenants
before the tenants send requests. Code that creates the services from contexts, and tests that
wrap a built context in a `BlackBox`, need changes too.

## What changed, in one paragraph

Before, a multi-tenant Bounded Context added a tenant the first time a command came with a
`TenantId` it had not seen, and served a query or a subscription of any tenant. Now
`CommandService`, `QueryService`, and `SubscriptionService` serve a request to a multi-tenant
context only if its tenant is in the tenant index of the application. Otherwise, the client
gets `ErrorCase.TENANT_ERROR`. Queries and subscriptions are also refused when they have no
tenant in a multi-tenant context, or a tenant in a single-tenant one, as commands already were.
The services are created from an `Application`, since the check needs its tenant index.
A request refused before it reaches a context is written to the log of the application.

## At a glance

| Area | Before | After |
|------|--------|-------|
| A request of an unregistered tenant to a multi-tenant context | served; a command registers its tenant | refused with `TENANT_ERROR` |
| A query or a subscription without a tenant to a multi-tenant context | served from a shared partition | refused with `TENANT_ERROR` |
| A query or a subscription with a tenant to a single-tenant context | served | refused with `TENANT_ERROR` |
| `CommandService`, `QueryService`, `SubscriptionService` | `newBuilder().add(context).build()`, `withSingle(context)` | **removed**; use `of(application)` |
| `AbstractServiceBuilder` | public | **removed** |
| Two contexts that handle commands of one type | `CommandService.Builder.build()` throws `IllegalStateException` | `Application.Builder.build()` throws `DuplicateCommandHandlerException` |
| `Application.log()`, `Application.Builder.setApplicationLog()` | — | **new** |
| `io.spine.server.app.ApplicationLog` | — | **new** |
| `BlackBox.from(BoundedContext)` | public | **removed**; use `from(Application)` or `from(BoundedContextBuilder)` |
| `MtBlackBox.withTenant()` | sets the tenant of the requests | also registers the tenant in the application |

## 1. Register the tenants

Register a tenant when your onboarding of the tenant is done, before the tenant sends requests:

```java
application.tenantIndex()
           .keep(tenantId);
```

There is no switch that registers the tenants of incoming requests. Requests posted from within
the server, such as the commands of a process manager, are not checked.

A client of an unregistered tenant gets `TENANT_ERROR`. `CommandRequest.post()` reports it to
the server error handler of the request, and returns an empty set without posting the command,
since the server refuses to subscribe to the events of the command.

## 2. Create the services from the application

`Server` creates the services from the application given to `Server.Builder.setApplication()`,
so a server needs no changes. Code that creates the services itself passes the application:

```java
var commandService = CommandService.of(application);
var queryService = QueryService.of(application);
var subscriptionService = SubscriptionService.of(application);
```

The services serve all the Bounded Contexts of the application. An application whose two
contexts handle commands of one type is not built: `Application.Builder.build()` throws
`DuplicateCommandHandlerException`, before it builds any context. So the check works for an
application that no `Server` exposes, too.

## 3. The log of the application

The services write to the log of the application when they refuse a request before it reaches
a Bounded Context: a request of an unregistered tenant, a request that no context serves, or
a request whose tenant cannot be checked because the tenant index fails. Each record describes
the problem with an event of the `spine.server.app` package: `RequestOfUnregisteredTenantRejected`,
`UnsupportedRequestRejected`, or `TenantCheckFailed`. The event holds the refused request, and
the reason of the refusal in its `reason` field, written for a person or an agent reading
the log.

By default, the records are written with Spine Logging. Each message starts with `[Application]`,
and an event is written as the name of its type, followed by its compact JSON form, for
example: `spine.server.app.UnsupportedRequestRejected {"request":{…},"reason":"…"}`.
A request of a type unknown to the application cannot be written as JSON, so it is written as
text, which names the type and the ID of the request.
The logger belongs to the `io.spine.server.app` package, so a logging backend may route the
records apart from the others by the name of the package. Each record holds the refused request,
so the log holds the data that the callers send.

To write the records elsewhere, implement `ApplicationLog`, and set it when building
the application:

```kotlin
val application = application {
    serverEnvironment = environment
    applicationLog = MyApplicationLog()
    add(contextBuilder)
}
```

`ApplicationLog` passes functions rather than values, so that a record of a level that is not
enabled costs nothing. The application may write its own records with `application.log()`.

## 4. Tests

- `MtBlackBox.withTenant()` registers the tenant in the application of the black box, so
  `BlackBox.clients()` keep working. `BlackBoxClients.create(tenantId)` does not register the
  tenant: use it to check that a request of an unregistered tenant is refused.
- A test that sends requests through the services with generated tenants registers them first,
  with `application.tenantIndex().keep(tenantId)`.
- `BlackBox.from(BoundedContext)` is removed: a black box needs the application of its context.
  Build the black box from the builder of the context, or from an application of one context.
  Closing the black box closes the application.
