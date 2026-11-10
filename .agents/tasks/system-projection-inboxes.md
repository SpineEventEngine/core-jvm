# System projections: deliver to the System context that received the signal

Branch: `claude/system-projection-inboxes` (from `master` at `6210d75dab6`).
There is no issue yet.

## Status

- [x] Reproduction: `SystemProjectionsIgTest`, 2 cases, both fail on `master`
- [x] Control: the same cases pass when the receiving context is built last
- [ ] Plan approved by the owner
- [x] `Delivery.newInbox()` takes the context; `Inbox.Builder.withContext()` removed
- [x] Proto field, `InboxPart`, `InboxDeliveries`
- [x] `InboxDeliveriesSpec` and `DeliverySpec` cases; hand-built test messages carry a context
- [x] `SystemProjectionsIgTest` green
- [x] Version bumped to `2.0.0-SNAPSHOT.680` (not committed)
- [x] `./gradlew clean build dokkaGenerate`: green; `:server:test` ran 2556 tests, none failed
- [x] Reviews: `spine-code-review`, `kotlin-engineer`, `review-docs`; findings applied
      (see "Reviews")
- [x] `./gradlew :server:check dokkaGenerate` after the review fixes: green, 2556 tests
- [ ] Commit and PR (waiting for the owner)

## What fails

`server/src/test/kotlin/io/spine/system/server/SystemProjectionsIgTest.kt` builds two
single-tenant contexts with the command log on, `First` and then `Last`. Both use
the `Delivery` of the server environment. A command is posted to `First`.

| Case | Expected | Actual |
|---|---|---|
| `CommandLog` of the command | in the System context of `First` | in the System context of `Last` |
| `ScheduledCommandRecord` of a scheduled command | in the System context of `First` | in neither |

When `First` is built after `Last`, both cases pass. So the outcome depends only on
the order in which the contexts are built.

A probe showed that the `CommandLog` of the scheduled command also lands in `Last`.
That explains the lost record. `SystemEnricher` attaches the `Command` to
`CommandScheduled` by reading the `CommandLogRepository` of its own System context
(`First`). That repository does not have the command. So the projection gets no
command, and its handler fails. Nothing is logged.

## Why

- An `InboxMessage` is addressed by `InboxId`: the entity ID and the state type URL.
  Neither the message nor the System events in it name a context.
- `InboxDeliveries` keys the inboxes of every type except the two catch-up types by
  the type alone. `register()` replaces an earlier inbox of the same type.
- Every System context with the command log registers `CommandLogRepository` and
  `ScheduledCommandRepository`. The strict duplicate-state check skips System
  contexts on purpose (`DuplicateEntityStateSpec`, "not count System contexts").
  So the inbox of the System context built last receives the messages of all of them.
- The catch-up types avoid this by deriving the System context from the job inside
  the message. A `CommandLog` cannot do the same: it is kept even for a command that
  no context handles.

## Proposed fix

Messages written before this change are out of scope: clients finish the delivery in
progress and move to new storages.

1. **Every inbox has a context.** `Delivery.newInbox(TypeUrl)` becomes
   `newInbox(TypeUrl, BoundedContextName)`, and `Inbox.Builder.withContext()` is removed.
   `Inbox.contextName()` is no longer `@Nullable`. Today the context is optional only
   for historical reasons (see "Why the context was optional").
2. **Proto.** Add `core.BoundedContextName context = 12 [(required) = true]` to
   `InboxMessage`: the context of the inbox that received the message.
3. **`InboxPart.store()`** sets it from the context of the inbox.
4. **`InboxDeliveries`** keeps one map, keyed by the type and the context.
   `CatchUpInbox` becomes this general key, and the map keyed by the type alone is removed.
   - `get(message)`:
     - catch-up types: unchanged, the context comes from the job;
     - any other type: the inbox of that type in the context of the message. If that
       context has no inbox of the type, it fails. It never falls back to the inbox
       of another context.
   - `register()` replaces only an inbox of the same type in the same context.
5. **Tests.**
   - `SystemProjectionsIgTest` turns green.
   - New `InboxDeliveriesSpec` cases:
     - a message reaches the inbox of its context, not the inbox registered last;
     - a message whose context has no inbox of the type fails, even when another
       context has one.
   - One case checks that a stored message carries the context of its inbox.
   - `InboxDeliveriesSpec.inbox()` and `TestInboxMessages` get a context.
6. **Docs.** Javadoc of `InboxDeliveries`, `register()`, `Delivery.newInbox()`,
   `Inbox.contextName()`, and of the new proto field.
7. **Version.** Bump above `.670`, choosing a number no open PR uses. A proto change
   needs `./gradlew clean build dokkaGenerate`. Then read the `:server:test` count.

## Alternatives considered

- **Add the context to `InboxId`.** Rejected as the wider change. `InboxId` is a storage
  column, and code compares it for dedup (`DispatchingId`), batching (`TargetDelivery`),
  and catch-up matching (`CatchUpJobs`, `CatchUpMixin`). Every place that builds or
  compares an `InboxId` would be involved. A separate field involves only the writer
  and the router.
- **Derive the context from the content**, as the catch-up does. Not possible: the
  System events carry no context name, and a `CommandLog` is kept for commands that
  no context handles.
- **A separate `Delivery` per System context.** Needs its own storages and shard
  sessions, and goes against `BoundedContextBuilder.setDelivery()`: "The System
  context of the built context uses the same delivery."
- **Make the duplicate check fail instead.** That would forbid two contexts with
  the command log, which `DuplicateEntityStateSpec` explicitly allows.

## Notes

- Catch-up messages now carry a context too, but `InboxDeliveries` still takes theirs
  from the job, as planned. Reading the code, every catch-up signal is posted to the
  System context of the job's projection, so the two should agree. Not verified at run
  time; using the stored context for them too would remove the job lookup.
- The key is the context *name*. Two contexts with the same name that share one
  `Delivery`, for example in two `Application` instances in one JVM, would still
  collide. `application-instances.md` may need the application in the key.

## Why the context was optional

- No production path creates an inbox without a context. `Repository.initInbox()` is
  the only production caller of `Delivery.newInbox()`, and it always calls
  `withContext(context().name())`. No other Spine repository calls `newInbox()`.
- The API still allows it. `Delivery.newInbox()` is public and not `@Internal`,
  `withContext()` is an optional builder step, and `Inbox.Builder.build()` does not
  check for it. One test uses this: `InboxDeliveriesSpec.inbox(type)`.
- History: until `820b042b119` (2026-10-27), `ShardMaintenanceProcess` and
  `AbstractStatefulReactor` built inboxes outside any repository, without a context.
  That commit removed those inboxes and added `withContext()` only to key the
  catch-up inboxes, as its Javadoc still says.

## Reviews

All three reviewers found nothing blocking. Applied:

- `kotlin-engineer`: the "registered later" case checks that the inbox of the first
  context stays the same instance; `SystemProjectionsIgTest` closes its contexts with
  `closeAll()`, so one failure does not leak the other; the `context` argument of
  `messageTo()` is named at the call sites; a builder is no longer called `context`.
- `review-docs`: `Delivery` docs no longer name `Inbox`; the `InboxDeliveries` Javadoc
  says that catch-up messages take their context from the job; the proto field comment
  and the fixture Javadocs are reworded.
- `spine-code-review`: only nits; its point about catch-up messages ignoring their
  stored context is covered by the `InboxDeliveries` Javadoc above.

Not applied: a shared helper for the "no shard observer" `Delivery` in `DeliverySpec`
(optional, would touch an existing test).
