# Several targets in a subscription topic

This guide tells you what changes when you upgrade to `2.0.0-SNAPSHOT.790` and then to
`2.0.0-SNAPSHOT.800`. A subscription `Topic` now has a list of targets instead of one.
Code that reads the target of a topic or of a subscription stops compiling, and needs
the changes below. Upgrade the servers before the clients; the [upgrade rule](#upgrade-rule)
tells why.

- `2.0.0-SNAPSHOT.790` changes the protocol and the server: sections 1–4.
- `2.0.0-SNAPSHOT.800` changes the client API: sections 5–7.

## What changed, in one paragraph

Before, a topic had one `Target`: one type of events or entity states, with its filters.
To follow what the domain language treats as one thing, such as the outcome of a command,
a client made a subscription for each type, and cancelled each of them. Now a topic may have
any number of targets, of event types and entity types, served by any Bounded Contexts of
the application. One type may appear in several targets, for example, with different filters.
The client gets one subscription, one stream of updates, and cancels the subscription once.
Each update names the target that it matches.

## At a glance

| Area | Before | After |
|------|--------|-------|
| `Topic.target` | one `Target` | `repeated Target target`; in Java, `getTargetList()`, `getTarget(int)`, `addTarget()` |
| The targets of one topic | one type, served by one Bounded Context | event and entity types served by any contexts; one type may repeat |
| `SubscriptionUpdate` | no reference to the target | `target_index`: the position of the matched target in `topic.target` |
| A message that matches several targets | — | one update for each target it matches |
| `Subscription.targetType()` | the type of the target | **removed**; use `targetTypes()` |
| `Subscription.ofEvent()` | tells if the target is an event type | **removed**; use `Target.ofEvent()` |
| `Stand.subscribe(Topic, StreamObserver)` | created a subscription | **removed**; `SubscriptionService` creates subscriptions |
| `Stand.subscribe(Subscription)` | registered a subscription | **`subscribe(Subscription, Set<Integer>)`**: the targets that the `Stand` serves |
| A topic in JSON | `"target": { … }` | `"target": [ { … } ]` |
| Several targets in one client request | — | **`.800`**: `ClientRequest.subscribe()` returns a `TopicRequest` |
| `CommandRequest.post()` | `ImmutableSet<Subscription>`, one for each event type | **`.800`**: one `Subscription`, or `null` on a refusal |
| Entity states in the outcome of a command | — | **`.800`**: `observe(Class, Consumer<StateTarget>)`; in Java, `observeState()` |
| `CommandRequest.onConsumingError()` | `ConsumerErrorHandler<EventMessage>` | **`.800`**: `ConsumerErrorHandler<Message>` |

In `2.0.0-SNAPSHOT.790`, the client requests of `Client`, such as `subscribeTo()`,
`subscribeToEvent()`, and `CommandRequest.post()`, still make one subscription for each type,
and their consumers work as before. `2.0.0-SNAPSHOT.800` changes `CommandRequest`, and adds
the request for several targets.

## 1. Read the targets of a topic

A topic made by `subscribeTo()`, `subscribeToEvent()`, or `TopicFactory.select()` has one
target, at the index `0`. A topic made elsewhere may have more.

In Kotlin:

```kotlin
// Before.
val type = subscription.targetType()
val ofEvent = subscription.ofEvent()

// After.
val types = subscription.targetTypes()
val ofEvent = subscription.topic.targetList.any { it.ofEvent() }
```

In Java:

```java
// Before.
var type = subscription.targetType();
var ofEvent = subscription.ofEvent();

// After.
var types = subscription.targetTypes();
var ofEvent = subscription.getTopic()
                          .getTargetList()
                          .stream()
                          .anyMatch(Target::ofEvent);
```

`targetTypes()` names each type once, in the order of the targets.

## 2. Tell which target an update matches

An update carries the index of its target. A message that matches two targets comes in two
updates, so that the consumers of each target receive it.

In Kotlin:

```kotlin
val target = update.subscription.topic.getTarget(update.targetIndex)
```

In Java:

```java
var target = update.getSubscription()
                   .getTopic()
                   .getTarget(update.getTargetIndex());
```

A `no_longer_matching` update is reported for each target that the entity state stops
matching. The entity may still match another target of the same type.

## 3. How the server serves a topic with several targets

- **Subscribing.** The subscription is created in each Bounded Context that serves the type of
  a target. A target of an event type that no context serves is served by every context, as
  a topic with one such target was before. If one context refuses the topic, no context keeps
  the subscription, and the client gets the error case of that context.
- **Activating.** The updates of all the targets come in the stream of one `Activate` call.
  The updates of one target are not ordered with respect to the updates of another. If
  a context that serves a type of the topic does not have the subscription, the activation is
  refused with `UNSUPPORTED_REQUEST`.
- **Cancelling.** One `Cancel` call cancels the subscription in all the contexts that have it.
- **Tenants.** The tenant of the topic must fit each context that serves a type of the topic.
  A topic spanning a single-tenant and a multi-tenant context is refused with `TENANT_ERROR`.

## 4. Code that subscribes in a `Stand` directly

`SubscriptionService` creates the subscriptions now. A test that used
`Stand.subscribe(Topic, StreamObserver)` creates the subscription itself, and names the targets
that the `Stand` serves.

In Kotlin:

```kotlin
val subscription = Subscriptions.from(topic)
stand.subscribe(subscription, setOf(0))
```

In Java:

```java
var subscription = Subscriptions.from(topic);
stand.subscribe(subscription, Set.of(0));
```

A `Stand` refuses a subscription that it already has with `IllegalArgumentException`.

## 5. Subscribe to several targets at once

`ClientRequest.subscribe()` makes one subscription to several targets. Each call of `event()`
or `state()` adds one target, and configures it: its filters or IDs, and its consumers.
One type may appear in several targets, for example, with different filters. A message that
matches two targets goes to the consumers of each of them.

In Kotlin:

```kotlin
val subscription = client.onBehalfOf(user).subscribe {
    event<TaskAssigned> { it.observe { e -> show(e) } }
    state<Task> {
        it.where(eq(Task.Field.status(), DONE))
            .observe { task -> showDone(task) }
    }
    state<Task> {
        it.where(eq(Task.Field.assignee(), user))
            .observe { task -> showMine(task) }
            .whenNoLongerMatching(TaskId::class.java) { id -> hideMine(id) }
    }
}
```

In Java:

```java
Subscription subscription = client.onBehalfOf(user)
        .subscribe()
        .event(TaskAssigned.class, t -> t.observe(this::show))
        .state(Task.class, t -> t.where(eq(Task.Field.status(), DONE))
                                 .observe(this::showDone))
        .state(Task.class, t -> t.where(eq(Task.Field.assignee(), user))
                                 .observe(this::showMine)
                                 .whenNoLongerMatching(TaskId.class, this::hideMine))
        .post();
```

`post()` throws `StatusRuntimeException` if the server refuses the subscription, as
`subscribeTo(…).post()` does. Each target needs at least one consumer of its messages;
otherwise, `post()` throws `IllegalStateException`.
One call of `client.subscriptions().cancel(subscription)` stops the updates of all the targets.

`subscribeTo()` and `subscribeToEvent()` keep making a subscription to one type.

## 6. Observe the outcome of a command

`CommandRequest.post()` makes one subscription for the whole outcome of the command:
the observed events and entity states. It returns this subscription, or `null` if the server
refuses the subscription or the command. The error case still goes to the server error handler.

In Kotlin:

```kotlin
// Before.
val subscriptions: Set<Subscription> = client.onBehalfOf(user)
    .command(assignTask)
    .observe(TaskAssigned::class.java) { e -> show(e) }
    .post()
subscriptions.forEach { client.subscriptions().cancel(it) }

// After.
val subscription: Subscription? = client.onBehalfOf(user).command(assignTask) {
    observe<TaskAssigned> { e -> show(e) }
    observe<Task> { it.byId(taskId).observe { task -> render(task) } }
}
subscription?.let { client.subscriptions().cancel(it) }
```

The block form posts the command when the block ends. The chain form,
`.command(assignTask).observe(…).post()`, works as well. Kotlin tells events from entity
states by their types: `observe(Task::class.java) { … }` adds a target of states.

In Java:

```java
// Before.
ImmutableSet<Subscription> subscriptions = client.onBehalfOf(user)
        .command(assignTask)
        .observe(TaskAssigned.class, this::show)
        .post();
subscriptions.forEach(client.subscriptions()::cancel);

// After.
Subscription subscription = client.onBehalfOf(user)
        .command(assignTask)
        .observe(TaskAssigned.class, this::show)
        .observeState(Task.class, t -> t.byId(taskId).observe(this::render))
        .post();
if (subscription != null) {
    client.subscriptions().cancel(subscription);
}
```

Java calls the method for entity states `observeState()`, because the versions of
`observe(Class, Consumer)` for events and for entity states would have the same signature
after type erasure.

An entity state does not refer to the command that changed it. A target of states without IDs
or filters gets every change of the entities of its type, so name the IDs of the entities that
the command changes.

The consumers of events and entity states share one handler of their errors.
`onConsumingError()` now takes a `ConsumerErrorHandler<Message>`. A lambda keeps compiling;
a variable declared as `ConsumerErrorHandler<EventMessage>` needs the new type.

`postAndForget()` throws `IllegalStateException` if the request observes events or entity
states, and `post()` throws it if the request observes nothing, as before.

## 7. Other client changes

- `SubscriptionRequest.whenNoLongerMatching()` adds a consumer, as its documentation says.
  Before, a second call replaced the consumer of the first one.
- A `whenNoLongerMatching()` consumer that throws no longer ends the subscription: the error
  is logged, and the updates go on. A subscription now carries the updates of all its targets,
  so one failing consumer would otherwise stop them all.
- `CommandRequest.observe()` refuses an event type that is not a part of the published
  language with `UnpublishedLanguageException`, as `subscribeToEvent()` does. Before, the error
  came from the server.
- A streaming error handler set on the `Client` builder, or on a `ClientRequest`, reaches
  the subscriptions that `subscribeTo()` and `subscribeToEvent()` make. Before, these
  subscriptions logged their streaming errors unless the handler was set on the subscription
  request itself. `CommandRequest` already used such a handler.

## Upgrade rule

Upgrade the servers before the clients. The binary format of a one-target topic has not
changed, so a new server reads the topics of old clients. An old server, however, merges
the targets of a topic with several of them into one target, without an error, and serves
a subscription that matches neither. So clients of `2.0.0-SNAPSHOT.800` need servers of
`2.0.0-SNAPSHOT.790` or later: `subscribe()` and `CommandRequest.post()` make topics with
several targets.

Convert the topics and subscriptions that you keep as JSON: their `target` field is now
an array.
