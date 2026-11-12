# Reject requests from unregistered tenants

## Status

**In progress.** The design was settled on 2026-11-04 (questions 1–10), revised against
`ErrorCase` on 2026-11-09 (questions 11–24), and redesigned on 2026-11-12 (questions 25–34),
after `Application` and `ServerEnvironment` became instances (#1720). The work is tracked
by #1709.

Progress:

- [x] PR 1, the tenant index of the application: #1717, merged on 2026-11-10.
- [ ] PR 2, rejecting unregistered tenants (Q25, Q26): the gate, the checks of the tenant of
  queries and subscriptions, and the `ApplicationLog` that reports the requests refused before
  they reach a Bounded Context. Implemented on 2026-11-12 in branch
  `claude/reject-unregistered-tenants-7939c6` (`2.0.0-SNAPSHOT.710`); see "As implemented".

Since the redesign:

- there is no `UnregisteredTenantPolicy`: a request of an unregistered tenant is always
  refused (Q25);
- `ApplicationLog` replaces the event store of `Application` (Q27);
- the gRPC services are created from an `Application` (Q29);
- `TenantAdded` and the removal of `CommandValidationError` are left out of PR 2 (Q26).

Sections 2–7, 10, and 12 describe the design of 2026-11-12. Sections 8 and 11 describe PR 1 as
merged; `application-instances.md` describes how #1720 changed them since. Section 9 waits for
a later PR.

To confirm (assumed while planning on 2026-11-12, not asked):

1. The levels: `WARNING` for the requests refused because of their tenant or their type,
   `ERROR` for a failure of the index (section 10).
2. The services no longer log on their own the refusals that they report to `ApplicationLog`.
3. A topic that no context serves is subscribed in every context; only the membership of its
   tenant is checked (section 3).
4. `Application.close()` does not close its `ApplicationLog`.

Decisions:

- Q1 (2026-11-04): for commands, an unregistered tenant is reported in an error `Ack`
  (option B), like other command validation errors. Since Q11, the `Ack` carries
  `ErrorCase.TENANT_ERROR`. See section 5.
- Q2 (2026-11-04): the tenant is checked only at the service boundary, not in the buses.
  See section 3. A bus-level check may come later, together with tenant removal.
- Q3 (2026-11-04): `REJECT` is the default in all environments, `Tests` included.
  `MtBlackBox.withTenant()` registers its tenant; `BlackBoxClients.create(tenant)` does not.
  See sections 2 and 7. Since Q25 there is no policy: such requests are always refused.
- Q4 (2026-11-04, superseded by Q11): tenant errors use a Protobuf enum nested in a message,
  `spine.core.TenantError.Reason` (option B2).
- Q5 (2026-11-04): one tenant index for the whole application, in this change. It is held
  by a new `Application` singleton, not by `ServerEnvironment`: `ServerEnvironment` is about
  the technologies that implement the server side, not about domain things like tenants.
  See section 8. Following the same principle, `UnregisteredTenantPolicy` is set on
  `Application` too. Superseded by Q25: there is no policy.
- Q6 (2026-11-04, superseded by Q11): a tenant that is not in the index is reported as
  `TenantError.Reason.TENANT_NOT_REGISTERED`, and `CommandValidationError` gets the alias
  `TENANT_NOT_SPECIFIED` for the ambiguous `TENANT_UNKNOWN`.
- Q7 (2026-11-04): user-facing text is the clients' job. Since Q11, the client gets only
  `ErrorCase.TENANT_ERROR` and builds its own text from it.
- Q8 (2026-11-04, superseded by Q11): spine-ts keeps failing the RPC with `NOT_FOUND` for a
  command of an unregistered tenant. The decision was made for the old wire contract;
  spine-ts revisits it when it adopts `ErrorCase`.
- Q9 (2026-11-04): `TenantAdded` stays, and is posted when a tenant is added, so that the
  Bounded Contexts can prepare their internals for the new tenant's requests. See section 9.
  Left out of PR 2 by Q26.
- Q10 (2026-11-04): `TenantAdded` is a notification. The contexts may finish preparing after
  the tenant's first request; handlers are idempotent. A synchronous preparation step can be
  added later, when a storage needs per-tenant setup. See section 9.
- Q11 (2026-11-09): no `TenantError` type and no `TENANT_NOT_SPECIFIED` alias. The client gets
  only `ErrorCase.TENANT_ERROR`. The server records the details as System events named after
  the kind of request and the check. `CommandValidationError` is removed: no code uses it
  since #1713, which removed the read-side enums for the same reason. See sections 4 and 6.
  The removal is left out of PR 2 by Q26: the enum still reports `DUPLICATE_COMMAND`.
- Q12 (2026-11-09): a request refused because its tenant is not registered, and a request that
  no Bounded Context serves, are recorded by `Application`, as part of #1709. The serving
  context does not record them: its System context would store data under a tenant that
  does not exist, and on Datastore the first write creates the tenant's namespace.
  See section 10. Since Q27, `Application` reports them to its `ApplicationLog`.
- Q13 (2026-11-09): the record of `Application` is a single-tenant `EventStore` of its own.
  The events are appended to it directly: no bus, no context, no subscribers. See section 10.
  Superseded by Q27.
- Q14 (2026-11-09): `Application` does not store the record unless told to, like
  `SystemSettings.persistEvents()`. Until then, a refusal is only logged, and the store is not
  created. So a flood of requests with made-up tenants does not grow the storage of an
  application that has not opted in. See section 10. Superseded by Q27.
- Q15 (2026-11-09, while reviewing PR 1): no index per context. `BoundedContextBuilder` loses
  `setTenantIndex()`, `hasTenantIndex()`, and `getTenantIndex()`, so every multitenant context
  and its System context use the index of `Application`. A per-context index served no need of
  this design, and made every PR more complex: closing rules in PR 1, and in PR 2 a second
  announcing wrapper and tenants that one context admits and another rejects. gcloud-jvm
  sets the index of `Application` instead. See section 8.
- Q16 (2026-11-09, while reviewing PR 1): `OpenContexts` is merged into `Application`, in PR 1,
  so that the application knows the contexts it is made of. It stays an internal class of its
  own, which `Application` holds, rather than a JVM-wide object: detekt limits a class to 11
  functions. `Application.close()` closes the open contexts, in the reverse order of their
  building, and then the tenant index; after it, no context can be built. See section 8.
- Q17 (2026-11-09, while reviewing PR 1): `Application` has no `reset()`. An application is
  closed, not reset. Tests close only the tenant index with the internal `closeIndex()`, and
  check closing on applications of their own (the constructor is internal). See section 8.
- Q18 (2026-11-09, while reviewing PR 1): a Bounded Context keeps no tenant index. The code
  that needs the index asks `Application.instance().tenantIndex()`, so it always gets the
  current one. `BoundedContext` loses its index and `InternalAccess.tenantIndex()`, and
  `BusBuilder` its tenant-index API. See section 8.
- Q19 (2026-11-09, while reviewing PR 1): `CommandBus` stops registering the tenants of the
  commands in PR 1, not PR 2. Nothing reads the index before the gate of PR 2, so no request
  is served differently, and no test relied on it. Until PR 2, only the application
  registers tenants. See section 3.
- Q20 (2026-11-09, while reviewing PR 1): `OpenContexts` is renamed to `ContextRegistry`, and
  `Application` gives it with `contexts()`. The record holds only the contexts that are open,
  so "open" adds nothing, and ties the type to one property. Its methods `opened()`,
  `closed()`, and `findContextServing()` become `register()`, `unregister()`, and
  `contextServing()`. The name `Contexts` is taken by the file facade of `Contexts.kt`.
- Q21 (2026-11-09, while reviewing PR 1): `Server` serves the contexts of `Application`, and
  `Server.Builder.add()` is removed, not deprecated. There is no `Application.add()`: building
  a context registers it in `Application` already (Q16). See section 11.
- Q22 (2026-11-09): `Server.shutdown()` closes the contexts the server serves, as before.
  The application stays open, so a later server in the same JVM can serve new contexts.
  Superseded by Q7 of `application-instances.md` (2026-11-10): `Server.shutdown()` closes the
  whole application, which is no longer a singleton. Q10 there (2026-11-11) supersedes Q7:
  `Server.shutdown()` stops the server only, and the application is closed after its servers.
  See section 11.
- Q23 (2026-11-09): this change goes into PR 1, not a PR of its own.
- Q24 (2026-11-10): the former PRs 2–4 become one PR, PR 2: the record of `Application`,
  `TenantAdded`, and rejecting unregistered tenants. See "Implementation plan".
  Superseded by Q26.

- Q25 (2026-11-12): no `UnregisteredTenantPolicy`. A request whose tenant is not in the index
  of the application is refused, always. See section 2.
- Q26 (2026-11-12): one PR for the rest of #1709: the membership check, the checks of the
  tenant of queries and subscriptions, and reporting the requests that no Bounded Context
  serves. `TenantAdded` (section 9) and the removal of `CommandValidationError` (section 6) are
  left out: the enum still reports `DUPLICATE_COMMAND` (`DoubleDispatchGuard`,
  `EntityLifecycle`).
- Q27 (2026-11-12): `ApplicationLog` reports the problems that concern no Bounded Context,
  instead of an event store of `Application`. An interface with `log(level, message)`,
  `logEvent(level, event)`, and `logEvent(level, message, event)`. The arguments other than
  the level are functions, called only if the level is enabled. See section 10.
- Q28 (2026-11-12): `DefaultLog`, the internal implementation used unless the builder of
  `Application` gets another one, writes with Spine Logging. `@LoggingDomain("Application")`
  on the class prefixes its records with `[Application]`. An event is printed as compact JSON
  of the event packed into `Any`, so that the record names its type. The callers choose
  the levels. Revised by Q38: the event is not packed.
- Q29 (2026-11-12): the gRPC services are created from an `Application`:
  `CommandService.of(application)`, `QueryService.of(application)`, and
  `SubscriptionService.of(application)`. The builders, `AbstractServiceBuilder`, and
  `withSingle()` are removed. See section 12.
- Q30 (2026-11-12): `BlackBox.from(BoundedContext)` is removed. A black box needs the
  application of its context for its clients and for `MtBlackBox.withTenant()`.
- Q31 (2026-11-12): `Application.log()` is public, so that an application can write to the
  same log. The builder takes the log with `setApplicationLog()`, and the `application {}`
  DSL with the `applicationLog` property.
- Q32 (2026-11-12): three events of `spine.server.app` describe the problems, one per problem:
  `RequestOfUnregisteredTenantRejected`, `UnsupportedRequestRejected`, and `TenantCheckFailed`.
  See section 10.
- Q33 (2026-11-12): the checks of the tenant of queries and subscriptions record six System
  events in the serving context, one per kind of request and check, as `TenantFilter` does for
  commands. See section 4.
- Q34 (2026-11-12): `Application` and the code written for it so far stay in `io.spine.server`.
  The new parts (the log, the gate, the events) go to `io.spine.server.app`, and the Protobuf
  types to the `spine.server.app` package.
- Q35 (2026-11-12, while reviewing PR 2): the check that no two contexts handle commands of
  one type moves from `CommandService.of()` to `Application.Builder.build()`, which runs it
  before building any context, with the checks of names and entity state types. An application
  needs it even when no `Server` exposes it. `BoundedContextBuilder.commandTypes()` tells the
  command types of the repositories and command dispatchers of a builder; the failure is
  `DuplicateCommandHandlerException`. Options considered: check right after building, with
  `BoundedContext.commandTypes()`, closing the built contexts on a failure.
- Q36 (2026-11-12, while reviewing PR 2): `RefusedRequest` holds the refused request itself,
  not its ID, type URL, and actor context, so that a record of the log is complete for
  diagnostics. See section 10.
- Q37 (2026-11-12, while reviewing PR 2): each event of `ApplicationLog` carries a required
  `reason`, readable by a person or an agent: the refusal has a reason, the request does not.
  The services write these records with `logEvent(level, event)`, with no separate message.
  See section 10.
- Q38 (2026-11-12, while reviewing PR 2): `DefaultLog` prints an event as its own compact JSON,
  with `@type` added as the first field, and does not pack it into `Any`. It only prints:
  the services compose events that JSON can hold. See section 10. Revised by Q40: the type
  goes before the JSON.
- Q39 (2026-11-12, while reviewing PR 2): a request of a type unknown to the server is written
  to `ApplicationLog` as text, naming the type and the ID of the request, like a request whose
  type cannot be read. JSON cannot hold such a request, and the events are composed so that
  `DefaultLog` needs no conversion. `UnsupportedRequestRejected` covers the types that are
  internal or served by no context. See section 10.
- Q40 (2026-11-12, while reviewing PR 2): `DefaultLog` writes an event as the name of its type,
  followed by its own compact JSON form. Adding `@type` to the JSON meant splicing a field into
  a string that the JSON printer had produced, with a special case for an empty object.
  See section 10.

## Problem

A multitenant Bounded Context adds a tenant the first time a command arrives with a
`TenantId` it has not seen. Real applications cannot add a tenant this way: adding one
involves payment details, storage provisioning, and other onboarding steps that the
application performs on its own. A request from an unregistered tenant must be rejected.

## Findings

### The user report is confirmed

The path of a command from a gRPC client:

1. `CommandService.post` → `ServiceDelegate.serve`
   ([ServiceDelegate.java](../../server/src/main/java/io/spine/server/ServiceDelegate.java))
   finds the context by the command type.
2. `CommandBus.post` → `Bus.filterAndPost`
   ([Bus.java](../../server/src/main/java/io/spine/server/bus/Bus.java)) runs the filters.
   `TenantFilter`
   ([TenantFilter.kt](../../server/src/main/kotlin/io/spine/server/commandbus/TenantFilter.kt))
   checks only that a tenant is *present* in a multitenant context and *absent* in a
   single-tenant one. Since #1711, it records `CommandTenantNotSpecified` or
   `CommandTenantNotExpected` and answers `TENANT_ERROR`.
3. Every command that passes the filters goes to `CommandBus.store`
   ([CommandBus.java:233](../../server/src/main/java/io/spine/server/commandbus/CommandBus.java)),
   which calls `tenantConsumer`, wired as `tenantIndex::keep`
   ([CommandBus.java:319](../../server/src/main/java/io/spine/server/commandbus/CommandBus.java)).
4. `TenantStorage.keep`
   ([TenantStorage.java:68](../../server/src/main/java/io/spine/server/tenant/TenantStorage.java))
   reads the record and, if there is none, writes a new `Tenant { id, when_created }`.

So any well-formed command creates a tenant. This applies to commands from gRPC clients
and to commands posted in-process (by process managers, commanders, `BlackBox`).

Further observations:

- **Queries and subscriptions never consult the index.** A query for an unknown tenant
  is served from an empty partition. A query *without* a tenant in a multitenant context
  is not rejected either: `TenantAware(TenantId)`
  ([TenantAware.java:62](../../server/src/main/java/io/spine/server/tenant/TenantAware.java))
  silently substitutes the `SINGLE_TENANT` constant.
- **Nothing in production reads `TenantIndex.all()`.** The list is written, never used.
- **No test asserts that a command registers its tenant.** The behavior is implicit.
- **`TenantAdded`** ([events.proto:43](../../server/src/main/proto/spine/server/tenant/events.proto))
  is declared but never emitted.
- **The scope of the index depends on the storage.** Each context gets its own
  `DefaultTenantStorage` over the `__SYSTEM_TENANTS__` spec. In memory, every record storage
  is a separate map, so each context has its own tenant list. On JDBC the physical table
  is derived from the record type, not the context (see `event-store-context-prefix.md`),
  so all contexts share one list.
- **The name `TENANT_UNKNOWN` is already taken, with another meaning.**
  `CommandValidationError.TENANT_UNKNOWN`
  ([command.proto:156](../../core/src/main/proto/spine/core/command.proto)) means
  "the command has no `tenant_id`". spine-ts reports that case as `TENANT_REQUIRED`.
  A new `TENANT_UNKNOWN` value cannot be added anywhere in the `spine.core` package:
  Protobuf scopes enum values at the package level, so the names would clash.
  Moot since Q11: `CommandValidationError` is removed.
- **Error payloads were not platform-neutral.** Resolved by #1713: the services answer with
  `ErrorCase` only, and the details are recorded as System events.
- **A multitenant context has two indexes.** `BoundedContextBuilder.buildSystem()` passes the
  index to the System context only if it was given with `setTenantIndex()`. Otherwise the
  System builder runs `initTenantIndex()` too and creates a second `DefaultTenantStorage`.
  An index given with `setTenantIndex()` is shared by both contexts, and both close it in
  `BoundedContext.shutDownRepositories()`.
- **Datastore's `NamespaceIndex.keep` only fills an in-memory cache**
  (`gcloud-jvm`, `NamespaceIndex.java:89`). A namespace becomes durable when data is first
  written to it. A tenant registered on one node is invisible to other nodes until then.

### Places that deal with the list of known tenants

| Place | Role |
|---|---|
| [`TenantIndex`](../../server/src/main/java/io/spine/server/tenant/TenantIndex.java) | The SPI: `keep`, `all`, `close`; factory `singleTenant()`; the default multitenant index comes from `Application.defaultMultitenantIndex()`. |
| [`TenantStorage`](../../server/src/main/java/io/spine/server/tenant/TenantStorage.java) | Abstract storage-backed index with an in-memory cache of known IDs. |
| [`DefaultTenantStorage`](../../server/src/main/java/io/spine/server/tenant/DefaultTenantStorage.java) | Default index; stores `Tenant` records under `__SYSTEM_TENANTS__`. |
| [`SingleTenantIndex`](../../server/src/main/java/io/spine/server/tenant/SingleTenantIndex.java) | Null-object for single-tenant contexts; `keep` does nothing. |
| [`tenant.proto`](../../server/src/main/proto/spine/server/tenant/tenant.proto) | `Tenant` record (internal type). |
| [`events.proto`](../../server/src/main/proto/spine/server/tenant/events.proto) | `TenantAdded` — never emitted. |
| [`BoundedContextBuilder`](../../server/src/main/java/io/spine/server/BoundedContextBuilder.java) | `setTenantIndex` (:253, removed by Q15), `initTenantIndex` (:708) creates the default per context; passes the index to the System context (:679) and to `CommandBus` (:719). |
| [`BoundedContext`](../../server/src/main/java/io/spine/server/BoundedContext.java) | Holds the index (:117), closes it (:586), exposes it via `InternalAccess.tenantIndex()` (:811). |
| [`BusBuilder`](../../server/src/main/java/io/spine/server/bus/BusBuilder.java) | `injectTenantIndex` (:184), `ensureTenantIndex` (:193). |
| [`CommandBus`](../../server/src/main/java/io/spine/server/commandbus/CommandBus.java) | **The only writer:** `store` → `tenantIndex::keep` (:233, :319). |
| [`TenantFilter`](../../server/src/main/kotlin/io/spine/server/commandbus/TenantFilter.kt) | Tenant *presence* checks of commands; records `CommandTenantNotSpecified` and `CommandTenantNotExpected`. |
| [`TenantAware`](../../server/src/main/java/io/spine/server/tenant/TenantAware.java) | Maps a default `TenantId` to `SINGLE_TENANT`. |
| [`TenantAwareTest`](../../server-testlib/src/main/java/io/spine/testing/server/tenant/TenantAwareTest.java), [`MtBlackBox`](../../server-testlib/src/main/java/io/spine/testing/server/blackbox/MtBlackBox.java) | Test library: `createTenantIndex`, `withTenant`. |
| `gcloud-jvm`: `NamespaceIndex`, `DatastoreTenants` | Datastore implementation of `TenantIndex`. |

## Reference: spine-ts PR #20 (merged 2026-10-30)

- `TenantIndex` has `contains`, `all`, `page`, and `keep`. `contains` "checks current
  membership without registration or storage preparation".
- The membership check runs after shape validation and before database resolution or
  handler execution.
- An unknown tenant fails the RPC with `NOT_FOUND`. The trailer `spine-error-bin` and the
  Connect details carry `spine.base.Error { type: "TENANT_UNKNOWN", code: 5, message }`.
  Note: `code` is the gRPC code number, not an enum value.
- For **commands too**, an unknown tenant fails the RPC. In contrast, `TENANT_REQUIRED` and
  `TENANT_INAPPLICABLE` for commands are returned in `Ack.status.error`.
- The membership check runs **inside the buses**: `commandBus().post()` and the read path
  throw `TenantUnknownError`, and the services map it to `NOT_FOUND`. So in-process posts
  are checked too.
- An unavailable tenant directory is an operational failure, not proof that the tenant
  is missing.
- The index is configured once in `ServerEnvironment`; a context may override it.

## Proposed design

### 1. `TenantIndex.contains()`

Add a membership query that has no side effects. A default method keeps existing
implementations (`NamespaceIndex`, user code) source- and binary-compatible.

```java
public interface TenantIndex extends Closeable {

    /**
     * Registers the tenant with the given ID.
     *
     * <p>Applications call this method from their tenant onboarding flow, through
     * {@code Application.instance().tenantIndex()}. Adding a new tenant there notifies
     * the multitenant contexts with {@code TenantAdded}.
     *
     * <p>Spine calls it only when {@code Application} is set to
     * {@linkplain UnregisteredTenantPolicy#REGISTER register} tenants of incoming requests.
     */
    void keep(TenantId id);

    /**
     * Tells whether the tenant with the given ID is registered.
     *
     * <p>Implementations must not register the tenant as a side effect.
     * They must not cache a negative answer either, because another server
     * node may register the tenant at any moment.
     *
     * <p>If the index cannot answer, for example, because the directory behind it
     * is not available, the implementation must throw instead of returning {@code false}.
     *
     * <p>The default implementation looks the tenant up in {@link #all()}.
     * Implementations backed by a remote store should override it.
     *
     * @since 2.0.0
     */
    default boolean contains(TenantId id) {
        checkNotNull(id);
        return all().contains(id);
    }

    Set<TenantId> all();

    // `close()` and `singleTenant()` stay as they are.
}
```

`TenantStorage` answers from the cache and falls back to a point read. Only hits are cached:

```java
@Override
public final boolean contains(TenantId id) {
    checkNotNull(id);
    if (cache.contains(id)) {
        return true;
    }
    var found = read(id).isPresent();
    if (found) {
        cache(id);
    }
    return found;
}
```

`NamespaceIndex` in `gcloud-jvm` should override `contains` with its existing
`contains(Namespace)` lookup.

### 2. No policy for unregistered tenants

Decided by Q25. A request whose tenant is not in the index of the application is refused, in
every environment. There is no switch for automatic registration. An application registers
its tenants with `application.tenantIndex().keep(tenantId)` as a part of its onboarding, and
a test does it before it sends requests through the gRPC services. Requests posted in-process
are not checked (Q2), so most tests are not affected.

The policy designed before, `UnregisteredTenantPolicy` with `REJECT` and `REGISTER`, stays in
the questions below (Q3, Q5) for the record.

### 3. One gate at the service boundary

The services check the tenant of every request that comes through `CommandService`,
`QueryService`, and `SubscriptionService` (`subscribe`, `activate`, `cancel`). The check runs in
`ServiceDelegate`, after the serving context is found and before the request reaches it.
The services are created from the application they serve (section 12), so they ask it for
the tenant index and for the log. The delegates already tell the tenant of a request:
`tenantOf()` was added by #1713.

The gate makes two checks, in this order:

- **Shape:** a tenant is given if, and only if, the context is multi-tenant. Only for queries
  and subscriptions. The `TenantFilter` of the `CommandBus` checks commands, because in-process
  posts must be checked too; its answer does not change. A refused request is recorded in the
  System context of the serving context (Q33), as `TenantFilter` does for commands.
- **Membership:** the tenant is in the index of the application. For all three services, and
  only in a multi-tenant context. A refused request is reported to `ApplicationLog`
  (section 10). If the index fails to answer, the request fails with `INTERNAL_ERROR`, and the
  failure is reported to `ApplicationLog` too.

A topic that no context serves is subscribed in every context of the application. It has no
serving context to compare the shape with, so only the membership of its tenant is checked,
whenever the topic has a tenant.

```java
// ServiceDelegate
private void serveIn(BoundedContext context,
                     T request,
                     StreamObserver<R> observer,
                     @Nullable Object params) {
    if (!admitted(context, request, observer)) {
        return;
    }
    // Serves the request, as before.
}
```

`CommandBus` does not register tenants since PR 1 (Q19).

Why the boundary and not the buses:

- Only requests from outside the process are untrusted. Signals produced inside
  (commands from process managers, integration events, scheduled commands) descend from
  a request that was already admitted.
- With per-context indexes (possible until Q15), a bus-level gate would
  break legitimate flows. For example, context A admits tenant T; A's event reaches context B;
  B's process manager posts a command for T, and B's index has never seen T.
- In-process posting (`context.commandBus().post(...)`, `BlackBox.receivesCommand`) keeps
  working unchanged, so most tests are unaffected.

### 4. What the client gets, and what is recorded

Every tenant problem is answered with `ErrorCase.TENANT_ERROR` (Q11). Read failures use
`Statuses.failure(ErrorCase)` from #1713: the gRPC status has no description, and
`grpc-status-details-bin` carries the error case. There is no new trailer, no
`google.rpc.ErrorInfo`, no `NOT_FOUND`, and no `UNAVAILABLE`.

| Problem | Commands | Queries and subscriptions | Recorded |
|---|---|---|---|
| No tenant, multi-tenant context | `Ack` with `TENANT_ERROR`, from `TenantFilter` (as today) | `INVALID_ARGUMENT`, `TENANT_ERROR` | In the serving context: `CommandTenantNotSpecified` (exists), `QueryTenantNotSpecified`, `TopicTenantNotSpecified`, `SubscriptionTenantNotSpecified` |
| A tenant, single-tenant context | `Ack` with `TENANT_ERROR`, from `TenantFilter` (as today) | `INVALID_ARGUMENT`, `TENANT_ERROR` | In the serving context: `CommandTenantNotExpected` (exists), `QueryTenantNotExpected`, `TopicTenantNotExpected`, `SubscriptionTenantNotExpected` |
| The tenant is not registered | `Ack` with `TENANT_ERROR`, from `CommandService` | `INVALID_ARGUMENT`, `TENANT_ERROR` | `ApplicationLog`, `WARNING`: `RequestOfUnregisteredTenantRejected` |
| The index cannot answer | The call fails with `INTERNAL_ERROR`, like other unexpected failures | `INTERNAL`, `INTERNAL_ERROR` | `ApplicationLog`, `ERROR`: `TenantCheckFailed` |

`SubscriptionTenant*` events record the activation and the cancellation of a subscription.
The events of a topic are `TopicTenant*`.

The six new System events follow the rules of #1713:

- They go to a new `spine/system/server/tenant_events.proto`, with `(internal_all)` and
  `java_package = "io.spine.system.server.event"`. `CommandTenantNotSpecified` and
  `CommandTenantNotExpected` stay in `command_log_events.proto`, since `CommandLog`
  subscribes to them.
- The ID of the request is the first field. It is not `(required)`: the gate runs before the
  request is validated, so the request may have no ID. The field doc says so.
- They carry the `core.ActorContext actor_context` of the request, like the other read events
  of #1713.
- `*TenantNotExpected` carry `core.TenantId tenant` `[(required) = true]`.

The events of `ApplicationLog` are described in section 10.

### 5. Commands: an unregistered tenant is answered with an `Ack`

Decided by Q1, revised by Q11. `CommandService` answers a command of an unregistered tenant
with `Ack { message_id, status.error_case = TENANT_ERROR }`, the same answer `TenantFilter`
gives to a command without a tenant. `CommandServiceImpl.respondWithError()` from #1713
already builds such an `Ack` for every error case except `INTERNAL_ERROR`, so no new code is
needed for the answer.

- The Java client handles this `Ack` like any other error `Ack` (`ServerErrorHandler`, as
  changed by slice 4 of the rework), and cancels the subscriptions it made before posting.
- Nothing is recorded under the unregistered tenant. The command never enters the
  `CommandBus`, so there is no `CommandReceived` and no `CommandLog` record.
  The refusal is reported to `ApplicationLog` with `RequestOfUnregisteredTenantRejected`
  (section 10).
- spine-ts: Q8 kept `NOT_FOUND` there under the old wire contract. spine-ts adopts
  `ErrorCase` as a follow-up of the rework, and revisits Q8 then.

### 6. Removing `CommandValidationError`

Left out of PR 2 by Q26. `DoubleDispatchGuard` and `EntityLifecycle` still report
`DUPLICATE_COMMAND` with the enum, so it can go only when that value has another home.
The plan, as decided before:

Decided (Q11). Since #1713, no code uses `spine.core.CommandValidationError`: the
`CommandBus` and the services report problems with `ErrorCase` and System events. #1713
removed `QueryValidationError`, `TopicValidationError`, and `SubscriptionValidationError` for
the same reason. `CommandValidationError` is removed from `command.proto`:

- No `.proto` field has the type `CommandValidationError` (`Error.code` is `int32`), so stored
  data and JSON are not affected.
- The ambiguous name `TENANT_UNKNOWN` goes with the enum, so the planned alias is not needed.
- Docs that mention the enum are updated.
- spine-ts keeps its copy of `command.proto` until it adopts `ErrorCase`.

### 7. Test library

`MtBlackBox.withTenant()` registers the tenant in the index of the application of the black
box. The test author says the tenant exists, so this matches the meaning of the call. It also
keeps `BlackBox.clients()` working, since those clients go through an in-process
`GrpcContainer`, whose services check the tenant.

```java
@Override
@CanIgnoreReturnValue
public MtBlackBox withTenant(TenantId tenant) {
    this.tenantId = checkNotNull(tenant);
    application().tenantIndex()
                 .keep(tenant);
    return this;
}
```

A black box always has an application: `BlackBox.from(BoundedContext)` is removed (Q30).
Tests use `BlackBox.from(Application)` or `BlackBox.from(BoundedContextBuilder)`.

`BlackBoxClients.create(TenantId)` does **not** register the tenant. A test can use it to
check that a request of an unregistered tenant is rejected.

### 8. `Application` — one tenant index for all contexts

Decided (Q5). A new singleton holds what the Bounded Contexts of a process share as parts
of one application. `ServerEnvironment` keeps the technologies (storage, transport,
delivery, tracing); `Application` uses them, and never the other way around.

```kotlin
package io.spine.server

/**
 * The application made of the Bounded Contexts that run in this process.
 *
 * Holds what the contexts share as parts of one application, such as the tenants
 * the application serves. The technologies the contexts run on, such as storage
 * and transport, are configured by [ServerEnvironment].
 */
public class Application private constructor() : Closeable {

    private var tenantIndex: TenantIndex? = null

    @Volatile
    private var unregisteredTenantPolicy = UnregisteredTenantPolicy.REJECT

    /**
     * Obtains the index of tenants served by the multitenant contexts of the application.
     *
     * Unless another index is [set][use], the default one is created on the first call.
     * It is stored with the storage factory of [ServerEnvironment], so the storage factory
     * must be configured before the first multitenant context is built.
     */
    @Synchronized
    public fun tenantIndex(): TenantIndex =
        tenantIndex ?: defaultMultitenantIndex().also { tenantIndex = it }

    /**
     * Sets the index of tenants of the application.
     *
     * @throws IllegalStateException if the index is already in use, for example,
     *   because a multitenant context was built before this call.
     */
    @Synchronized
    @CanIgnoreReturnValue
    public fun use(index: TenantIndex): Application {
        check(tenantIndex == null) { "The tenant index of the application is already in use." }
        tenantIndex = index
        return this
    }

    /**
     * Tells how the contexts treat a request from a tenant that is not in the index.
     *
     * `REJECT` unless [set][use] otherwise.
     */
    public fun unregisteredTenantPolicy(): UnregisteredTenantPolicy = unregisteredTenantPolicy

    /**
     * Sets how the contexts treat a request from a tenant that is not in the index.
     */
    @CanIgnoreReturnValue
    public fun use(policy: UnregisteredTenantPolicy): Application {
        unregisteredTenantPolicy = policy
        return this
    }

    /**
     * Closes the tenant index.
     */
    @Synchronized
    override fun close() {
        tenantIndex?.close()
        tenantIndex = null
    }

    public companion object {

        private val instance = Application()

        /**
         * Obtains the application of this process.
         */
        @JvmStatic
        public fun instance(): Application = instance
    }
}
```

`instance()` follows `ServerEnvironment.instance()` and `Environment.instance()`, so Java
callers write `Application.instance()`, not `Application.INSTANCE`.

Changes elsewhere:

- Bounded Contexts keep no tenant index (Q18): the code that needs it asks
  `Application.instance().tenantIndex()`. `BoundedContextBuilder` loses `setTenantIndex()`,
  `hasTenantIndex()`, and `getTenantIndex()` (Q15), `BoundedContext` its index and
  `InternalAccess.tenantIndex()`, and `BusBuilder` its tenant-index API. The index of the
  application is closed by `Application.close()`.
- Shutdown order: contexts, then `Application`, then `ServerEnvironment`. The default index
  writes through the storage factory, so it must close before the factory does.
- An application registers a tenant once, when its own onboarding steps are done:
  `Application.instance().tenantIndex().keep(tenantId)`. The contexts are then notified with
  `TenantAdded` (section 9).
- Tests in `server` that switch storage factories call the internal `closeIndex()`, so that
  the next multitenant context gets an index in the new storage. Tests that set a policy set
  the default back with `use(UnregisteredTenantPolicy.REJECT)`. In-memory storages stay usable
  after their factory closes (`InMemoryStorageFactory.close()` only sets a flag), so tests
  that reset only the environment keep working.
- The System context of a multitenant context takes the index of `Application` too, instead
  of the second default index it makes today (see "Findings").
- `Application` also holds the `UnregisteredTenantPolicy` (section 2) and the record of
  refused requests with its switch (section 10). `close()` closes the record too.
- As implemented in PR 1, unlike the sketch above (Q16, Q17):
  - `Application` holds the registry of its contexts and of the entity state types they
    serve: `ContextRegistry`, given by `contexts()`, the internal class that replaced the
    JVM-wide `OpenContexts` object (Q16, Q20).
  - `close()` closes the open contexts, in the reverse order of their building, and then the
    tenant index. It closes the application for good, as the contract of
    `io.spine.server.Closeable` requires: no context can be built afterwards, and
    `tenantIndex()` and `use()` throw.
  - There is no `reset()`. The internal `closeIndex()` closes only the tenant index, for tests.
    The constructor is internal, so that tests check closing on applications of their own.

### 9. `TenantAdded` — contexts prepare for a new tenant

Left out of PR 2 by Q26, for a later PR. The design, as decided before:

Decided (Q9). `TenantAdded` stays and is posted when a tenant is added, so that Bounded
Contexts can prepare their internals for the new tenant's requests.

**How it reaches the contexts.** Through the System context of each multitenant context, the
way the framework posts its other events (`CommandReceived`, the catch-up events):

- `SystemWriteSide.postEvent()` under the new tenant (`WriteSideFunction.delegatingTo(...)
  .get(tenant)`). `SystemEventFactory` takes the producer ID from the first field of the
  message; `TenantAdded.id` is a `TenantId`, so the type already fits.
- Every event dispatcher of a domain context is also registered with the System read side
  (`BoundedContext.registerEventDispatcher()`, `BoundedContext.java:314`). So a reactor,
  a process manager, or a projection of the context receives `TenantAdded` by declaring
  a handler for it, with no new delivery code. A process manager with the `TenantId` as its ID
  is a natural place for per-tenant setup.
- A context with no handler for `TenantAdded` is not affected: the System write side posts
  with a no-op observer, as it does for `CommandReceived`.
- Like other system events, `TenantAdded` is not stored unless the context calls
  `systemSettings().persistEvents()`.

**Who posts it.** The index tells whether a tenant is new, so the event is posted by a wrapper
around the index, not by each `TenantIndex` implementation:

```kotlin
/**
 * Posts [TenantAdded] to the given contexts when a new tenant is added to the index.
 */
internal class AnnouncingTenantIndex(
    private val delegate: TenantIndex,
    private val contexts: () -> Iterable<BoundedContext>,
) : TenantIndex {

    override fun keep(id: TenantId) {
        val added = !delegate.contains(id)
        delegate.keep(id)
        if (added) {
            announce(id)
        }
    }

    override fun contains(id: TenantId): Boolean = delegate.contains(id)

    override fun all(): Set<TenantId> = delegate.all()

    override fun close() = delegate.close()

    private fun announce(tenant: TenantId) {
        val event = TenantAdded.newBuilder()
            .setId(tenant)
            .build()
        contexts().forEach { it.postSystemEvent(event, tenant) }   // Via internal access.
    }
}
```

- `Application.tenantIndex()` returns the application's index in this wrapper, announcing to
  every open multitenant context (`Application` keeps the record of the open contexts).
- Every way of adding a tenant goes through a wrapper: the application's onboarding
  (`Application.instance().tenantIndex().keep(id)`), the `REGISTER` policy in `TenantGate`,
  and `MtBlackBox.withTenant()`.

**What this does not guarantee:**

- *Exactly once.* Two nodes that add the same tenant at the same time may both see it as new
  (`TenantStorage.keep` reads, then writes), and both post the event. Handlers must be
  idempotent.
- *Every node.* Only the contexts open on the node that adds the tenant receive the event.
  Handlers that keep their state in entities are fine: entities are stored and delivered
  across nodes. State that lives in one node's memory (a cache, a connection pool) must also
  be prepared at startup from `all()`, or on the tenant's first request.
- *Before the first request.* Decided by Q10: `TenantAdded` is a notification. In production
  the System write side posts in parallel and `Delivery` may dispatch later, so the handlers
  may run after the tenant's first request. An application that needs the contexts ready
  before its users come in decides when to let them in, as part of its onboarding.
  The documentation of `TenantAdded` says so. A synchronous preparation step (a new SPI, run
  before the tenant is registered) can be added when a storage needs per-tenant setup;
  none of the JVM storages does today.

### 10. `ApplicationLog`

Decided by Q27, Q28, Q31, and Q32. It replaces the event store of `Application` that Q13 and
Q14 planned. Some refused requests concern no Bounded Context:

- a request that no Bounded Context serves, or of a type internal or unknown to the server
  (`UNSUPPORTED_REQUEST`), or whose type cannot be read (`INVALID_REQUEST`);
- a request of an unregistered tenant (`TENANT_ERROR`, section 4): the serving context would
  record it under a tenant that does not exist, and on Datastore the first write creates
  the tenant's namespace;
- a failure of the index to answer (`INTERNAL_ERROR`, section 4), for the same reason.

Such problems are few, so they get no `EventBus`, no `EventStore`, and no context. The
services report them to the log of the application:

```kotlin
package io.spine.server.app

public interface ApplicationLog {

    public fun log(level: Level, message: () -> String)

    public fun logEvent(level: Level, event: () -> EventMessage)

    public fun logEvent(level: Level, message: () -> String, event: () -> EventMessage)
}
```

- The level is `io.spine.logging.Level`. The other arguments are functions, so that nothing is
  computed for a level that is not enabled.
- The methods that take an event are named `logEvent`: `log(Level, () -> String)` and
  `log(Level, () -> EventMessage)` would have one JVM signature, and `@JvmName` is not allowed
  on the members of an interface.
- `Application.Builder.setApplicationLog()`, and the `applicationLog` property of the
  `application {}` DSL, set the log. `Application.log()` gives it, so that an application can
  write there too, for example, when it registers a tenant.
- Unless another log is set, the application uses `DefaultLog`, an `internal` class that writes
  with Spine Logging:
  - `@LoggingDomain("Application")` on the class prefixes each record with `[Application] `.
    The name of its logger, `io.spine.server.app.DefaultLog`, lets a logging backend route the
    records apart from the others.
  - An event is written as the name of its type, followed by its own compact JSON form (Q40),
    for example, `spine.server.app.UnsupportedRequestRejected {"request":{…},"reason":"…"}`.
    The event is not packed into `Any` (Q38): packing serializes it, and printing the `Any`
    reads the bytes back.
  - `logEvent(level, message, event)` writes one record of two lines: the message, then the
    event.
- `Application.close()` does not close the log.

The events, in `spine/server/app/events.proto`, with `java_package = "io.spine.server.app"`:

| Event | Reported when | Level |
|---|---|---|
| `RequestOfUnregisteredTenantRejected` | The tenant is not in the index. | `WARNING` |
| `TenantCheckFailed` | The index fails to tell if it has the tenant. | `ERROR` |
| `UnsupportedRequestRejected` | No context serves the type, or the type is internal to the server. | `WARNING` |

- Each event carries a `RefusedRequest`, declared in `spine/server/app/refused_request.proto`
  because it is not an event: the request itself, as a `oneof` (`command`, `query`, `topic`,
  `subscription_activation`, `subscription_cancellation`) (Q36). The request may be invalid: it
  is refused before it is validated. The events must be valid when they are composed:
  `request`, `tenant`, `error`, and `reason` are `(required)`, and so is the `oneof` of
  `RefusedRequest` (`(choice).required`). The refused request itself is not validated.
- The tenant events also carry the tenant and the name of the serving context, and
  `TenantCheckFailed` carries the error.
- Each event carries a required `reason`: why the request was refused, written for a person or
  an agent reading the log (Q37). For `UnsupportedRequestRejected`, it tells which of the three
  causes it was. The services write these records with `logEvent(level, event)`, so the text is
  not written twice.
- A request whose type cannot be read gets only `log(WARNING) { … }`, since nothing tells what
  kind of request it is. So does a request of a type unknown to the server (Q39): JSON cannot
  hold it, so the text names its type and its ID instead.
- The services no longer log these refusals with their own loggers.

### 11. `Server` serves the contexts of `Application`

Decided by Q21–Q23, in PR 1. `Application` is made of the Bounded Contexts of the JVM, so the
server serves them instead of building contexts of its own.

- **Building.** `Server.Builder.add(BoundedContextBuilder)` is removed. `Server.Builder.build()`
  takes the contexts registered in `Application` at that moment (`ContextRegistry` gets an
  internal `all()`); a context built later is not served. `build()` throws
  `IllegalStateException` if the application has no contexts, which is also the case once it
  is closed: closing it closes and unregisters the contexts.
  `BoundedContextBuilder.build()` is `@CanIgnoreReturnValue`: the application keeps the built
  context, so the caller does not need to. A package-private
  `Server.Builder.serving(Application)` lets a test give the server an application of its own,
  for example, one with no contexts.
- **Shutting down.** `Server.shutdown()` and `shutdownAndWait()` close the contexts the server
  serves, as before (Q22). The application stays open.
- **What goes away.** `Server.Builder` no longer builds contexts, so its code that closed the
  contexts built before a failed build is removed. An application whose start fails closes its
  contexts with `Application.instance().close()`.
- **Duplicate command types.** `CommandService.Builder.build()` throws `IllegalStateException`
  if two contexts handle commands of one type. Before, `TypeDictionary` silently routed such
  commands to the context added last. A server over every context of the JVM makes that more
  likely, for example, with a context that a test left open. Query and subscription types are
  not checked: entity state types are unique already, and one event type may be exposed by
  several contexts.
- **Documentation.** The doc of `Server` says it exposes the Bounded Contexts of the
  `Application`, and that they must be built before the server. Release notes: build the
  contexts, then `Server.atPort(port).build()` or `Server.inProcess(name).build()`.
- **Tests.**
  - `AbstractClientTest` builds the contexts its subclasses give before building the server;
    its nine subclasses do not change.
  - `ClientEndToEndTest` builds its contexts before the server.
  - `ServerSpec`: the cases that build a server with no contexts build one context first; the
    two cases on closing contexts after a failed build are removed; new cases: a server takes
    the contexts of the application, and `build()` throws without contexts.
  - A new case for the duplicate command types of `CommandService`.
  - A server built in a test also serves the contexts that earlier tests left open, and
    closes them when it shuts down. The duplicate-command check may surface such leaks; the
    leaking tests are then fixed to close their contexts.
- **Follow-ups outside core-jvm**, after PR 1 is merged: `examples/hello`, `examples/hello-kt`,
  the copy of hello in `documentation`, and the demo servlet of `delivery` build their
  contexts before the server.

### 12. The services are created from `Application`

Decided by Q29 and Q30. The gate needs the tenant index and the log of the application, and a
Bounded Context keeps no reference to its application (Q5 of `application-instances.md`). So
the services are created from the application, as `Server` is:

- `CommandService.of(application)`, `QueryService.of(application)`, and
  `SubscriptionService.of(application)` serve all the contexts of the application.
  The builders, `AbstractServiceBuilder`, and `withSingle(BoundedContext)` are removed: they
  held nothing but the contexts.
- `Application.Builder.build()` refuses an application whose two contexts handle one command
  type, before it builds any context (Q35). Before, `CommandService.Builder.build()` checked it
  (section 11).
- `Server.Builder` creates the services with `of(application)`.
- `BlackBox.from(BoundedContext)` is removed (Q30): the clients of a black box are served by the
  services of its application, and `MtBlackBox.withTenant()` registers the tenant in its index.

## Expected impact on existing tests

- In-process posting does not go through the gate, so most tests that use generated
  tenant IDs keep passing.
- Tests that post through the services with generated tenants must register them first:
  `CommandServiceTest`, `CommandServiceTenancyTest`, `CommandServiceMultiTenancySpec`, and the
  multi-tenant cases of `QueryServiceTest`, `SubscriptionServiceTest`, and the
  `*ErrorCaseSpec` classes.
- The shape checks of queries and subscriptions are new. A query or a subscription without
  a tenant in a multi-tenant context was served from the `SINGLE_TENANT` partition; now it is
  refused with `TENANT_ERROR`.
- The `BlackBox.clients()` tests pass because `withTenant()` registers.
- The tests that create the services from contexts create them from an application
  (section 12); the delivery tests that wrap a built context into a black box use
  `BlackBox.from(Application)`.
- New specs: the gate for each service and each row of the table in section 4, with the event
  and where it is recorded; `DefaultLog` (the prefix, the type name and the JSON, the two-line
  record, no call of the functions when the level is off); the log of `Application` (the
  default, a log set on the builder); no `CommandReceived` for a command of an unregistered
  tenant; the services reporting the requests that no context serves.

## Implementation plan

PR 1 was merged as #1717. PR 2 does the rest, in one PR (Q26). It is breaking: it removes the
service builders and `BlackBox.from(BoundedContext)`, and refuses requests that were served
before. Bump the version (`bump-version`), regenerate the dependency reports with a full build,
and pass `./gradlew clean build dokkaGenerate`. After every test run, read the test count, not
only the exit code.

1. **Protobuf.** `spine/server/app/refused_request.proto` (`RefusedRequest`) and
   `spine/server/app/events.proto` (the three events of section 10), with
   `java_package = "io.spine.server.app"`; `spine/system/server/tenant_events.proto` (the six
   events of section 4).
2. **`io.spine.server.app`.** `ApplicationLog`, `DefaultLog`, and the membership check of the
   gate.
3. **`Application`.** `log()`, `Builder.setApplicationLog()`, and the `applicationLog`
   property; `DefaultLog` unless set.
4. **The services** (section 12). `of(Application)`; the builders, `AbstractServiceBuilder`,
   and `withSingle()` removed; `Server` creates the services from its application.
5. **The gate** (section 3). `ServiceDelegate` checks the shape of the tenant of queries and
   subscriptions, then its membership. The refusals and failures of section 4, and the
   requests that no context serves, are reported to `ApplicationLog`.
6. **`server-testlib`** (section 7). `BlackBox.from(BoundedContext)` removed; the clients of
   a black box are served by the services of its application; `MtBlackBox.withTenant()`
   registers.
7. **Tests.** Create the services from applications; register the tenants that tests send
   through the services; the new specs of "Expected impact on existing tests".
8. **Docs.** The Javadoc of `TenantIndex`, the services, and `ServiceDelegate`; the migration
   guide; the release notes on refusing unregistered tenants and on `ApplicationLog`.

Follow-ups in other repositories, once PR 2 is merged:

- **spine-ts:** revisit how it answers a command of an unregistered tenant (Q8) when it adopts
  `ErrorCase`.
- **gcloud-jvm:** set the index of the application in `DatastoreStorageFactory`; override
  `contains` in `NamespaceIndex`; make registration durable, so that other nodes do not reject
  a tenant until it has data (item 5 below).

Follow-ups in core-jvm: `TenantAdded` (section 9), and the removal of `CommandValidationError`
(section 6), once `DUPLICATE_COMMAND` has another home.

## As implemented

Added while implementing PR 2, within the decisions above:

- The gate checks the shape of the tenant through two hooks of `ServiceDelegate`,
  `tenantNotSpecified()` and `tenantNotExpected()`, which create the System event of the
  problem. `CommandService` returns `null` from both, so commands keep being checked by
  `TenantFilter`. Each service also describes its requests for the log with `refusedRequest()`.
- The membership check is the internal `TenantGate` of `io.spine.server.app`. It returns the
  error case to answer with: `TENANT_ERROR` for an unregistered tenant, `INTERNAL_ERROR` if the
  index fails.
- The `context` field of `RequestOfUnregisteredTenantRejected` and `TenantCheckFailed` is not
  required: a topic that no context serves is subscribed in every context, and has no serving
  context. Such a topic, and the activation and cancellation of its subscription, get only the
  membership check, and only if the application has a multi-tenant context.
- The services serve all the contexts of the application. `TypeDictionary` loses `contexts()`
  and `isEmpty()`, which nothing uses any more.
- `GivenCommandService` (test fixtures) is removed: tests create the services from test
  applications. `CommandServiceBuilderSpec` became `CommandServiceOfSpec`.
- `AbstractDeliveryTest.newApplication()` is public, for tests that need the context and the
  black box of one application. `blackBox()` builds a black box over an application of its own,
  and closing the black box closes the application.
- The routing fixtures `createFizzBuzzContext()`, `createSortingContext()`, and
  `drawingContext()` return builders. `ProjectionEndToEndTest` checks the external delivery
  between two contexts of one application without black boxes: a black box needs an
  application of one context.
- The migration guide is `docs/migration/unregistered-tenants.md`.
- After merging #1725 (2026-11-12), activation and cancellation of a subscription to a topic
  that no context serves share one path in `SubscriptionDelegate`, which checks the tenant first.
  `SubscriptionServiceUnservedTopicSpec` creates its services from applications: the contexts
  disagree because the subscription is created in one context's `Stand` directly, and the
  multi-tenant case registers its tenants.

Added after the pre-PR review (2026-11-12):

- The KDoc of `ApplicationLog` states that an implementation must be thread-safe and must not
  throw: the services write to it concurrently, while they answer requests. `DefaultLog` is
  an `object`, since it holds no state.
- `io.spine.server.given.FailingIndex` serves the specs of the gate, which no longer use `!!`.
- Base is `2.0.0-SNAPSHOT.465` (SpineEventEngine/base-libraries#971): `runKeepingFailure()`
  lets Java callers ignore its result, so IDEA does not report them.

Added after the review of the PR (#1727, 2026-11-12):

- `BoundedContextBuilder.commandTypes()` counts the commands of the event dispatchers that
  delegate the dispatching of commands to them too: a context registers them with its
  `CommandBus`.
- `CommandRequest.post()` reports the error case of a refused subscription to its events, for
  example, of an unregistered tenant, to the server error handler, cancels the subscriptions
  already made, and does not post the command, instead of letting the gRPC exception escape.

## Other aspects to discuss

1. **Scope of the index.** Resolved by Q5: one index per application, held by
   `Application` (section 8). The per-context override is removed (Q15). Unlike spine-ts, it
   is not part of `ServerEnvironment`.
2. **How applications register tenants.** Resolved by Q5:
   `Application.instance().tenantIndex().keep(tenantId)`.
3. **`TenantAdded`.** Resolved by Q9: posted when a tenant is added (section 9).
4. **Removing or suspending a tenant.** Out of scope, but `contains` makes it possible.
   After removal, other nodes may keep a cached positive answer until they restart.
5. **Datastore.** `NamespaceIndex.keep` is not durable, so with `REJECT` a tenant registered
   on one node is rejected by the others until the tenant has data. `gcloud-jvm` needs a
   durable registry or a documented onboarding step.
6. **Tenant enumeration.** `TENANT_ERROR` does not say which check failed, but a caller that
   sends a tenant to a multitenant context still learns from it that the tenant is not
   registered. Authentication should run before the Spine services (in an interceptor), so
   that anonymous callers cannot probe tenant IDs.
7. **User-facing messages.** Resolved by Q7 and Q11: the client gets only `TENANT_ERROR` and
   builds its own text.
8. **Other errors.** Errors with a Java class name in `Error.type`, and the `UNKNOWN`
   status from `QueryService.serveNoContext`, had the same portability problem. Resolved by
   #1713: the services answer with `ErrorCase` only.
9. **Blocking lookups.** `contains` is synchronous. A remote directory call blocks the
   gRPC thread, so implementations should cache hits.
10. **Commands differ from spine-ts.** After Q1, the JVM answers a command of an unknown
    tenant with an error `Ack`, and spine-ts fails the RPC with `NOT_FOUND`. Either spine-ts
    switches to the `Ack` too (it already uses the `Ack` for `TENANT_REQUIRED` and
    `TENANT_INAPPLICABLE`), or the difference is documented for client authors.
    Resolved by Q8: documented. Superseded by Q11: the JVM answers with
    `ErrorCase`, and spine-ts revisits Q8 when it adopts `ErrorCase`.
11. **spine-ts adopts `TenantError`.** Moot since Q11: there is no `TenantError`. spine-ts
    adopts `ErrorCase` as a follow-up of the rework, and drops the enums that #1713 and
    this work remove.
12. **The name `Application` is common in user code.** The Javadoc of
    `io.spine.environment.Environment` uses `public final class Application` as the example of
    an application's own class. Such applications would refer to the new type by its full name,
    `io.spine.server.Application`, in the files that use both.
13. **`OpenContexts` belongs to `Application` too.** Resolved by Q16: in PR 1, the record of
    the open contexts and of the entity state types they serve became a part of `Application`.
14. **Floods of refused requests.** Every refusal is reported to `ApplicationLog`, with no
    rate limit, as `ServiceDelegate` logs the other refusals since #1713. Since Q36, each record
    holds the whole request, so the volume of the log grows with the size of the requests, and
    the log holds the data that the callers send. An application open to anonymous callers
    should authenticate them first (item 6).

## Questions and the options considered

Questions 4, 6, and 8 were superseded on 2026-11-09 by question 11.

1. ~~Commands: fail the RPC with `NOT_FOUND` (option A), or answer with an error `Ack`
   (option B)?~~ Decided: B.
2. ~~Gate: at the service boundary only (proposed), or in `CommandBus` too (as spine-ts)?~~
   Decided: the service boundary only. A bus-level check runs inside the pipeline that posts
   `CommandReceived` and `CommandErrored` under the command's tenant, and, with per-context
   indexes, rejects commands that process managers post for tenants admitted by another
   context.
3. ~~`Tests` default `REGISTER`: acceptable, or should tests register tenants explicitly?~~
   Decided: `REJECT` everywhere; tests register explicitly. After Q2 the policy affects
   only requests that come through the gRPC services; tests that post in-process are never
   checked. By reading (not running) the tests, a `REJECT` default
   would break about three test classes in core-jvm (`CommandServiceTest` and the
   `CommandServiceTenancyTest` family, which post with generated tenants), plus the
   `BlackBox.clients()` tests unless `withTenant()` registers. A search of the sibling
   repositories in `~/Projects/Spine` (`examples`, `chat-bot`, `delivery`, `gcloud-jvm`,
   `jdbc-storage`, …) found no other tests that send requests of a multitenant context
   through the services; the only matches are copies of core-jvm (`summit/core-jvm`,
   `core-java-1x`). Applications outside the organization may still have such tests.
   If tests register explicitly: `MtBlackBox.withTenant()` registers, but
   `BlackBoxClients.create(tenant)` does not, so that a test can still check
   the rejection.
4. ~~Error payload: copy spine-ts exactly (`type` is a string, `code` is the gRPC code), or
   introduce a shared Protobuf enum for tenant errors and update spine-ts to match?~~
   Decided: an enum, nested as `spine.core.TenantError.Reason` (B2). The design was dropped
   by Q11; the discussion is kept below. Facts:
   - `spine.base.Error` documents `type` as "an exception from Java, C# (or another
     language) or a Proto enum name", and `code` as "a number from a Proto enum value".
     spine-ts puts an enum *value* name in `type` and a gRPC status number in `code`.
   - Inside an `Ack`, Spine reads `Error.code` as a `CommandValidationError` number. The
     spine-ts `code` 5 (`NOT_FOUND`) is `CommandValidationError.EXPIRED`, so a client that
     checks the code without the type would misread the error. No Java client code reads
     `Error.code` today, so the risk is in application code.
   - spine-ts already follows the enum convention for another error: `client-web`
     (`client.ts:1927`) recognizes an unknown subscription by
     `type == "spine.client.SubscriptionValidationError"` and
     `code == UNKNOWN_SUBSCRIPTION`. Only the tenant error uses a bare string.
   - spine-ts depends on `type: "TENANT_UNKNOWN"` in its server, in `client-web` tests and
     in `client-web/REFERENCE.md`. Its packages are at `2.0.0-snapshot.26`, with no release tag.

   Option B, sketched. **Where the enum goes.** Tenant errors apply to commands as well as
   to queries and subscriptions, and `TenantId` is in `spine.core`, so `spine.core` is the
   natural home. (`spine.client` holds the read-side requests — `Query`, `Topic`,
   `Subscription` — and their errors. The package does not mark a side: the server uses
   `spine.client` types too.) The obstacle is a name clash, checked with protoc 4.36.2:
   a top-level `spine.core.TenantError` with `TENANT_UNKNOWN` and `TENANT_INAPPLICABLE`
   fails with `"spine.core.TENANT_UNKNOWN" is already defined`, because enum values are
   siblings of their enum type. The alias `TENANT_REQUIRED` proposed in section 6 would
   clash too. Ways to resolve it:

   - **B1. Top-level `spine.core.TenantError`, retiring the tenant values of
     `CommandValidationError`** (`reserved 3, 4; reserved "TENANT_UNKNOWN",
     "TENANT_INAPPLICABLE";` — protoc accepts this next to the new enum). All tenant errors,
     commands included, use one enum, and question 6 goes away. Breaks Java source that
     references the two constants: in the `~/Projects/Spine` repositories these are only
     `InvalidCommandException` and two tests in core-jvm. No `.proto` field has the type
     `CommandValidationError` (`Error.code` is `int32`), so stored data and JSON are not
     affected. The `Ack` for a command without a tenant changes from
     `type = io.spine.server.commandbus.InvalidCommandException, code = 3` to
     `type = spine.core.TenantError, code = TENANT_REQUIRED`.
   - **B2. Nested in a message, `spine.core.TenantError.Reason`.** Nested enum values are
     scoped by the message, so nothing clashes (also checked with protoc). Nothing breaks,
     but `spine.core` keeps two `TENANT_UNKNOWN` constants with different meanings, and
     `Error.type` becomes `spine.core.TenantError.Reason`.
   - **B3. `spine.client.TenantError`** (the first draft). No clash and nothing breaks,
     but the package is about read-side requests, not commands.

   B2 was chosen, and dropped later by Q11.
5. ~~Index scope: application-wide in `ServerEnvironment` now, or later?~~
   Decided: application-wide, now, held by a new `Application` singleton (section 8), because
   `ServerEnvironment` is about technologies, not domain things like tenants. Facts that led
   to it:
   - `DefaultTenantStorage` always uses the same spec, `ContextSpec.singleTenant("__SYSTEM_TENANTS__")`.
     The per-context lists in memory come only from each context calling
     `TenantIndex.defaultMultitenant()`, which makes a new storage from
     `ServerEnvironment.instance().storageFactory()`. On JDBC the contexts already share one table.
   - After Q3 (`REJECT` by default), an application must register its tenants. Today it can
     do that only through `@Internal` API (`context.internalAccess().tenantIndex().keep(id)`),
     once for every context.
   - Sharing one index by calling `setTenantIndex(shared)` on every builder does not work:
     `BoundedContext.close()` closes its index (`BoundedContext.java:587`), so the first
     context to close breaks the others.
   So "later" would mean shipping `REJECT` with no public way to register a tenant in an
   application of several contexts.
6. ~~Rename `TENANT_UNKNOWN` → `TENANT_REQUIRED` in `CommandValidationError` with an alias?~~
   Decided: the alias is `TENANT_NOT_SPECIFIED`, and the new reason is
   `TenantError.Reason.TENANT_NOT_REGISTERED`; both dropped later by Q11. Commands keep
   `CommandValidationError` for a missing or inapplicable tenant. The discussion:
   After Q4 (B2) the alias no longer clashes, since the new values are scoped by
   `TenantError`. But `spine.core` now has `CommandValidationError.TENANT_UNKNOWN` ("missing")
   and `TenantError.Reason.TENANT_UNKNOWN` ("not registered"). An alternative to the alias:
   report a command's missing or inapplicable tenant with `TenantError.Reason` too, and
   deprecate the two `CommandValidationError` values.
7. ~~User-facing messages: client-side by `ErrorInfo.reason`, or `LocalizedMessage` from the server?~~
   Decided: clients only. The server sends the developer message; clients map the
   `TenantError.Reason` value to their own text. Facts:
   - After Q1, a command's tenant error comes in an `Ack`, which has no `ErrorInfo`. The key
     that every case has is the `TenantError.Reason` value: in `Error.code` and in
     `TenantError.reason` inside `Error.details`.
   - Requests carry the user's language: `ActorContext.language` (`spine.ui.Language`, ISO 639-1
     codes). The Java client never sets it, so it is empty today.
   - `google.rpc.LocalizedMessage` fits only the `google.rpc.Status` of a failed RPC. An `Ack`
     has no place for it: `Error.details` holds the `TenantError`, so `TenantError` would need
     a field for the text.
   - Core-jvm has no message catalogs or other localization support.
   - The words a user should see are the product's. One application calls its tenants
     "organizations", another "workspaces"; the framework cannot write that text.

   Options:
   - **Clients only.** The server sends the developer message. A client maps the
     `TenantError.Reason` value to its own text, in the user's language.
   - **The server too.** Spine adds a default user-facing text in the language from
     `ActorContext.language`: `LocalizedMessage` for RPC failures, and a new `TenantError` field
     for the `Ack`. Applications override the text. Needs a catalog and a way to override it.
8. ~~Commands in spine-ts: should spine-ts answer a command of an unregistered tenant with an
   error `Ack` too, or keep failing the RPC with `NOT_FOUND` and document the difference?~~
   Decided: keep `NOT_FOUND` in spine-ts and document the difference. Superseded by Q11. Facts:
   - spine-ts already answers `TENANT_REQUIRED` and `TENANT_INAPPLICABLE` for commands with an
     error `Ack` (`ServiceValues.commandErrorAck`); only `TenantUnknownError` is turned into a
     failed RPC, in the `catch` of `#dispatchCommand` (`spine-services.ts`).
   - spine-ts changes for Q4 and Q6 anyway (item 11), so `client-web` and its tests change in
     that same release.
   - Without the change, a TypeScript client that talks to both kinds of servers must look for
     `TENANT_NOT_REGISTERED` in the `Ack` (JVM) and in the RPC error (spine-ts).
9. ~~`TenantAdded`: emit it, or delete it?~~ Decided: keep it and post it, so that the
   contexts can prepare for the new tenant (section 9). The discussion:
   - It is declared in `server/src/main/proto/spine/server/tenant/events.proto`
     (Java package `io.spine.server.tenant.event`), the only type in that file. It was added
     on 2017-11-30 and has never been emitted since. Nothing in the `~/Projects/Spine`
     repositories produces or consumes it.
   - After Q5 the index is held by `Application`, which is not a Bounded Context. To emit an
     event, `keep` would need a context to post it to, and a tenant to post it under.
   - Registration is the application's own onboarding step. The application can emit its own
     domain event there, with the details it needs (who onboarded the tenant, the plan, …).

   Options:
   - **Delete it** in this change, together with `events.proto`. It breaks only code that
     references `io.spine.server.tenant.event.TenantAdded`; none was found.
   - **Deprecate it** now (`option deprecated = true;`) and delete it in a later version.
   - **Emit it** from `keep` when a new tenant is stored. `Application` would post it to the
     System context of every multitenant context, under the new tenant. Someone would also
     have to subscribe to it: System events are not offered to clients by default.
10. ~~Timing of `TenantAdded`: may the contexts finish preparing after the tenant's first
    request, or must adding a tenant wait until they are ready?~~ Decided: a notification
    (section 9). Facts:
    - In production the System write side posts in parallel (`postEventsInParallel()`, on by
      default outside tests), and `Delivery` may dispatch to entities later. So `keep()`
      returns before the handlers run. The catch-up API documents the same effect
      (`ProjectionRepository.catchUp()`).
    - In tests, posting is synchronous (`SystemSettings.defaults()` disables parallel posting
      in `Tests`), so a test sees the handlers' results right after `withTenant()`.
    - Spine has no general way to wait until every handler of an event, in every context,
      has finished. Waiting would need a synchronous step that is not an event, like
      spine-ts's `createStorageStructures(tenantId)`, which the application calls after
      `tenantDirectory.keep(tenantId)`.
    - With the `REGISTER` policy, the tenant is added by its first request, so the event and
      that request always race.

    Options:
    - **A notification.** `TenantAdded` may arrive after the first request. Handlers are
      idempotent. An application that needs the contexts ready before users come in decides
      when to let them in, as part of its onboarding.
    - **Ready before admission.** Adding a tenant also runs a synchronous preparation step in
      every context (a new SPI), and returns only when all of them are done. `TenantAdded`
      remains the notification for domain handlers.
11. ~~After the `Status` rework, what replaces `TenantError` and the `TENANT_NOT_SPECIFIED`
    alias, and what happens to `CommandValidationError`?~~ Decided: nothing replaces them on
    the wire; the client gets only `TENANT_ERROR`, and the details go to System events.
    `CommandValidationError` is removed. Options for the enum:
    - **Remove it** (chosen), like the read-side enums that #1713 removed.
    - **Deprecate it**, and remove it with `Status.error` (#1706).
    - **Keep it** unchanged for now.
12. ~~Where is a request of an unregistered tenant recorded?~~ Decided: by `Application`, in
    this work, together with the requests that no context serves (section 10). Options:
    - **Log it now**, and add an application-wide record in a later PR.
    - **Build the application-wide record now** (chosen).
    - **The serving context**, under the tenant, like the other tenant checks. It writes data
      for a tenant that does not exist (with `persistEvents()` or the `CommandLog`), and on
      Datastore that write creates the tenant's namespace.
13. ~~What is the record of `Application`?~~ Decided: an `EventStore` of its own. Options:
    - **An event store of `Application`** (chosen): System events appended directly, with no bus
      and no context.
    - **A System context of `Application`**: the same storage rules as other System contexts,
      and tracing sees the events. Costs a context to build and close, a reserved name in
      `OpenContexts`, and catch-up and shard repositories it does not need.
    - **A pluggable recorder**: an SPI with a logging default; the framework stores nothing.
14. ~~Does `Application` store the record by default?~~ Decided: no. Options:
    - **Off by default** (chosen): a switch on `Application` turns storing on. Until then, a
      refusal is only logged, and the store is not created.
    - **On by default**: storage grows with every refused request, including floods of
      made-up tenant IDs.
15. ~~Keep the per-context index (`BoundedContextBuilder.setTenantIndex()`) next to the index of
    `Application`?~~ Decided: remove it, in PR 1. Options:
    - **Remove it** (chosen). Its users were gcloud-jvm's
      `DatastoreStorageFactory.configureTenantIndex()` and three test classes of core-jvm.
    - **Deprecate it**, and remove it in a later release.
16. ~~Merge `OpenContexts` into `Application`?~~ Decided: yes, in PR 1, and `close()` closes
    the open contexts before the tenant index. Options:
    - **Merge, and `close()` closes the open contexts** (chosen): the shutdown order becomes
      automatic.
    - **Merge, and leave the contexts to the caller**: `close()` only refuses new contexts.
    - **Later**, in a PR of its own; PR 1 only rewords the doc of `Application`.
17. ~~Keep `Application.reset()` for tests?~~ Decided: no. In terms of the domain, an
    application is closed, not reset. Tests use the internal `closeIndex()`, and applications
    of their own to check closing.
18. ~~Keep the tenant index in each Bounded Context (a copy of the index of `Application`)?~~
    Decided: no. Nothing in production read it; its only use carried the index to `CommandBus`.
    A copy taken when the context is built also goes stale when the index of the application
    is closed. Options:
    - **Ask `Application` where the index is needed** (chosen), and remove the tenant-index API
      of `BusBuilder` too.
    - **Remove it from the contexts only**, and let `CommandBus` get the index through
      `BusBuilder` until PR 2.
19. ~~Stop `CommandBus` registering tenants in PR 1, or in PR 2 with the gate?~~ Decided: in PR 1.
    Nothing read the index yet, and only the spec added by PR 1 checked the registration.
20. ~~Keep the name `OpenContexts` for the record of contexts, given by `openContexts()`?~~
    Decided: `ContextRegistry`, given by `Application.contexts()`. Options:
    - **`ContextRegistry`** (chosen), after `DispatcherRegistry` and `SubscriptionRegistry`.
    - Another name; `Contexts` is taken by the file facade of `Contexts.kt`.
21. ~~Add contexts to `Application` with `add()`, and let `Server` take them from there?~~
    Decided: `Server` takes them from `Application`, with no `add()`: building a context
    registers it already. An `add()` would make a second list, and a context built but not
    added would be guarded, yet served by no one.
22. ~~What does `Server.shutdown()` do with the contexts?~~ Decided: closes them, as before.
    Options: stop only the transport, and let `Application.close()` close the contexts; or
    close them, as before (chosen). Closing the `Application` was not an option: it closes for
    good, and tests start and stop servers in one JVM.
23. ~~In PR 1, or a PR of its own?~~ Decided: PR 1.
24. ~~Keep the record of `Application`, `TenantAdded`, and the gate in three PRs?~~ Decided: one
    PR, PR 2, after PR 1. So the plan has two PRs.
25. ~~Keep `UnregisteredTenantPolicy`?~~ Decided: no. An unregistered tenant is always
    refused. The option of `REGISTER` for applications that rely on automatic registration was
    dropped with the policy.
26. ~~What does PR 2 include?~~ Decided: the gate, the shape checks of queries and
    subscriptions, and reporting the requests that no context serves. Left out: `TenantAdded`,
    and the removal of `CommandValidationError`, which still reports `DUPLICATE_COMMAND`.
27. ~~Where are the requests refused before a context recorded?~~ Decided: in `ApplicationLog`,
    with no `EventBus` or `EventStore` of the application. On the level: the owner asked for
    a level parameter, and for functions instead of values, so that the logging API computes
    them only if the level is enabled. On the names, options were: `logEvent` for the
    message-less form only; `logEvent` for both forms that take an event (chosen); no
    message-less form.
28. ~~How are the records of `DefaultLog` told from the others?~~ Decided:
    `@LoggingDomain("Application")` on `DefaultLog` only. Options: the same annotation
    (`@JvmLoggingDomain`) on the package, which would prefix the records of `Application` too;
    a boolean `MetadataKey` that the default formatter prints as `[CONTEXT …]`. On the JSON of
    an event: packed into `Any`, with `@type` (chosen); the simple type name before the JSON;
    the fields only.
29. ~~How do the services get the tenant index and the log?~~ Decided: they are created from
    an `Application`, with `of(application)`. Options: the application gives each context its
    index and log when it builds it (reverses Q18); the service builders take both the contexts
    and the application. On the API: `of(Application)` (chosen); a builder with
    `setApplication()`; services reachable only through `Server`.
30. ~~Keep `BlackBox.from(BoundedContext)`?~~ Decided: remove it. Options: keep it without
    clients and with a `withTenant()` that does not register; `from(BoundedContext, Application)`.
31. ~~How does code reach the log of an application?~~ Decided: the public `Application.log()`.
    Options: `applicationLog()`; an internal accessor only.
32. ~~Which events describe the problems reported to `ApplicationLog`?~~ Decided: one event per
    problem, with the request as a `oneof` of its ID. Options: one event per kind of request,
    like the System events of #1713 (about 12 events); new events for tenants only, with the
    System events reused for the requests that no context serves.
33. ~~How are the shape checks of queries and subscriptions recorded?~~ Decided: six System
    events, one per kind of request and check, in the serving context. Options: two System
    events with the request as a `oneof`; `ApplicationLog`.
34. ~~Move `Application` and its code to `io.spine.server.app`?~~ Decided: no. The owner keeps
    `Application` and the code written for it in `io.spine.server`, to keep the imports few and
    the structure visible from the top. Only the new parts go to `io.spine.server.app`.
35. ~~Where does the application check that no two contexts handle commands of one type?~~
    Decided: in `Application.Builder.build()`, before building any context (option A). Options:
    - **Before building, from the builders** (chosen): the same moment as the checks of names
      and entity state types; nothing is built for an application that fails.
    - **Right after building, from the built contexts**: one source of truth, what each
      `CommandBus` registered; the built contexts are closed on a failure.
    A handler registered with `commandBus().register()` after the application is built is not
    checked by either.
36. ~~Should `RefusedRequest` hold the request, or a reference to it?~~ Decided: the request
    itself. Options:
    - **The request** (chosen): each record is complete for diagnostics, as `CommandReceived` of
      the System context holds the whole command. The log then holds the data of untrusted
      callers, and grows with the size of their requests.
    - **A reference**, made of the ID, the type URL, and the actor context, as the read-side
      System events of #1713 do.
37. ~~Where does the reason of a refusal go?~~ Decided: a required `reason` in each event.
    Options:
    - **A field of each event** (chosen): the reason belongs to the refusal, and
      `RefusedRequest` stays the request alone.
    - **A field of `RefusedRequest`**: one place for all three events.
    - **The message line only**, as before: a log that keeps only the events loses it.
38. ~~How does `DefaultLog` print an event?~~ Decided: its own compact JSON, with `@type` added
    as the first field. The owner does not want an event packed before it gets to the log, nor
    bytes in a record: the fields go into the compact JSON. The services compose events that
    JSON can hold, so `DefaultLog` converts nothing (Q39).
39. ~~How is a request of a type unknown to the server written to the log?~~ Decided: as text,
    naming the type and the ID of the request (option A). Options:
    - **Text only** (chosen), like a request whose type cannot be read.
    - **The request without its unreadable parts**, cleared by each service before it composes
      the event: a command's message, the filters of a query or a topic.
    - **A separate event**, with the type URL, the ID of the request, and its actor context.
40. ~~Where does the type of a logged event go?~~ Decided: before the JSON, as the full name of
    the type. The JSON is the event's own, with no splicing. Options:
    - **Before the JSON** (chosen).
    - **`@type` inside the JSON**: needs a field spliced into the printed JSON, since Protobuf
      prints `@type` only for `Any`.
    - **No type**: records of different events look alike apart from their fields.
