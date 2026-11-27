---
slug: subscription-multiple-targets
branch: claude/subscriptions-multiple-targets-f1f9f8
owner: claude
status: in-progress
started: 2026-11-26
---

## Goal and context

Client apps want to subscribe to what the domain language treats as one thing, such as the
outcome of a command: the events its handling emits, and the entity states it changes. Today
a `Topic` has one `Target`. Two events and two states therefore take four subscriptions, and
cancelling takes four calls. This change gives a `Topic` several targets: the client
subscribes once, gets one `Activate` stream, and cancels once. It is a breaking change, and
users migrate.

What the code does today, in brief:

- `ServiceDelegate.serve()` routes a request to one context by type (`TypeDictionary`).
- Only `serveNoContext()` keeps one subscription in several `Stand`s.
- `SubscriptionRecord` keys its handlers by the handled event type, so two entity targets
  would collide: `toImmutableMap` throws.
- `no_longer_matching` carries only a packed `EntityId`.
- `CommandRequest` observes only events, and `post()` returns `ImmutableSet<Subscription>`.

## Owner's decisions (2026-11-26)

1. A topic may span Bounded Contexts.
2. There are two client APIs.
   - `CommandRequest` observes states as well as events, and `post()` returns one
     subscription.
   - The general `ClientRequest.subscribe()` returns a `TopicRequest`.
   - `subscribeTo(Class)` and `subscribeToEvent(Class)` stay single-target.
3. A topic may name one type in several targets (for example, `Task` where `status = DONE`,
   and `Task` where `assignee = me`). Each target is matched on its own. A message that
   matches two targets is sent as two updates, one per target, and `no_longer_matching` is
   reported per target.
4. Each target is configured by a lambda over a narrow builder (`where`, `byId`, `observe`,
   `whenNoLongerMatching`), with no `post()` and no error handlers.
5. Names:
   - `TopicRequest.event(type) { … }` and `.state(type) { … }`;
   - `CommandRequest.observeState(type) { … }`, next to the existing `observe(eventType, …)`.
6. `CommandRequest.post()` returns `@Nullable Subscription`. It is `null` on a refusal, and
   the error case still goes to the `ServerErrorHandler`.

## Design

### Protobuf

- **`Topic`.** `repeated Target target = 2 [(required) = true, (validate) = true]`, with the
  same number and a singular name.
  - New code reads an old topic as one target.
  - An old server would silently merge several targets into one, so the migration guide
    says to upgrade servers before clients.
  - In JSON, the field becomes an array.
- **`SubscriptionUpdate`.** `int32 target_index = 3`, the position of the matched target in
  `subscription.topic.target`, with the reserved range narrowed to `4 to 9`.
  - Fields 3–9 have been reserved for future attributes since `1659a0a` and were never used.
  - It names the target of every update, including `no_longer_matching`. The type alone
    cannot, since a type may appear in several targets.
- **`UnsupportedTopicReceived.type_url`.** Only the doc changes: it names one target type
  that the context does not serve.

### Server: `SubscriptionService` (in `server/.../SubscriptionService.java`)

**Subscribe** runs in two phases, so a rejection leaves nothing registered.

1. **Checks before routing.** These answer as `ServiceDelegate.serve()` does today, and log
   to the application log:
   - Every target type is checked: an unreadable type gives `INVALID_REQUEST`; an unknown or
     internal type gives `UNSUPPORTED_REQUEST`.
   - A topic that fails proto validation (for example, with no targets) gives
     `INVALID_REQUEST`.
2. **Routing to holders.** The result is a map from context to the indexes of its targets,
   deduplicated and sorted with `Ordering.natural()`. Targets of one type go to the same
   context.
   - Each served type goes to its context.
   - An entity type that no context serves is refused with `refuseNotServed`.
   - An event type that no context declares goes to every context: today's fallback (#1712),
     generalised.
