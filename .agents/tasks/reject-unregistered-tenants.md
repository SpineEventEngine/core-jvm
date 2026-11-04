# Reject requests from unregistered tenants (`TENANT_NOT_REGISTERED`)

## Status

**Parked.** The design is settled (questions 1–10, decided on 2026-11-04), and no code is
changed. The work waits for the rework of how errors are reported to clients:
`spine.core.Status` is going to tell the client side less than it does now. That change is
planned separately. Before the implementation starts, revisit the parts of this design that
report errors against the new `Status`: the rejections of the gate (section 3), the wire
contract (section 4), the command `Ack` (section 5), and the alias (section 6). The other
parts (sections 1, 2, and 7–9) do not depend on it.

To confirm when the work resumes (filled in while recording the decisions):

- `UnregisteredTenantPolicy` lives in `Application`, not in `ServerEnvironment` (from Q5).
- `TenantError.Reason` names a missing tenant `TENANT_NOT_SPECIFIED`, and the policy type is
  named `UnregisteredTenantPolicy` (from Q6).
- `TenantError` has the fields `reason`, `tenant`, `context`, and is packed into
  `Error.details` (from Q4).

Decisions:

- Q1 (2026-11-04): for commands, an unregistered tenant is reported in an error `Ack`
  (option B), like other command validation errors. See section 5.
- Q2 (2026-11-04): the tenant is checked only at the service boundary, not in the buses.
  See section 3. A bus-level check may come later, together with an application-wide index
  and tenant removal.
- Q3 (2026-11-04): `REJECT` is the default in all environments, `Tests` included.
  `MtBlackBox.withTenant()` registers its tenant; `BlackBoxClients.create(tenant)` does not.
  See sections 2 and 7.
- Q4 (2026-11-04): tenant errors use a Protobuf enum nested in a message,
  `spine.core.TenantError.Reason` (option B2). `Error.type` is the enum's full name and
  `Error.code` its number, as `spine.base.Error` documents. See section 4.
- Q5 (2026-11-04): one tenant index for the whole application, in this change. It is held
  by a new `Application` singleton, not by `ServerEnvironment`: `ServerEnvironment` is about
  the technologies that implement the server side, not about domain things like tenants.
  See section 8. Following the same principle, `UnregisteredTenantPolicy` moves from
  `ServerEnvironment` to `Application` too (to confirm, see above).
- Q6 (2026-11-04): the value for a tenant that is not in the index is
  `TenantError.Reason.TENANT_NOT_REGISTERED`. `CommandValidationError` gets the alias
  `TENANT_NOT_SPECIFIED` for value 3, used instead of the ambiguous `TENANT_UNKNOWN`, which is
  deprecated. Commands keep reporting a missing or inapplicable tenant with
  `CommandValidationError`. See sections 4 and 6. Following the same reasoning (to confirm,
  see above): `TenantError.Reason` names a missing tenant
  `TENANT_NOT_SPECIFIED`, and the policy type is named `UnregisteredTenantPolicy`.
- Q7 (2026-11-04): user-facing text is the clients' job. The server sends only the developer
  message, and clients map the `TenantError.Reason` value to their own text.
- Q8 (2026-11-04): spine-ts keeps failing the RPC with `NOT_FOUND` for a command of an
  unregistered tenant. The difference from the JVM (error `Ack`) is documented on both sides.
  See section 5.
- Q9 (2026-11-04): `TenantAdded` stays, and is posted when a tenant is added, so that the
  Bounded Contexts can prepare their internals for the new tenant's requests. See section 9.
- Q10 (2026-11-04): `TenantAdded` is a notification. The contexts may finish preparing after
  the tenant's first request; handlers are idempotent. A synchronous preparation step can be
  added later, when a storage needs per-tenant setup. See section 9.

## Problem

A multitenant Bounded Context adds a tenant the first time a command arrives with a
`TenantId` it has not seen. Real applications cannot add a tenant this way: adding one
involves payment details, storage provisioning, and other onboarding steps that the
application performs on its own. A request from an unregistered tenant must be rejected.

## Findings

### The user report is confirmed

The path of a command from a gRPC client:

