# Reject requests from unregistered tenants

## Status

**In progress.** The design was settled on 2026-11-04 (questions 1–10) and revised against
`ErrorCase` on 2026-11-09 (questions 11–14), on top of the services slice of the `Status`
rework (#1713, merged). The work is tracked by #1709.

Progress:

- [ ] PR 1, the tenant index of the application: #1717, from branch
  `claude/application-tenant-index` (`2.0.0-SNAPSHOT.651`).
- [ ] PR 2, the record of `Application`.
- [ ] PR 3, `TenantAdded`.
- [ ] PR 4, rejecting unregistered tenants.

Since the rework, the client learns only the kind of a problem: `ErrorCase.TENANT_ERROR` for
every tenant problem. The server records the details as System events. Sections 3–6 are
rewritten for it, and section 10 is new.

Confirmed while revising: `UnregisteredTenantPolicy` is set on `Application`, which holds the
tenant index (from Q5). The other two items of the former "To confirm" list described
`TenantError`, which is dropped (Q11).

Decisions:

- Q1 (2026-11-04): for commands, an unregistered tenant is reported in an error `Ack`
  (option B), like other command validation errors. Since Q11, the `Ack` carries
  `ErrorCase.TENANT_ERROR`. See section 5.
- Q2 (2026-11-04): the tenant is checked only at the service boundary, not in the buses.
  See section 3. A bus-level check may come later, together with tenant removal.
- Q3 (2026-11-04): `REJECT` is the default in all environments, `Tests` included.
  `MtBlackBox.withTenant()` registers its tenant; `BlackBoxClients.create(tenant)` does not.
  See sections 2 and 7.
- Q4 (2026-11-04, superseded by Q11): tenant errors use a Protobuf enum nested in a message,
  `spine.core.TenantError.Reason` (option B2).
- Q5 (2026-11-04): one tenant index for the whole application, in this change. It is held
  by a new `Application` singleton, not by `ServerEnvironment`: `ServerEnvironment` is about
  the technologies that implement the server side, not about domain things like tenants.
  See section 8. Following the same principle, `UnregisteredTenantPolicy` is set on
  `Application` too.
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
- Q10 (2026-11-04): `TenantAdded` is a notification. The contexts may finish preparing after
  the tenant's first request; handlers are idempotent. A synchronous preparation step can be
  added later, when a storage needs per-tenant setup. See section 9.
- Q11 (2026-11-09): no `TenantError` type and no `TENANT_NOT_SPECIFIED` alias. The client gets
  only `ErrorCase.TENANT_ERROR`. The server records the details as System events named after
  the kind of request and the check. `CommandValidationError` is removed: no code uses it
  since #1713, which removed the read-side enums for the same reason. See sections 4 and 6.
- Q12 (2026-11-09): a request refused because its tenant is not registered, and a request that
  no Bounded Context serves, are recorded by `Application`, as part of #1709. The serving
  context does not record them: its System context would store data under a tenant that
  does not exist, and on Datastore the first write creates the tenant's namespace.
  See section 10.
- Q13 (2026-11-09): the record of `Application` is a single-tenant `EventStore` of its own.
  The events are appended to it directly: no bus, no context, no subscribers. See section 10.
- Q14 (2026-11-09): `Application` does not store the record unless told to, like
  `SystemSettings.persistEvents()`. Until then, a refusal is only logged, and the store is not
  created. So a flood of requests with made-up tenants does not grow the storage of an
  application that has not opted in. See section 10.
- Q15 (2026-11-09, while reviewing PR 1): no index per context. `BoundedContextBuilder` loses
  `setTenantIndex()`, `hasTenantIndex()`, and `getTenantIndex()`, so every multitenant context
  and its System context use the index of `Application`. A per-context index served no need of
  this design, and made every PR more complex: closing rules in PR 1, a second announcing
  wrapper in PR 3, and tenants that one context admits and another rejects in PR 4. gcloud-jvm
  sets the index of `Application` instead. See section 8.
- Q16 (2026-11-09, while reviewing PR 1): `OpenContexts` is merged into `Application`, in PR 1,
  so that the application knows the contexts it is made of. It stays an internal class of its
  own, which `Application` holds, rather than a JVM-wide object: detekt limits a class to 11
  functions. `Application.close()` closes the
  open contexts, in the reverse order of their building, and then the tenant index; after it,
  no context can be built. See section 8.
- Q17 (2026-11-09, while reviewing PR 1): `Application` has no `reset()`. An application is
  closed, not reset. Tests close only the tenant index with the internal `closeIndex()`, and
  check closing on applications of their own (the constructor is internal). See section 8.
- Q18 (2026-11-09, while reviewing PR 1): a Bounded Context keeps no tenant index. The code
  that needs the index asks `Application.instance().tenantIndex()`, so it always gets the
  current one. `BoundedContext` loses its index and `InternalAccess.tenantIndex()`, and
  `BusBuilder` its tenant-index API. See section 8.
- Q19 (2026-11-09, while reviewing PR 1): `CommandBus` stops registering the tenants of the
  commands in PR 1, not PR 4. Nothing reads the index before the gate of PR 4, so no request
  is served differently, and no test relied on it. Until PR 4, only the application
  registers tenants. See section 3.

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
| [`TenantIndex`](../../server/src/main/java/io/spine/server/tenant/TenantIndex.java) | The SPI: `keep`, `all`, `close`; factories `defaultMultitenant()`, `singleTenant()`. |
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

    // `close()`, `defaultMultitenant()`, `singleTenant()` stay as they are.
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

### 2. `UnregisteredTenantPolicy` — opt-in automatic registration

`REJECT` is the default in every environment, `Tests` included (decision Q3). Tests and
production behave the same, and most tests are unaffected anyway: in-process posts are not
checked (decision Q2). `REGISTER` is an explicit opt-in for applications that rely on
automatic registration while they migrate, and for test suites that post through the gRPC
services with generated tenant IDs.

```kotlin
package io.spine.server.tenant

/**
 * Tells how a multitenant Bounded Context treats a request from a tenant
 * that its [TenantIndex] does not contain.
 */
public enum class UnregisteredTenantPolicy {

    /**
     * Rejects the request with [ErrorCase.TENANT_ERROR][io.spine.core.ErrorCase.TENANT_ERROR].
     *
     * A command is answered with an `Ack` that carries the error case. A query or
     * a subscription request fails with the `INVALID_ARGUMENT` gRPC status.
     * The request is recorded by [Application].
     *
     * This is the default in all environments.
     */
    REJECT,

    /**
     * Registers the tenant in the index and serves the request.
     *
     * Use it for an application that relies on automatic registration while it moves
     * to its own onboarding, or for tests that send requests through the gRPC services
     * on behalf of newly generated tenants.
     */
    REGISTER
}
```

The policy is set on `Application` (section 8), not on `ServerEnvironment`: it is about
tenants, not about the technologies the server runs on. An application that needs different
policies in different environments chooses one itself, with `Environment.instance().is(...)`.

```kotlin
// An existing production app that needs time to migrate its onboarding.
Application.instance().use(UnregisteredTenantPolicy.REGISTER)

// A test suite that posts through the services with generated tenants, as before.
@BeforeAll
fun registerTenantsOnFirstRequest() {
    Application.instance().use(UnregisteredTenantPolicy.REGISTER)
}

@AfterAll
fun restoreDefaultPolicy() {
    Application.instance().use(UnregisteredTenantPolicy.REJECT)
}
```

The release notes point both kinds of users to these lines.

### 3. One gate at the service boundary

A single gate checks the tenant of every request that comes through `CommandService`,
`QueryService`, and `SubscriptionService` (`subscribe`, `activate`, `cancel`). It runs in
`ServiceDelegate`, after the context is found and before the request reaches the context.
The delegates already tell the tenant of a request: `tenantOf()` was added by #1713.

The gate makes two checks:

- **Shape:** a tenant is given if, and only if, the context is multitenant. Only for queries
  and subscriptions. The `TenantFilter` of the `CommandBus` checks commands, because
  in-process posts must be checked too; its answer does not change. A refused request is
  recorded in the System context of the serving context, as `TenantFilter` does for commands.
- **Membership:** the tenant is registered in the index of a multitenant context. For all
  three services. With `REGISTER`, the tenant is registered and the request is served. With
  `REJECT`, the request is refused and recorded by `Application` (section 10).

```java
// ServiceDelegate
private void serveIn(BoundedContext context,
                     T request,
                     StreamObserver<R> observer,
                     @Nullable Object params) {
    if (!admitted(context, request, observer)) {
        return;
    }
    try {
        serve(context, request, observer, params);
    } catch (RuntimeException e) {
        // As in #1713: `failureEvent()` is recorded in the context, `INTERNAL_ERROR`.
    }
}

/**
 * Checks the tenant of the request, and refuses a request that does not pass.
 *
 * @return {@code true} if the request may be served
 */
private boolean admitted(BoundedContext context, T request, StreamObserver<R> observer) {
    var tenant = tenantOf(request);
    try {
        var misfit = tenantMisfit(context, request, tenant);
        if (misfit != null) {
            reject(context, request, misfit, TENANT_ERROR, observer);
            return false;
        }
        if (!TenantGate.admits(context, tenant)) {
            Application.instance().record(tenantNotRegistered(context, request, tenant),
                                          validOriginOf(request));
            refuse(request, TENANT_ERROR, observer, "The tenant is not registered.");
            return false;
        }
        return true;
    } catch (RuntimeException e) {
        // The index cannot answer. Recorded by `Application`, not under the tenant.
        Application.instance().record(failureEvent(request, Errors.fromThrowable(e)),
                                      validOriginOf(request));
        respondWithError(request, INTERNAL_ERROR, observer);
        return false;
    }
}

/**
 * Creates the event that records a request whose tenant does not fit the context.
 *
 * @return the event, or {@code null} if the tenant fits the context, or if the tenant of
 *         such requests is checked elsewhere
 */
protected abstract @Nullable EventMessage tenantMisfit(BoundedContext context,
                                                       T request,
                                                       TenantId tenant);

/**
 * Creates the event that records a request of a tenant that is not registered.
 */
protected abstract EventMessage tenantNotRegistered(BoundedContext context,
                                                    T request,
                                                    TenantId tenant);
```

`CommandService` returns `null` from `tenantMisfit()`, because `TenantFilter` does that check.

```kotlin
package io.spine.server.tenant

/**
 * Admits requests to a Bounded Context depending on their tenant.
 */
internal object TenantGate {

    /**
     * Tells if a request of the given tenant may reach the context.
     *
     * Always `true` for a single-tenant context or a request without a tenant:
     * the shape of the tenant is checked separately.
     *
     * Registers the tenant if it is not registered and the policy of [Application]
     * is [REGISTER][UnregisteredTenantPolicy.REGISTER].
     */
    @JvmStatic
    fun admits(context: BoundedContext, tenant: TenantId): Boolean {
        if (!context.isMultitenant || tenant.isDefault()) {
            return true
        }
        val index = Application.instance().tenantIndex()
        if (index.contains(tenant)) {
            return true
        }
        return when (Application.instance().unregisteredTenantPolicy()) {
            UnregisteredTenantPolicy.REGISTER -> {
                index.keep(tenant)
                true
            }
            UnregisteredTenantPolicy.REJECT -> false
        }
    }
}
```

`CommandBus` no longer registers tenants since PR 1 (Q19): `CommandBus.store()` does nothing,
because the bus does not store commands.

Why the boundary and not the buses:

- Only requests from outside the process are untrusted. Signals produced inside
  (commands from process managers, integration events, scheduled commands) descend from
  a request that was already admitted.
- With per-context indexes (possible until Q15), a bus-level gate would
  break legitimate flows. For example, context A admits tenant T; A's event reaches context B;
  B's process manager posts a command for T, and B's index has never seen T.
- In-process posting (`context.commandBus().post(...)`, `BlackBox.receivesCommand`) keeps
  working unchanged, so most tests are unaffected.

Open for PR 4: `SubscriptionService` subscribes a topic that no context serves in every
context (`serveNoContext`). Such a topic has no serving context to check against. It can be
checked against the index of `Application` and its policy, which every multitenant context
uses.

### 4. What the client gets, and what is recorded

Every tenant problem is answered with `ErrorCase.TENANT_ERROR` (Q11). Read failures use
`Statuses.failure(ErrorCase)` from #1713: the gRPC status has no description, and
`grpc-status-details-bin` carries the error case. There is no new trailer, no
`google.rpc.ErrorInfo`, no `NOT_FOUND`, and no `UNAVAILABLE`.

| Problem | Commands | Queries and subscriptions | Recorded |
|---|---|---|---|
| No tenant, multitenant context | `Ack` with `TENANT_ERROR`, from `TenantFilter` (as today) | `INVALID_ARGUMENT`, `TENANT_ERROR` | In the serving context: `CommandTenantNotSpecified` (exists), `QueryTenantNotSpecified`, `TopicTenantNotSpecified`, `SubscriptionTenantNotSpecified` |
| A tenant, single-tenant context | `Ack` with `TENANT_ERROR`, from `TenantFilter` (as today) | `INVALID_ARGUMENT`, `TENANT_ERROR` | In the serving context: `CommandTenantNotExpected` (exists), `QueryTenantNotExpected`, `TopicTenantNotExpected`, `SubscriptionTenantNotExpected` |
| The tenant is not registered (`REJECT`) | `Ack` with `TENANT_ERROR`, from `CommandService` | `INVALID_ARGUMENT`, `TENANT_ERROR` | By `Application`: `CommandTenantNotRegistered`, `QueryTenantNotRegistered`, `TopicTenantNotRegistered`, `SubscriptionTenantNotRegistered` |
| The index cannot answer | The call fails with `INTERNAL_ERROR`, like other unexpected failures | `INTERNAL`, `INTERNAL_ERROR` | By `Application`: `CommandPostingFailed`, `QueryExecutionFailed`, `SubscriptionServiceFailed` |

`SubscriptionTenant*` events record the activation and the cancellation of a subscription.
The events of a topic are `TopicTenant*`.

The new events follow the rules of #1713:

- They go to a new `spine/system/server/tenant_events.proto`, with `(internal_all)` and
  `java_package = "io.spine.system.server.event"`. `CommandTenantNotSpecified` and
  `CommandTenantNotExpected` stay in `command_log_events.proto`, since `CommandLog`
  subscribes to them.
- The ID of the request is the first field. It is not `(required)`: the gate runs before the
  request is validated, so the request may have no ID. The field doc says so.
- The read events carry the `core.ActorContext actor_context` of the request, like the other
  read events of #1713.
- `*TenantNotExpected` and `*TenantNotRegistered` carry `core.TenantId tenant`
  `[(required) = true]`. `*TenantNotRegistered` also carries
  `core.BoundedContextName context` `[(required) = true]`, because the record of
  `Application` is shared by all contexts.

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
  `CommandTenantNotRegistered` goes to the record of `Application` (section 10).
- spine-ts: Q8 kept `NOT_FOUND` there under the old wire contract. spine-ts adopts
  `ErrorCase` as a follow-up of the rework, and revisits Q8 then.

### 6. Removing `CommandValidationError`

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

`MtBlackBox.withTenant()` registers the tenant. The test author says the tenant exists,
so this matches the meaning of the call. It also keeps `BlackBox.clients()` working under
the `REJECT` default, since those clients go through an in-process `GrpcContainer`.

```java
@Override
@CanIgnoreReturnValue
public MtBlackBox withTenant(TenantId tenant) {
    this.tenantId = checkNotNull(tenant);
    Application.instance()
               .tenantIndex()
               .keep(tenant);
    return this;
}
```

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
        tenantIndex ?: TenantIndex.defaultMultitenant().also { tenantIndex = it }

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
  - `Application` holds the record of the open contexts and of the entity state types they
    serve: `OpenContexts`, an internal class now, no longer a JVM-wide object.
  - `close()` closes the open contexts, in the reverse order of their building, and then the
    tenant index. It closes the application for good, as the contract of
    `io.spine.server.Closeable` requires: no context can be built afterwards, and
    `tenantIndex()` and `use()` throw.
  - There is no `reset()`. The internal `closeIndex()` closes only the tenant index, for tests.
    The constructor is internal, so that tests check closing on applications of their own.

### 9. `TenantAdded` — contexts prepare for a new tenant

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

### 10. The record of `Application`

Decided by Q12–Q14. Some refused requests have no context to record them in:

- a request that no Bounded Context serves, or of a type internal or unknown to the server
  (`UNSUPPORTED_REQUEST`; since #1713 it is only logged);
- a request of an unregistered tenant (`TENANT_ERROR`, section 4): the serving context would
  store it under a tenant that does not exist;
- a failure of the index to answer (`INTERNAL_ERROR`, section 4), for the same reason.

`Application` records them in an `EventStore` of its own:

- **Off by default.** `Application.instance().persistEvents()` turns storing on, and
  `forgetEvents()` turns it off, as in `SystemSettings`. Until storing is on, a refusal is only
  logged by `ServiceDelegate`, as today, and the store is not created.
- **Single-tenant.** The store is created with the storage factory of `ServerEnvironment`, for
  the reserved single-tenant spec `ContextSpec.singleTenant("__APPLICATION__")`. `Application`
  refuses this name for a Bounded Context. The tenant of a request stays in its event: in the
  `tenant` field, in the `actor_context` of a read event, and in the origin of a command event.
- **Apart from the events of the contexts.** `DefaultEventStore` groups its storage by the
  context name (`StorageGroup.of(BoundedContextName)`). Once `jdbc-storage` and `gcloud-jvm`
  honor the group (`event-store-context-prefix.md`), the record has its own table or kind.
- **No bus, no context.** The events are built the way the System contexts build theirs (the
  producer is the first field of the message) and appended with `EventStore.append()`. Nothing
  subscribes to them. Operations tooling reads them from the storage; tests read them through a
  `@VisibleForTesting` accessor.
- **Events.** A request that no context serves is recorded with the types the contexts use for
  an unsupported request: `UnsupportedCommandReceived`, `UnsupportedQueryReceived`,
  `UnsupportedTopicReceived`, `UnknownSubscriptionActivationRequested`, and
  `UnknownSubscriptionCancellationRequested`. `UnsupportedCommandReceived.id` loses
  `(required)`, because such a command is not validated. Their docs say that `Application`
  records them too. Tenant problems use the events of section 4. A request whose type cannot be
  read (`INVALID_REQUEST`) is only logged: nothing tells which kind of request it is.
- **Closing.** `Application.close()` closes the store together with the tenant index, before
  `ServerEnvironment` closes the storage factory.

```kotlin
// Application

/**
 * Tells the application to store the requests that are refused before they reach
 * a Bounded Context.
 */
@CanIgnoreReturnValue
public fun persistEvents(): Application

/**
 * Tells the application not to store the refused requests. This is the default.
 */
@CanIgnoreReturnValue
public fun forgetEvents(): Application

/**
 * Records the event of a refused request, if storing is on.
 */
@Internal
public fun record(event: EventMessage, origin: Origin)
```

`record()` is public with `@Internal`, not Kotlin `internal`: the services that call it are
Java classes.

## Expected impact on existing tests

- In-process posting does not go through the gate, so most tests that use generated
  tenant IDs keep passing under the `REJECT` default.
- Tests that post through the services with generated tenants must register them first:
  `CommandServiceTest`, `CommandServiceTenancyTest`, `CommandServiceMultiTenancySpec`, and the
  multitenant cases of `QueryServiceTest`, `SubscriptionServiceTest`, and the
  `*ErrorCaseSpec` classes. To be confirmed by a run in PR 4.
- The shape checks of queries and subscriptions are new. A query or a subscription without
  a tenant in a multitenant context was served from the `SINGLE_TENANT` partition; now it is
  refused with `TENANT_ERROR`. Tests that rely on it are found by the run in PR 4.
- The `BlackBox.clients()` tests pass because `withTenant()` registers.
- No test asserts that posting a command registers a tenant. `ProjectionRepositoryTest`
  already calls `keep` itself.
- All multitenant contexts of a test JVM share the in-memory index of `Application`, and
  `TenantAdded` goes to every open multitenant context. No context in the tests handles
  `TenantAdded`, and it travels through the System contexts, not the domain `EventBus` that
  `BlackBox.assertEvents()` watches, so existing assertions are not affected.
- Tests that set a policy or turn the record on set the defaults back when they finish:
  `use(UnregisteredTenantPolicy.REJECT)`, `forgetEvents()`.
- New specs: the gate for each service and each row of the table in section 4, with the
  event and where it is recorded; `contains` in `TenantStorage` (cache hit, point read, no
  negative caching); the record of `Application` (off by default and not created; when on,
  each kind of refused request is stored with its tenant; `close()`); no
  `CommandReceived` for a command of an unregistered tenant.

## Implementation plan

Four PRs in core-jvm, in this order, stacked on #1713 until it is merged. Each one bumps the
version (`bump-version`), regenerates the dependency reports with a full build, and passes
`./gradlew clean build dokkaGenerate`. After every test run, read the test count, not only
the exit code.

1. **Tenant index for the application.** No change in how requests are served.
   - `TenantIndex.contains()` (default method) and `TenantStorage.contains()` (section 1).
   - `Application` (Kotlin, section 8): `tenantIndex()`, `use(TenantIndex)`, `close()`, and
     the internal `closeIndex()`. `OpenContexts` becomes a part of `Application` (Q16).
   - Bounded Contexts keep no tenant index (Q18), and contexts cannot be given one (Q15):
     `BoundedContextBuilder` loses `setTenantIndex()`, `hasTenantIndex()`, and
     `getTenantIndex()`; `BoundedContext` its index and `InternalAccess.tenantIndex()`;
     `BusBuilder` its tenant-index API.
   - `CommandBus` no longer registers the tenants of the commands (Q19).
   - Specs: `contains` (cache hit, point read, no negative caching); `Application` (lazy
     default, `use` after first use throws, `close` closes the contexts and then the index,
     `closeIndex`); a multitenant `CommandBus` does not register the tenant of a posted
     command.
2. **The record of `Application`.** Records the requests that no context serves. Independent
   of PR 1, except for `Application` itself.
   - `persistEvents()`, `forgetEvents()`, the lazily created `EventStore`, and `record()`
     (section 10).
   - `ServiceDelegate` records the requests that no context serves, and those of a type
     internal or unknown to the server, by `Application`. `UnsupportedCommandReceived.id` loses
     `(required)`; the docs of the reused events say where they are recorded.
   - `CommandValidationError` is removed (section 6).
   - Specs: off by default, nothing created; when on, each kind of request that no context
     serves is stored with its tenant; `close()`.
3. **`TenantAdded`.** Needs PR 1.
   - `AnnouncingTenantIndex`, returned by `Application.tenantIndex()` (section 9). Posting
     through the System write side of each open multitenant context, under the new tenant.
   - The documentation of `TenantAdded`: a notification, may arrive after the first request,
     may arrive twice, reaches the contexts of the node that added the tenant.
   - Specs: a reactor or process manager in each of two contexts receives `TenantAdded` once
     per new tenant, and not for a tenant that is already in the index.
4. **Reject unregistered tenants.** The behavior change. Needs PRs 1–3.
   - `UnregisteredTenantPolicy` in `Application` (section 2); `TenantGate` and the gate in
     `ServiceDelegate` (section 3).
   - The events of section 4 in `tenant_events.proto`. The shape checks of queries and
     subscriptions are recorded in the serving context; `*TenantNotRegistered` and the
     failures of the index, by `Application`.
   - `MtBlackBox.withTenant()` registers (section 7).
   - Tests: register tenants where the services are called with generated tenants; new specs
     from "Expected impact on existing tests".
   - Check test isolation again: one case of `ApplicationSpec` (PR 1) closes the tenant index
     of the `Application` of the test JVM, so the contexts that earlier tests left open hold
     a closed index afterwards. No test uses a context across test classes today; with the
     gate, such a context would fail on every request.
   - Documentation: the Javadoc of `CommandService.post()` and of the query and subscription
     services; release notes on `REJECT`, how to turn on `REGISTER`, the new shape checks of
     queries and subscriptions, and `Application.persistEvents()`.

Follow-ups in other repositories, once PR 2 (for the removed enum) or PR 4 (for the behavior)
is merged:

- **spine-ts:** drop `CommandValidationError` together with the enums that #1713 removed, when
  it adopts `ErrorCase`; revisit how it answers a command of an unregistered tenant (Q8).
- **gcloud-jvm:** set the index of `Application` instead of calling the removed
  `setTenantIndex()` in `DatastoreStorageFactory.configureTenantIndex()`; override `contains`
  in `NamespaceIndex`; make registration durable, so that other nodes do not reject a tenant
  until it has data (item 5 below); honor the storage group of the event store, so that the
  record of `Application` gets its own kind.
- **jdbc-storage:** honor the storage group of the event store, so that the record of
  `Application` gets its own table.

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
14. **Floods of refused requests.** `ServiceDelegate` logs every refusal at `WARNING`, with no
    rate limit, as it does for the other refusals since #1713. With the record of `Application`
    on, every refusal is also stored. An application open to anonymous callers should
    authenticate them first (item 6).

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
      `BusBuilder` until PR 4.
19. ~~Stop `CommandBus` registering tenants in PR 1, or in PR 4 with the gate?~~ Decided: in PR 1.
    Nothing read the index yet, and only the spec added by PR 1 checked the registration.