3. **Phase A: admission and validation.** Nothing is registered yet.
   - A holder serving a type checks the tenant strictly with `admitted(context, …)`. So
     a topic spanning a single-tenant and a multi-tenant context gets `TENANT_ERROR`.
   - A fallback-only holder keeps today's `admittedToAll`.
   - Then each holder runs `stand.validate(subscription, itsTargets)`. The first failure is
     answered with `reject(...)` and recorded in that holder's System context.
4. **Phase B: registration.** The service creates one `Subscription` and calls
   `stand.subscribe(subscription, itsTargets)` in each holder.
   - If holder k fails unexpectedly, the holders before it drop the subscription. Holder k
     records `SubscriptionServiceFailed`, and the client gets `INTERNAL_ERROR`.

**Activate**
- The holders are the contexts whose `Stand` has the subscription; each checks the tenant.
- Every target of the topic must be held by some holder; otherwise the request gets
  `UNSUPPORTED_REQUEST`.
- All holders are activated with one shared callback over the `ThreadSafeObserver`, and the
  client gets one acknowledgement.
- **Fail closed.** If one holder fails, the subscription is cancelled in all of them, so no
  `Stand` keeps writing to a stream that is closed. `UpdateHandler.callback` cannot be unset.
- If no holder has the subscription but its types are served, the request is rejected in the
  serving context with `UnknownSubscriptionActivationRequested` / `…Cancellation…`. This keeps
  `SubscriptionServiceErrorCaseSpec`.

**Cancel**
- Every holder is served, even after one fails; the client gets one answer.
- A retry finds the holders that remain.

**`ServiceDelegate`**
- Routing becomes a hook: `enclosedMessageTypes(T)` (one element by default) plus a route
  step that the subscription delegates override.
- `admitted` becomes `protected final`.
- `CommandService` and `QueryService` keep their behaviour.

### Server: `Stand` and the registry (`server/.../stand/`)

- **`Stand`.**
  - `validate(Subscription, targets)` and `subscribe(Subscription, targets)` validate and
    hold only the targets the service gives them. An entity target must be in the type
    registry with `SUBSCRIBE` visibility; an event target must be an event.
  - It refuses a subscription ID it already has. Re-adding today leaves a stale inactive
    record in `typeToRecord` (`TenantSubscriptionRegistry.add`), and updates get lost.
  - `subscribe(Topic, StreamObserver)` is removed: the service always creates the
    subscription, so all holders share one ID.
- **`SubscriptionRecord`.** An `ImmutableListMultimap` maps the handled event type to the
  handlers; `update()` hands the event to every handler for its type, so each target that
  matches sends its own update. `SubscriptionRecord.of(...)` decides event versus entity per
  target, not per subscription.
- **`UpdateHandler(Subscription, targetIndex)`.** Each handler reads its own target from
  the topic and sets `target_index` on the updates it builds. The changes reach
  `AbstractEntityUpdateHandler`, `EventUpdateHandler`, `TopicValidator` and
  `AbstractTargetValidator`.
- **`Stand.validate`/`subscribe`** take the target indexes that the service routed to this
  context, so every holder uses the same indexes.
- **Docs.** The docs say that one stream carries no ordering across contexts.

### Client

- **`TopicFactory.forTargets(Iterable<Target>)`** is `@Internal`. It refuses an empty list.
  `TopicBuilder` stays single-target.
- **`SubscriptionMixin`.** `targetTypes()` replaces `targetType()` and `ofEvent()`;
  `toShortString()` lists the types.
- **Routing observer (new, Kotlin, internal).** It routes updates by `target_index` to each
  target's consumers. It merges what `SubscriptionObserver`, `DeliveringMultiEventObserver`
  and `NoLongerMatchingFilter` do today for one type, so all requests share one delivery
  path.
- **`EventTarget<E>` and `StateTarget<S>`** are new Kotlin builders: `where`, `byId`,
  `observe`, and `whenNoLongerMatching` for states. They check the published language as
  `ClientRequest` does.