1. `CommandService.post` → `ServiceDelegate.serve`
   ([ServiceDelegate.java:79](../../server/src/main/java/io/spine/server/ServiceDelegate.java))
   finds the context by the command type.
2. `CommandBus.post` → `Bus.filterAndPost`
   ([Bus.java:189](../../server/src/main/java/io/spine/server/bus/Bus.java)) runs the filters.
   `CommandValidator.isTenantIdValid`
   ([CommandValidator.java:79](../../server/src/main/java/io/spine/server/commandbus/CommandValidator.java))
   checks only that a tenant is *present* in a multitenant context and *absent* in a
   single-tenant one.
3. Every command that passes the filters goes to `CommandBus.store`
   ([CommandBus.java:256](../../server/src/main/java/io/spine/server/commandbus/CommandBus.java)),
   which calls `tenantConsumer`, wired as `tenantIndex::keep`
   ([CommandBus.java:343](../../server/src/main/java/io/spine/server/commandbus/CommandBus.java)).
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
- **Error payloads are not platform-neutral today.** For commands, `Error.type` is either a
  Java class name (`io.spine.server.commandbus.InvalidCommandException`, for tenant errors)
  or the full name of the enum (`spine.core.CommandValidationError`, for unsupported
  commands); `Error.code` is a `CommandValidationError` number. For queries `Error.type` is
  the simple name of the enum (`QueryValidationError`). `serveNoContext` in `QueryService`
  ([QueryService.java:117](../../server/src/main/java/io/spine/server/QueryService.java))
  passes a raw Java exception to `onError`, so the client receives `UNKNOWN` with no details.
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
| [`BoundedContextBuilder`](../../server/src/main/java/io/spine/server/BoundedContextBuilder.java) | `setTenantIndex` (:253), `initTenantIndex` (:708) creates the default per context; passes the index to the System context (:679) and to `CommandBus` (:719). |
| [`BoundedContext`](../../server/src/main/java/io/spine/server/BoundedContext.java) | Holds the index (:117), closes it (:587), exposes it via `InternalAccess.tenantIndex()` (:812). |
| [`BusBuilder`](../../server/src/main/java/io/spine/server/bus/BusBuilder.java) | `injectTenantIndex` (:184), `ensureTenantIndex` (:193). |
| [`CommandBus`](../../server/src/main/java/io/spine/server/commandbus/CommandBus.java) | **The only writer:** `store` → `tenantIndex::keep` (:256, :343). |
| [`CommandValidator`](../../server/src/main/java/io/spine/server/commandbus/CommandValidator.java), [`InvalidCommandException`](../../server/src/main/java/io/spine/server/commandbus/InvalidCommandException.java) | Tenant *presence* checks and their `Error`s. |
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
     * Rejects the request with the `TENANT_NOT_REGISTERED` error.
     *
     * A command is answered with an error `Ack`. A query or a subscription request
     * fails with the `NOT_FOUND` gRPC status.
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
fun resetApplication() {
    Application.instance().reset()
}
```

The release notes point both kinds of users to these lines.

### 3. One gate at the service boundary

A single `TenantGate` checks the tenant of every request that comes through
`CommandService`, `QueryService`, and `SubscriptionService` (`subscribe`, `activate`,
`cancel`). It runs in `ServiceDelegate.serve`, after the context is found and before the
request reaches the context.

```java
// ServiceDelegate
final void serve(T request, StreamObserver<R> observer, @Nullable Object params) {
    if (detectInternal(request)) {
        handleInternal(request, observer);
        return;
    }
    var type = enclosedMessageType(request);
    var found = find(type);
    if (found.isEmpty()) {
        serveNoContext(request, observer, params);
        return;
    }
    var context = found.get();
    var rejection = admit(context, tenantOf(request));
    if (rejection != null) {
        rejectTenant(request, rejection, observer);
        return;
    }
    serve(context, request, observer, params);
}

/**
 * Obtains the tenant on whose behalf the request is made.
 */
protected abstract TenantId tenantOf(T request);

/**
 * Checks the tenant of the request.
 *
 * <p>By default, checks that the tenant is given if, and only if, the context is
 * multitenant, and that the tenant is registered.
 *
 * @return the rejection, or {@code null} if the request may proceed
 */
