# Several targets in a subscription topic

This guide tells you what changes when you upgrade to `2.0.0-SNAPSHOT.790`. A subscription
`Topic` now has a list of targets instead of one. Code that reads the target of a topic or of
a subscription stops compiling, and needs the changes below. Upgrade the servers before
the clients; the [upgrade rule](#upgrade-rule) tells why.

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

The client requests of `Client`, such as `subscribeTo()`, `subscribeToEvent()`, and
`CommandRequest.post()`, still make one subscription for each type in this version.
Their consumers work as before.

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

## Upgrade rule

Upgrade the servers before the clients. The binary format of a one-target topic has not
changed, so a new server reads the topics of old clients. An old server, however, merges
the targets of a topic with several of them into one target, without an error, and serves
a subscription that matches neither.

Convert the topics and subscriptions that you keep as JSON: their `target` field is now
an array.