- **`TopicRequest` and `ClientRequest.subscribe()`** are new, in Kotlin.
  - Each `event(…)` or `state(…)` call adds one target, in call order, so the index of
    a target is its position in the chain.
  - Error handlers come from `SubscribingRequest`.
  - `post()` throws `StatusRuntimeException` on a refusal, like the single-type `post()`.
- **`CommandRequest.observeState(Class<S>, Consumer<StateTarget<S>>)`**, and `post()` returns
  `@Nullable Subscription`.
  - `EventsAfterCommand` builds one topic: an event target with the origin filter for each
    observed event type, plus a target for each `observeState(…)` call. Observers of one
    event type share a target, as `MultiEventConsumers` does today, so an event is not
    delivered twice.
  - On a refusal, the one subscription is cancelled.
- **`Subscriptions.addAll()`** is removed.
- **Non-Spine types in the new signatures:** `Class` and `java.util.function.Consumer`, which
  match the existing `observe(Class, Consumer)`.

### Sample (Kotlin)

```kotlin
val subscription: Subscription? = client.onBehalfOf(user)
    .command(assignTask)
    .observe(TaskAssigned::class.java) { e -> show(e) }
    .observeState(Task::class.java) { it.byId(taskId).observe { task -> render(task) } }
    .post()
subscription?.let { client.subscriptions().cancel(it) }   // one call for the outcome

val board: Subscription = client.onBehalfOf(user)
    .subscribe()
    .event(TaskAssigned::class.java) { it.where(...).observe { e -> show(e) } }
    .state(Task::class.java) {
        it.byId(taskId)
          .observe { task -> render(task) }
          .whenNoLongerMatching(TaskId::class.java) { id -> hide(id) }
    }
    .post()
```

## Delivery: two PRs, each a breaking version bump via the `bump-version` skill

**PR 1: the proto, the server, and the client internals.**
- The protobuf changes.
- The server design above.
- `TopicFactory.forTargets`, `SubscriptionMixin`, and the routing observer.
- `CommandRequest` keeps one topic per event type until PR 2.
- The migration guide `docs/migration/multiple-targets-per-topic.md`: the proto and server
  part, including "upgrade servers first".

**PR 2: the public client API.**
- `EventTarget`, `StateTarget`, `TopicRequest`, `ClientRequest.subscribe()`.
- `CommandRequest.observeState` and `post(): Subscription?`.
- `EventsAfterCommand` makes one topic.
- `SubscriptionRequest` and `EventSubscriptionRequest` are moved onto the shared builders.
- The client part of the migration guide, with Kotlin samples first.

**Follow-ups in other repositories, after the release:** they keep the set from `post()`.
Filed on 2026-11-26: spine-examples/hello#80, SpineEventEngine/delivery#86 (`GreeterServlet`),
and SpineEventEngine/documentation#99 (the quick start embeds the `hello` client).
`examples/hello-kt` has no client code, and `todo-list` only calls `postAndForget()`.