protected @Nullable TenantRejection admit(BoundedContext context, TenantId tenant) {
    var shape = TenantGate.checkShape(context, tenant);
    return shape != null
           ? shape
           : TenantGate.checkMembership(context, tenant);
}

/**
 * Reports a request rejected because of its tenant.
 *
 * <p>By default, fails the call with the status of the rejection.
 */
protected void rejectTenant(T request, TenantRejection rejection, StreamObserver<R> observer) {
    observer.onError(rejection.asStatusException());
}
```

```kotlin
package io.spine.server.tenant

/**
 * Admits requests to a Bounded Context depending on their tenant.
 */
internal object TenantGate {

    /**
     * Checks that a tenant is given if, and only if, the context is multitenant.
     *
     * @return the rejection, or `null` if the tenant fits the context.
     */
    @JvmStatic
    fun checkShape(context: BoundedContext, tenant: TenantId): TenantRejection? {
        val specified = tenant != TenantId.getDefaultInstance()
        return when {
            context.isMultitenant && !specified -> TenantRejection.notSpecified(context)
            !context.isMultitenant && specified -> TenantRejection.inapplicable(context, tenant)
            else -> null
        }
    }

    /**
     * Checks that the tenant is registered in the index of a multitenant context.
     *
     * Does nothing for a single-tenant context or a request without a tenant.
     *
     * @return the rejection, or `null` if the tenant is known or was just registered.
     */
    @JvmStatic
    fun checkMembership(context: BoundedContext, tenant: TenantId): TenantRejection? {
        if (!context.isMultitenant || tenant == TenantId.getDefaultInstance()) {
            return null
        }
        val index = context.internalAccess().tenantIndex()
        return if (admits(index, tenant)) null else TenantRejection.notRegistered(context, tenant)
    }