**Out of scope, noted:**
- The leak when a client disconnects without cancelling (an existing problem): issue #1739.
- `TypeDictionary` keeping the last context for an event type produced by two contexts
  (#1728).

## Critical files

- `client/src/main/proto/spine/client/subscription.proto`
- `server/src/main/java/io/spine/server/SubscriptionService.java`
- `server/src/main/java/io/spine/server/ServiceDelegate.java`
- In `server/src/main/java/io/spine/server/stand/`: `Stand`, `SubscriptionRecord`,
  `TenantSubscriptionRegistry`, `UpdateHandler` and its subclasses, `TopicValidator`.
- In `client/src/main/java/io/spine/client/`: `CommandRequest`, `EventsAfterCommand`,
  `TopicFactory`, `SubscriptionMixin`, `Subscriptions`, `SubscriptionObserver`,
  `SubscribingRequest`.

To reuse:
- `StreamObservers.memoizingObserver()`, for the single acknowledgement;
- `ThreadSafeObserver`;
- `ServiceDelegate.reject` / `refuse` / `refuseNotServed`;
- `TargetBuilder`, to build each `Target`;
- `MultiEventConsumers` and `StateConsumers`, for consumers per type.

## Verification

All specs are in Kotlin with Kotest.

**PR 1**
- `SubscriptionRecordSpec`: two entity targets and an event target in one record; each
  update has its `target_index`, including `no_longer_matching`. Two targets of one type
  with different filters: a state that matches both gives two updates. A state that stops
  matching one target but still matches the other gives `no_longer_matching` for the first
  target only.
- `StandMultiTargetSpec`: a `Stand` refuses an ID it already has.
- `SubscriptionServiceMultiTargetSpec`, two contexts (an event of A and a projection of B):
  - one subscribe registers in both, one stream carries both, one acknowledgement, and one
    cancellation removes it from both;
  - B rejects (visibility), and A holds nothing;
  - a topic with no targets gives `INVALID_REQUEST`;
  - an unknown entity type, and an internal type in one target, give `UNSUPPORTED_REQUEST`;
  - a single-tenant plus a multi-tenant context gives `TENANT_ERROR`;
  - an activation that fails in B cancels the subscription in A;
  - an event type that no context declares is held by every context.
- `TopicFactorySpec`: an empty list is refused; repeated types are kept in order.
- The existing stand and subscription specs are migrated.

**PR 2**
- `TopicRequestSpec`, end to end: events and states; two `state(Task…)` targets with
  different filters each get their own consumers; `no_longer_matching` per target; one
  cancellation; an unpublished type is refused.
- `CommandRequest` specs:
  - `observeState`;
  - one subscription;
  - a refusal returns `null` and leaves `subscriptions()` empty.

**Before each PR**
- `./gradlew build dokkaGenerate`. Read the test counts, not only the exit code.
- `:server:detekt`.
- The `pre-pr` reviewers.

## Log

- 2026-11-26 — analysis; the owner answered seven questions (decisions 1–6, with
  decision 3 relaxed to allow one type in several targets); plan approved; PR 1 started.
- 2026-11-26 — PR 1 implemented, not committed. Differences from the design above:
  - `Stand` has `subscribe(Subscription, Set<Integer>)` and an `@Internal`
    `allowsSubscriptionTo(Target)`, not a separate `validate(...)`. The service checks each
    routed target with `allowsSubscriptionTo()`, so `UnsupportedTopicReceived` names the exact
    type; the proto validation of a topic happens once, in the service.
  - `ServiceDelegate`: the abstract `enclosedMessageTypes(T)` replaces `enclosedMessageType(T)`.
    A request whose types one context serves takes the single-context path (`serve(context)`),
    any other one `serveNoContext()`, where the subscription delegates route across contexts.
  - `Target.ofEvent()` (in `TargetMixin`) replaces `Subscription.ofEvent()`.
  - The routing observer moves to PR 2: PR 1 has no consumers of several targets.
  - Fail-closed activation has no spec: an activation fails in a holder only on a race or
    an unexpected error, which a test cannot trigger without injecting a failing `Stand`.
  - Migration guide `docs/migration/multiple-targets-per-topic.md` names `.790` (breaking bump
    from `.782`); the bump waits for commit authorization.
- 2026-11-26 — PR #1740 opened. On the owner's request, `TenantSubscriptionRegistry` keys
  the records by `SubscriptionId` (was the whole `Subscription`, with a scan of all of them to
  find one by ID). Lookups, the duplicate check in `add()` (which ran inside the registry lock)
  and removal no longer depend on the number of subscriptions. This matters for PR 2, where
  each `CommandRequest.post()` subscribes and cancels once.
- 2026-11-26 — review of #1740 (Copilot): activation and cancellation now find the contexts
  that have a subscription by its ID on the single-context path too, so a client that sends
  the subscription back with a modified topic still reaches every holder. Activation streams
  through `GuardedObserver`, which passes nothing after the stream terminates, so an update
  that one context delivers while another fails cannot follow the error.
- 2026-11-26 — PR 2 started in its own worktree (branch `claude/subscriptions-client-api-7f88b0`,
  stacked on PR 1's branch). The owner approved the public signatures in two rounds. The
  second round added a Kotlin DSL and one `observe` name in Kotlin. Differences from the
  design above:
  - `CommandRequest` and `EventsAfterCommand` are converted to Kotlin. In Kotlin,
    `observe(Class<S>, Consumer<StateTarget<S>>)` takes entity states. Kotlin chooses it by
    the bound of the type. Java sees it as `observeState()` (`@JvmName`), because the two
    erase to the same signature.
  - The Kotlin DSL lives in `ClientExts.kt`: `ClientRequest.command(c) { … }: Subscription?`,
    `ClientRequest.subscribe { … }: Subscription`, and the reified `observe<T>`, `event<E>`,
    and `state<S>`, all `@JvmSynthetic`. They are extensions because detekt allows 10
    functions per class, and `*Exts.kt` files are exempt.
  - `CommandRequest.onConsumingError()` takes `ConsumerErrorHandler<Message>` (was
    `<EventMessage>`), so it also covers the consumers of states.
  - `whenNoLongerMatching()` adds a consumer, as its doc says (it replaced the consumer
    before). `CommandRequest.observe(eventType, …)` checks the published language.
  - `TopicRequest` does not redeclare `onServerError()`. As on `SubscribingRequest`,
    a refusal throws from `post()`.
  - Removed: `SubscriptionObserver`, `NoLongerMatchingFilter`, `DeliveringMultiEventObserver`,
    `MultiEventConsumers`, `Subscriptions.addAll()`, and `SubscribingRequest.chain()`
    (protected in a class that cannot be extended outside the package).
  - Internals: `TargetObserver` (sealed: events or states) and `RoutingObserver`.
    `Subscriptions.subscribeTo(TopicFactory, List<TargetObserver>, ErrorHandler?)` is the one
    delivery path of every request. When no streaming handler is set, the observer logs
    the error.
  - Behaviour change found on the way: `Client.onBehalfOf()` sets the client-level
    streaming handler on each `ClientRequest`. `subscribeTo()`/`subscribeToEvent()` streams
    now use it (before, only `CommandRequest` did). `SubscriptionCancellationErrorCaseSpec`
    used `addAll()` as a test hook. It now makes a real subscription and drops it on the server
    through a raw stub. Its stub service accepts `subscribe`/`activate`, and its requests set
    their own streaming handler.
- 2026-11-26 — PR #1741 opened (base `master`, since #1740 was merged first). On the owner's
  request, a separate fixture tests the client API in full. The new `board` model
  (`server/src/testFixtures/.../client/board/`) has a `Card` aggregate with the `status` and
  `assignee` columns. `TopicRequestSpec` and `CommandRequestSpec` now filter state targets by
  fields, use a composite filter, repeat a filtered event type, and report an entity that stops
  matching one target while it still matches another. `CTask` has no columns, and stays as it is.
- 2026-11-26 — The owner renamed the internal `TargetDelivery` to `TargetObserver`. Its subclasses
  are now `EventTargetObserver` and `StateTargetObserver`, with `onUpdate()` and the targets'
  `toObserver()`. The reason: the server has `io.spine.server.delivery.TargetDelivery`, and
  "delivery" names the server's Delivery subsystem. After Codex and Copilot reviewed #1741,
  a `whenNoLongerMatching()` consumer that throws any `Throwable` is logged. Before, only an
  `Exception` was, so an `AssertionError` still ended the subscription. A `VirtualMachineError`
  still propagates.
- 2026-11-27 — armiol's review of #1741. A `state()` called from the Kotlin block of another
  target (`this` there is still the request) used to come first. Now each target keeps the
  position of its call, in `TopicRequest` and among the state targets of `CommandRequest`.
  `CommandRequest.post()` still rethrows a failed subscription that carries no error case, as
  master's Java code did. Its KDoc and the guide now say so. Trap: `Subscription` already has
  a member `targetTypes()` that returns an unordered `ImmutableSet<TypeUrl>`, which shadows any
  test extension with that name.