    private fun admits(index: TenantIndex, tenant: TenantId): Boolean {
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

`CommandBus` stops registering tenants:

```java
@Override
protected void store(Iterable<Command> commands) {
    // Commands are not stored, and tenants are admitted by the services.
}
```

`tenantConsumer`, and with it `BusBuilder.injectTenantIndex()`, become unnecessary and
can be deprecated.

Why the boundary and not the buses:

- Only requests from outside the process are untrusted. Signals produced inside
  (commands from process managers, integration events, scheduled commands) descend from
  a request that was already admitted.
- With per-context indexes (the in-memory default before Q5, and still possible with
  `setTenantIndex()`), a bus-level gate would break legitimate flows. For example, context A admits tenant T; A's event reaches context B;
  B's process manager posts a command for T, and B's index has never seen T.
- In-process posting (`context.commandBus().post(...)`, `BlackBox.receivesCommand`) keeps
  working unchanged, so most tests are unaffected.

Missing and inapplicable tenants for commands stay in `CommandValidator`, because
in-process posts must be checked too, and the existing `Ack` contract for them does not
change. So `CommandService` checks only membership, and answers with an error `Ack`
(decision Q1):

```java
// CommandService.CommandServiceImpl
/**
 * {@inheritDoc}
 *
 * <p>A missing or inapplicable tenant is reported by the {@code CommandBus}
 * in the {@code Ack}, as before.
 */
@Override
protected @Nullable TenantRejection admit(BoundedContext context, TenantId tenant) {
    return TenantGate.checkMembership(context, tenant);
}

/**
 * Responds with the {@code Ack} that carries the error of the rejection,
 * like for other commands that cannot be accepted.
 */
@Override
protected void rejectTenant(Command cmd,
                            TenantRejection rejection,
                            StreamObserver<Ack> observer) {
    respondWithError(cmd, rejection.asError(), observer);
}
```

For queries and subscriptions the gate adds the shape checks, which are missing today.

### 4. Wire contract

Tenant errors are described by a new Protobuf type in `spine.core` (decision Q4, option
B2). The enum is nested, so its values do not clash with `CommandValidationError` in the same
package. The enclosing message carries the structured details of the rejection:

```proto
// spine/core/tenant_error.proto, package `spine.core`.

import "spine/core/tenant_id.proto";
import "spine/core/bounded_context.proto";

// Describes a request rejected because of the tenant on whose behalf it is made.
//
// Packed into `spine.base.Error.details`.
//
message TenantError {

    // Why the request was rejected.
    Reason reason = 1 [(required) = true];

    // The tenant given in the request, if any.
    TenantId tenant = 2;

    // The Bounded Context that rejected the request.
    BoundedContextName context = 3 [(required) = true];

    // Reasons for rejecting a request because of its tenant.
    enum Reason {

        // Reserved value.
        TE_UNKNOWN_REASON = 0;

        // The tenant is not registered in the application.
        TENANT_NOT_REGISTERED = 1;

        // The request has no tenant, but the Bounded Context is multitenant.
        TENANT_NOT_SPECIFIED = 2;

        // The request has a tenant, but the Bounded Context is single-tenant.
        TENANT_INAPPLICABLE = 3;
    }
}
```

The fields of the message are an addition made after the decision; the message could also
stay empty and serve only as the scope of the enum.

`TENANT_NOT_REGISTERED` is the name chosen in Q6. The value for a missing tenant is named
`TENANT_NOT_SPECIFIED`, like the new `CommandValidationError` alias (section 6): the same name
for the same meaning. The nested scope keeps the two apart; protoc 4.36.2 compiles both.

Every tenant rejection is reported with the same `spine.base.Error`:

- `type` — `spine.core.TenantError.Reason`, the full name of the enum;
- `code` — the number of the `Reason` value;
- `message` — a developer-facing description;
- `details` — the packed `TenantError`.

| Case | How it reaches the client | `Error.code` | `google.rpc.ErrorInfo.reason` |
|---|---|---|---|
| Tenant not in the index (queries, subscriptions) | `NOT_FOUND` | `TENANT_NOT_REGISTERED` (1) | `TENANT_NOT_REGISTERED` |
| Tenant not in the index (commands) | `Ack.status.error` | `TENANT_NOT_REGISTERED` (1) | — |
| No tenant, multitenant context (queries, subscriptions) | `INVALID_ARGUMENT` | `TENANT_NOT_SPECIFIED` (2) | `TENANT_NOT_SPECIFIED` |
| Tenant given, single-tenant context (queries, subscriptions) | `INVALID_ARGUMENT` | `TENANT_INAPPLICABLE` (3) | `TENANT_INAPPLICABLE` |
| The index cannot answer (all services) | `UNAVAILABLE` | — | — |

A missing or inapplicable tenant of a command is still reported by `CommandValidator` in the
`Ack`, with `CommandValidationError` codes (section 6).

`UNAVAILABLE` fails the RPC for commands too: an `Ack` would claim that the server made a
decision about the command. Java clients already have to handle `UNAVAILABLE`, since the
transport returns it when the server is down.

For queries and subscriptions, the rejection is sent in two trailers:

- `spine-error-bin` — `spine.base.Error`, as `MetadataConverter` does today and spine-ts
  does too;
- `grpc-status-details-bin` — `google.rpc.Status` with `spine.base.Error` and
  `google.rpc.ErrorInfo` in `details`. `ErrorInfo.reason` is the name of the `Reason`
  value, for gRPC clients that do not use the Spine types. This is the standard gRPC
  trailer for rich errors: grpc-java (`StatusProto.fromThrowable`) and Connect
  (`ConnectError.findDetails`) decode it directly; other gRPC libraries expose it as
  a trailer with well-known types.

`grpc-protobuf` 1.85.0 and `proto-google-common-protos` 2.64.1 are already on the classpath,
so no new dependency is needed.

```kotlin
package io.spine.server.tenant

/**
 * A request rejected because of its tenant.
 */
internal class TenantRejection private constructor(
    private val details: TenantError,
    private val status: Status.Code,
    private val message: String,
) {

    /**
     * Converts this rejection to the platform-neutral `Error`.
     */
    fun asError(): Error = Error.newBuilder()
        .setType(TenantError.Reason.getDescriptor().fullName)
        .setCode(details.reason.number)
        .setMessage(message)
        .setDetails(AnyPacker.pack(details))
        .build()

    /**
     * Creates an exception to fail a query or subscription RPC with.
     */
    fun asStatusException(): StatusRuntimeException {
        val error = asError()
        val info = ErrorInfo.newBuilder()
            .setReason(details.reason.name)
            .setDomain("spine.io")
            .putMetadata("bounded_context", details.context.value)
            .build()
        val status = com.google.rpc.Status.newBuilder()
            .setCode(this.status.value())
            .setMessage(message)
            .addDetails(AnyPacker.pack(error))
            .addDetails(Any.pack(info))
            .build()
        val trailers = MetadataConverter.toMetadata(error)
        return StatusProto.toStatusRuntimeException(status, trailers)
    }

    companion object {

        fun notRegistered(context: BoundedContext, tenant: TenantId): TenantRejection =
            TenantRejection(
                details(TENANT_NOT_REGISTERED, context, tenant),
                Status.Code.NOT_FOUND,
                "The tenant `${shortDebugString(tenant)}` is not registered" +
                        " in the context `${context.name().value}`."
            )

        // `notSpecified(...)` and `inapplicable(...)` follow the same pattern
        // with `INVALID_ARGUMENT`.

        private fun details(
            reason: TenantError.Reason,
            context: BoundedContext,
            tenant: TenantId
        ): TenantError = TenantError.newBuilder()
            .setReason(reason)
            .setTenant(tenant)
            .setContext(context.name())
            .build()
    }
}
```

The message is for developers. It contains only the tenant ID that the caller sent.

### 5. Commands: an unregistered tenant is reported in the `Ack`

Decided (Q1, option B). `CommandService` overrides `rejectTenant` (section 3) to send
`Ack { message_id, status.error }`, like other command validation errors.

- **The Java client needs no change.** `CommandRequest.PostOperation.perform()`
  ([CommandRequest.java:222](../../client/src/main/java/io/spine/client/CommandRequest.java))
  already passes an error `Ack` to `ServerErrorHandler` and cancels the event
  subscriptions it made before posting.
- **Nothing is recorded for the unregistered tenant.** The `Ack` is built by the service, so the
  command never enters `CommandBus`. The bus would post `CommandReceived` (from
  `CommandReceivedTap`, the first filter) and `CommandErrored` (from `CommandAckMonitor`)
  under the command's tenant. With the default `SystemSettings` these events are not stored,
  but an application that calls `persistEvents()` or `enableCommandLog()` would write
  records for a tenant that does not exist.
- **Known difference from spine-ts, kept on purpose (Q8).** A TypeScript client that talks to
  both kinds of servers has to look for the `TENANT_NOT_REGISTERED` error in two places for
  commands: the `Ack` (JVM) and the RPC error (spine-ts). It is documented on both sides:
  - core-jvm: the Javadoc of `CommandService.post()` and the doc comment of `TenantError` in
    `tenant_error.proto` say that a command of an unregistered tenant is answered with an
    error `Ack`, while queries and subscriptions fail with `NOT_FOUND`;
  - spine-ts: `client-web/REFERENCE.md` says that a JVM server answers such a command with an
    error `Ack`, and spine-ts with `NOT_FOUND`.

Option A, kept for the record: fail the RPC with `NOT_FOUND`. It needed a client fix,
because `perform()` subscribes before posting and expects an `Ack`; a thrown
`StatusRuntimeException` would skip `ServerErrorHandler` and leave the subscriptions open.

### 6. A clear name for the "tenant not specified" error

Decided (Q6). `CommandValidationError` gets the alias `TENANT_NOT_SPECIFIED`, and Spine code
uses it instead of the ambiguous `TENANT_UNKNOWN`. Commands keep reporting a missing or
inapplicable tenant with `CommandValidationError`, as before.

```proto
enum CommandValidationError {
    option allow_alias = true;

    // ...

    // The command context has no `tenant_id`, but the command is posted to
    // a multitenant Bounded Context.
    TENANT_NOT_SPECIFIED = 3;

    // Deprecated: use `TENANT_NOT_SPECIFIED`.
    //
    // The name is ambiguous: the tenant is not specified, rather than unknown.
    // A tenant that is specified but not registered is reported with
    // `TenantError.Reason.TENANT_NOT_REGISTERED`.
    //
    TENANT_UNKNOWN = 3 [deprecated = true];

    // ...
}
```

Checked with protoc 4.36.2 and `--java_out`:

- `TENANT_NOT_SPECIFIED` is the enum constant. `TENANT_UNKNOWN` becomes
  `public static final CommandValidationError TENANT_UNKNOWN = TENANT_NOT_SPECIFIED;`,
  so existing references compile and compare equal.
- protoc does not put `@Deprecated` on that field (only on the `int` constant
  `TENANT_UNKNOWN_VALUE`). Java and Kotlin callers get no deprecation warning, so the proto
  comment and the release notes have to carry the message.
- An alias is not an enum constant. `case TENANT_UNKNOWN:` in a Java `switch` and
  `CommandValidationError.valueOf("TENANT_UNKNOWN")` stop working, and `name()` returns
  `TENANT_NOT_SPECIFIED`. No code in the `~/Projects/Spine` repositories does any of this:
  the only uses are `InvalidCommandException` and `MultiTenantCommandBusTest`, which compare
  numbers.
- The wire format does not change (the number stays 3). No `.proto` field has the type
  `CommandValidationError` (`Error.code` is `int32`), so JSON is not affected either.

`InvalidCommandException` switches to `TENANT_NOT_SPECIFIED`. Its public
`unknownTenantError(...)` gets a clearer name, with the old one deprecated. The copy of
`command.proto` in spine-ts needs the same edit.

### 7. Test library

`MtBlackBox.withTenant()` registers the tenant. The test author says the tenant exists,
so this matches the meaning of the call. It also keeps `BlackBox.clients()` working under
the `REJECT` default, since those clients go through an in-process `GrpcContainer`.

```java
@Override
@CanIgnoreReturnValue
public MtBlackBox withTenant(TenantId tenant) {
    this.tenantId = checkNotNull(tenant);
    context().internalAccess()
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

    /**
     * Closes the tenant index and restores the default policy.
     */
    @VisibleForTesting
    public fun reset() {
        close()
        use(UnregisteredTenantPolicy.REJECT)
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

- `BoundedContextBuilder.initTenantIndex()` takes `Application.instance().tenantIndex()` for a
  multitenant context unless `setTenantIndex()` was called. Single-tenant contexts keep
  `TenantIndex.singleTenant()`.
- `BoundedContext.close()` closes the tenant index only if it was set with `setTenantIndex()`.
  The index of the application is closed by `Application.close()`.
- Shutdown order: contexts, then `Application`, then `ServerEnvironment`. The default index
  writes through the storage factory, so it must close before the factory does.
- An application registers a tenant once, when its own onboarding steps are done:
  `Application.instance().tenantIndex().keep(tenantId)`. The contexts are then notified with
  `TenantAdded` (section 9).
- Tests that switch storage factories, or set a policy, call `Application.instance().reset()`
  along with `ServerEnvironment.instance().reset()`. In-memory storages stay usable after
  their factory closes (`InMemoryStorageFactory.close()` only sets a flag), so tests that
  reset only the environment keep working.

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
  every open multitenant context that uses it (`OpenContexts` knows the open contexts).
- `BoundedContextBuilder` wraps an index given with `setTenantIndex()` the same way,
  announcing to that context only.
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

## Expected impact on existing tests

- In-process posting does not go through the gate, so most tests that use generated
  tenant IDs keep passing under the `REJECT` default.
- Tests that post through the services with generated tenants must register them first:
  `CommandServiceTest` and the `CommandServiceTenancyTest` family, by reading (not yet
  confirmed by a run). The `BlackBox.clients()` tests pass because `withTenant()` registers.
- No test asserts that posting a command registers a tenant. `ProjectionRepositoryTest`
  already calls `keep` itself.
- `BlackBoxTest.assertTenantIndex()` checks that the context returns the very instance given
  to `setTenantIndex()` (`isSameInstanceAs`). With `AnnouncingTenantIndex` (section 9), it has
  to compare the wrapped index.
- All multitenant contexts of a test JVM share the in-memory index of `Application`, and
  `TenantAdded` goes to every open multitenant context. No context in the tests handles
  `TenantAdded`, and it travels through the System contexts, not the domain `EventBus` that
  `BlackBox.assertEvents()` watches, so existing assertions are not affected.
- New specs: the gate for each service and each case in the table; the trailers decoded
  both through `MetadataConverter` and `StatusProto.fromThrowable`; `contains` in
  `TenantStorage` (cache hit, point read, no negative caching); the error `Ack` for a
  command of an unregistered tenant reaching `ServerErrorHandler` in the Java client; no
  `CommandReceived` or `CommandErrored` system event for such a command.

## Implementation plan

PRs 1 and 3 do not touch error reporting. PRs 2 and 4 depend on the `Status` rework (see
"Status").

Four PRs in core-jvm, in this order. Each one bumps the version (`bump-version`; master is at
`2.0.0-SNAPSHOT.611`), regenerates the dependency reports with a full build, and passes
`./gradlew clean build dokkaGenerate` (the first three change `.proto` files or public API).
After every test run, read the test count, not only the exit code.

1. **Tenant index for the application.** No change in how requests are served.
   - `TenantIndex.contains()` (default method) and `TenantStorage.contains()` (section 1).
   - `Application` (Kotlin, section 8): `tenantIndex()`, `use(TenantIndex)`, `close()`,
     `reset()`.
   - `BoundedContextBuilder.initTenantIndex()` takes the index of `Application`;
     `BoundedContext.close()` closes only an index set with `setTenantIndex()`.
   - Specs: `contains` (cache hit, point read, no negative caching); `Application` (lazy
     default, `use` after first use throws, `reset`); two multitenant contexts share the index;
     closing one context leaves the index open.
2. **Tenant error vocabulary.** Independent of PR 1; may go in parallel.
   - `spine/core/tenant_error.proto`: `TenantError` with the nested `Reason` (section 4).
   - `CommandValidationError`: `allow_alias`, `TENANT_NOT_SPECIFIED = 3`, deprecated
     `TENANT_UNKNOWN` (section 6). `InvalidCommandException` uses the new name; its
     `unknownTenantError(...)` gets a clearer name, the old one deprecated.
   - Specs: the alias compares equal to the old constant and keeps the number 3.
3. **`TenantAdded`.** Needs PR 1.
   - `AnnouncingTenantIndex`, used by `Application.tenantIndex()` and by `BoundedContextBuilder`
     for an index given with `setTenantIndex()` (section 9). Posting through the System write
     side of each open multitenant context, under the new tenant.
   - The documentation of `TenantAdded`: a notification, may arrive after the first request,
     may arrive twice, reaches the contexts of the node that added the tenant.
   - Until PR 4 lands, `CommandBus` still registers tenants, so a command of a new tenant
     posts `TenantAdded` too. That is correct: the tenant is added.
   - Specs: a reactor or process manager in each of two contexts receives `TenantAdded` once
     per new tenant, and not for a tenant that is already in the index; a context with its own
     index receives only its own tenants; `BlackBoxTest.assertTenantIndex()` adjusted.
4. **Reject unregistered tenants.** The behavior change. Needs PRs 1–3.
   - `UnregisteredTenantPolicy` in `Application` (section 2); `TenantGate` and
     `TenantRejection` (sections 3 and 4).
   - `ServiceDelegate.serve()` admits by tenant; `tenantOf()` in the command, query, and
     subscription delegates; `CommandService` answers with an error `Ack` (section 5).
   - Queries and subscriptions: shape checks (`TENANT_NOT_SPECIFIED`, `TENANT_INAPPLICABLE`)
     and membership (`TENANT_NOT_REGISTERED`), as RPC failures with both trailers.
   - `CommandBus.store()` stops registering; `tenantConsumer` and
     `BusBuilder.injectTenantIndex()` deprecated.
   - `MtBlackBox.withTenant()` registers (section 7).
   - Tests: register tenants in `CommandServiceTest` and the `CommandServiceTenancyTest`
     family; new specs from "Expected impact on existing tests".
   - Documentation: the Javadoc of `CommandService.post()` and of the query and subscription
     services; `TenantError` says the client builds user-facing text from `Reason` (Q7) and how
     the JVM and spine-ts differ for commands (Q8); release notes on `REJECT` and how to turn
     on `REGISTER`.

Follow-ups in other repositories, once PR 2 (for the names) or PR 4 (for the behavior) is
merged:

- **spine-ts:** adopt `tenant_error.proto` and the `Error` format (`type`, `code`, `details`),
  the new `ErrorInfo.reason` names, and the `TENANT_NOT_SPECIFIED` alias in its copy of
  `command.proto`; keep `NOT_FOUND` for commands and document the JVM `Ack` in
  `client-web/REFERENCE.md` (Q8); check that protobuf-es handles the alias.
- **gcloud-jvm:** override `contains` in `NamespaceIndex`; make registration durable, so that
  other nodes do not reject a tenant until it has data (item 5 below).

Later in core-jvm: move `OpenContexts` into `Application` (item 13); portable `Error.type` for
the other errors (item 8).

## Other aspects to discuss

1. **Scope of the index.** Resolved by Q5: one index per application, held by
   `Application` (section 8), with the existing per-context override. Unlike spine-ts, it is
   not part of `ServerEnvironment`.
2. **How applications register tenants.** Resolved by Q5:
   `Application.instance().tenantIndex().keep(tenantId)`.
3. **`TenantAdded`.** Resolved by Q9: posted when a tenant is added (section 9).
4. **Removing or suspending a tenant.** Out of scope, but `contains` makes it possible.
   After removal, other nodes may keep a cached positive answer until they restart.
5. **Datastore.** `NamespaceIndex.keep` is not durable, so with `REJECT` a tenant registered
   on one node is rejected by the others until the tenant has data. `gcloud-jvm` needs a
   durable registry or a documented onboarding step.
6. **Tenant enumeration.** `NOT_FOUND` tells the caller whether a tenant exists.
   Authentication should run before the Spine services (in an interceptor), so that
   anonymous callers cannot probe tenant IDs.
7. **User-facing messages.** Resolved by Q7: clients build the text from the
   `TenantError.Reason` value. `Error.message` stays a developer message. The documentation of
   `TenantError` should say so, so that client authors do not show `Error.message` to users.
8. **Other errors.** Errors with a Java class name in `Error.type`, and the `UNKNOWN`
   status from `QueryService.serveNoContext`, have the same portability problem. Follow-up.
9. **Blocking lookups.** `contains` is synchronous. A remote directory call blocks the
   gRPC thread, so implementations should cache hits.
10. **Commands differ from spine-ts.** After Q1, the JVM answers a command of an unknown
    tenant with an error `Ack`, and spine-ts fails the RPC with `NOT_FOUND`. Either spine-ts
    switches to the `Ack` too (it already uses the `Ack` for `TENANT_REQUIRED` and
    `TENANT_INAPPLICABLE`), or the difference is documented for client authors.
    Resolved by Q8: documented (section 5).
11. **spine-ts adopts `TenantError`.** After Q4, spine-ts needs the same payload: the new
    `tenant_error.proto`, `type = "spine.core.TenantError.Reason"`, `code` = the value number,
    and the packed `TenantError` in `details`. Its server, the `client-web` tests and
    `client-web/REFERENCE.md` check `type: "TENANT_UNKNOWN"` today. The packages are at
    `2.0.0-snapshot.26`, with no release tag. After Q6, the value names it sends in
    `ErrorInfo.reason` change too (`TENANT_NOT_REGISTERED`, `TENANT_NOT_SPECIFIED`), and its
    copy of `command.proto` gets the `TENANT_NOT_SPECIFIED` alias. Whether protobuf-es
    generates aliases the way the JVM side expects is not checked yet.
12. **The name `Application` is common in user code.** The Javadoc of
    `io.spine.environment.Environment` uses `public final class Application` as the example of
    an application's own class. Such applications would refer to the new type by its full name,
    `io.spine.server.Application`, in the files that use both.
13. **`OpenContexts` belongs to `Application` too.** The internal `OpenContexts` object
    (`server/src/main/kotlin/io/spine/server/OpenContexts.kt`) keeps the open contexts of the
    process and the entity state types they serve. That is application-level state, so it can
    move into `Application` later. Out of scope for this change.

## Questions and the options considered

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
   Decided: an enum, nested as `spine.core.TenantError.Reason` (B2). The resulting design is
   in section 4; the discussion is kept below. Facts:
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

   B2 was chosen; its definition is in section 4.
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
   `TenantError.Reason.TENANT_NOT_REGISTERED` (section 6). Commands keep
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
   Decided: keep `NOT_FOUND` in spine-ts and document the difference (section 5). Facts:
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
