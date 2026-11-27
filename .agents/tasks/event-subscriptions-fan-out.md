---
slug: event-subscriptions-fan-out
branch: claude/event-subscriptions-fan-out-16f8c0
owner: claude
status: in-progress
started: 2026-11-27
---

## Goal

`SubscriptionService` serves each event target of a topic in every Bounded Context of the
application whose tenancy fits the topic, not in the one context that happened to declare
the type last. Issue #1728 ("Proposal" section is the scope). Breaking: public API is removed.

## Context

- Issue: https://github.com/SpineEventEngine/core-jvm/issues/1728
- Base: `origin/master` `f559e52e4af` (`2.0.0-SNAPSHOT.810`). No open PRs touch these files.
- `SubscriptionService.of()` puts `stand.exposedTypes()` and `stand.exposedEventTypes()` of each
  context into one `TypeDictionary`; the last `put` wins. A topic whose types all map to one
  context goes to `SubscriptionImpl.serve(context, …)` and is subscribed only there.
- An event type that no context declares already takes `serveNoContext()` → `TopicRouting` →
  `holders(contexts())`: every context of the application, with no tenancy filter.
  `EventTap` passes only the events posted in its own context, so the fan-out never delivers
  an event twice.
- Tenancy and the subscription registry: `TenantAware` substitutes the single-tenant ID for
  a missing tenant, and a single-tenant registry ignores the current tenant. So today
  a tenantless topic fanned into a multi-tenant context lands in a slice that no event reaches,
  and a topic with a tenant fanned into a single-tenant context gets that context's events.
  `Stand.hasSubscription()` returns `false` in a context of the other tenancy once the service
  stops putting the subscription there.
- `EventProducingRepository` is more than `outgoingEvents()`: its defaults `filter()`,
  `postEvents()`, and `postIfCommandRejected()` are used by `AggregateRepository`,
  `ProcessManagerRepository`, `AggregateEndpoint`, and `PmEndpoint`. Only
  `InMemoryEventRegistry` reads the interface as a type.

## Decisions (proposed)

1. **Subscribing.** An entity target is served by its one serving context, checked with
   `admitted()` as now (tenant fit and registration, naming the context). An event target is
   served by every context with `isMultiTenant()` equal to "the topic has a tenant"; the tenant
   is checked once for the application with `admittedToAll()`, naming no context, as for
   an undeclared type today. A topic whose event targets fit no context is refused with
   `TENANT_ERROR` through the existing misfit path: `TopicTenantNotExpected` or
   `TopicTenantNotSpecified` is recorded in the first context of the application.
   An invalid event-only topic is refused with `INVALID_REQUEST` and written to the application
   log, as for an undeclared type today. An entity-only topic of one context still takes
   `serve(context, …)`.
2. **Activation and cancellation.** The same fit rule: a subscription whose event targets fit no
   context is refused with `TENANT_ERROR` (`SubscriptionTenantNotExpected`/`…NotSpecified`).
   Holders are still found by ID. `servesOnlyComplete()` still covers only the contexts that
   serve entity targets, so a subscription to an event type is served in the contexts that have
   it, as `SubscriptionServiceUnservedTopicSpec` requires (#1712/#1725).
3. **The dictionary** of `SubscriptionService` holds `exposedTypes()` only. The "Unable to find
   a Bounded Context for the event types" warning goes. `QueryService` and `CommandService`
   are untouched.
4. **Removed** (each grepped; no production reader left):
   - `Stand.exposedEventTypes()`, `Stand.registerTypeSupplier(EventProducer)`, the event-registry
     half of `Stand.registerTypeSupplier(Repository)` and of `Stand.close()`;
   - `EventRegistry`, `InMemoryEventRegistry` (both package-private);
   - `AbstractAssignee.registerWith()` override (only added the call);
     `AbstractEventReactor.registerIn(Stand)` (protected `@Internal` hook, no overrides);
   - `EventProducer.producedEvents()` and its overrides: `AbstractAssignee`, `AbstractCommander`,
     `AbstractEventReactor`, `Aggregate`, `ProcessManager`, and the test fixtures
     `TestEventReactor`, `ConferenceSetup`, `SignalDispatchingEntitySpec`;
   - `EventProducingRepository.outgoingEvents()` and its overrides in `AggregateRepository`
     and `ProcessManagerRepository`.
5. **Also removed (owner's answer, 2026-11-27: "Remove both too"):**
   - `EventProducingRepository` as a whole. Its posting defaults (`filter()`, `postEvents()`,
     `postIfCommandRejected()`) move into `AggregateRepository` and `ProcessManagerRepository`.
   - `AggregateClass.outgoingEvents()` and `ProcessManagerClass.outgoingEvents()`, with the
     tests that read them.
   - `rejections()` (`CommandHandlingClass`, its implementations, and `CommandReceptor`) and
     `reactionOutput()` (`ReactingClass` and its implementations), with their tests: once
     the above went, only tests read them (owner's answer, 2026-11-27: "Yes, remove them").
   - The command side too (owner, 2026-11-27: "Let's do this in this session"):
     `commandOutput()`, `outgoingCommands()` with `CommandingClass`, and what only fed them:
     `producedTypes()` of `ReceptorMap` and `EventReceivingClassDelegate`,
     `Receptor.producedMessages()`, and `MethodResults` with its test and fixture. The generic
     type parameters of the produced message classes stay: removing them would touch every
     receptor signature.
6. **Version:** breaking → next round version via `bump-version` (expected `.820`).
7. **Migration note:** `docs/migration/event-subscriptions-in-every-context.md`.

## Plan

- [x] New Kotlin spec `SubscriptionServiceEventFanOutSpec` (`application { … }` DSL, closed
      after each test, backticked names):
  - two single-tenant contexts post one event type, one declaring it: one subscriber gets both;
  - a multi-tenant and a single-tenant context post one type: a topic with a registered tenant
    is held and served by the multi-tenant one only, a topic without a tenant by the
    single-tenant one only; neither is refused;
  - a rejection that one context declares and another throws undeclared (Kotlin, no `@Throws`)
    reaches the subscriber from both;
  - a topic that fits no context is refused with `TENANT_ERROR` and recorded (with a tenant in
    a single-tenant application; without one in a multi-tenant application);
  - activation and cancellation of a subscription held by several contexts; both refused with
    `TENANT_ERROR` when no context fits.
- [x] Run the new spec against the old code and record which cases fail.
- [x] Update the existing specs:
  - `SubscriptionServiceTest.PickContextBy`: the three event cases become "no context is picked
    for an event type";
  - `TenantOfRequestSpec`: `not check the tenant of a topic that no context serves, if no context
    is multi-tenant` becomes a refusal; rename the "no context serves" cases of event types;
  - `SubscriptionServiceMultiTargetSpec`, `SubscriptionServiceUnservedTopicSpec`,
    `SubscriptionServiceErrorCaseSpec`: wording of the event-type cases ("no context serves");
  - drop `producedEvents()` assertions/overrides (`ProcessManagerLifecycleSpec` and fixtures).
- [x] Implement 1–3 in `SubscriptionService` (`TopicRouting`, `SubscriptionDelegate`), update
      the class Javadoc and the method docs.
- [x] Remove the API in 4; update the Javadoc of `Stand`, `EventProducer`, and the repositories.
- [x] Targeted run: `:server:test --tests '*SubscriptionService*' --tests '*TenantOfRequest*'
      --tests '*Stand*' --tests '*ProcessManager*' --tests '*Aggregate*'`; read the counts.
- [x] Bump the version; write the migration note.
- [x] `pre-pr`: `./gradlew build dokkaGenerate`, reviewers; read the real test counts.
- [ ] Show the diff; commit, push, and open the PR ("Fixes #1728") only when asked.

## Out of scope

- #1748: `@External` and receptors taking events from every context.
- Events of a `ThirdPartyContext` never reach subscribers: it publishes straight to the
  broker, the other contexts receive the events as external, and `EventTap` skips external
  events. Confirmed with a throwaway probe (not committed); filed as #1750.

## Log

- 2026-11-27 — plan written; the owner chose to remove `EventProducingRepository` and the two
  `outgoingEvents()` too; implementation started.
- 2026-11-27 — `SubscriptionServiceEventFanOutSpec` on the old code: 10 tests, 8 failed as
  expected. Passed: cancelling in both contexts (only one held it), and the tenantless topic in
  the mixed application (the single-tenant context was added last). The multi-tenant topic was
  refused with `TENANT_ERROR` (#1727 review case); unfit topics were subscribed or answered
  `UNSUPPORTED_REQUEST`.
- 2026-11-27 — implemented; targeted `:server:test` (subscription, tenant, client, stand,
  procman, aggregate, entity, event, command packages): 1119 tests, 0 failed. The posting
  defaults of `EventProducingRepository` moved into `SignalDispatchingRepository` as `internal`
  members; the repositories' `eventBus()` went with the interface. Version `.820` (not committed).
- 2026-11-27 — `rejections()` and `reactionOutput()` removed with their four tests (targeted
  `:server:test`: 1138 tests, 0 failed). Reviewer suggestions applied at the owner's request:
  lazy fitting contexts, `refuseMisfit()` throws `IllegalStateException`, Javadoc split by
  error case, spec renames, migration note section 3 rewritten around `addEventListener`.
- 2026-11-27 — owner's request: the duplicated target-index check of `Stand.subscribe()` and
  `SubscriptionRecord.of()` moved into the internal `Topic.checkHasTarget()`
  (`stand/TopicTargets.kt`), with `TopicTargetsSpec`.
- 2026-11-27 — filed #1750: events of a `ThirdPartyContext` never reach subscribers.
- 2026-11-27 — final `./gradlew build dokkaGenerate`: `:server:test` 2725 tests, 0 failed;
  `:server-testlib` 162, `:server-otel` 22; `core`/`client` up to date from the previous green
  run. Reviewers (`spine-code-review`, `kotlin-engineer`, `review-docs`, then delta re-reviews):
  no must-fix. Committed on `master`; the trial merge with #1749 conflicts only on one import
  line of `SubscriptionRecord.java` and the version files.
- 2026-11-27 — Copilot: listeners also get external events; the note's examples now skip
  them (`4692d70527e`, thread resolved). Then the command-side declared outputs were removed.
- 2026-11-27 — owner: the identical `onRoutingFailed()` of `AggregateRepository` and
  `ProcessManagerRepository` moved into `SignalDispatchingRepository`; `postIfCommandRejected()`
  is `private` now. Command-side removal: targeted `:server:test` 1002 tests, 0 failed.
- 2026-11-27 — owner: `SubscriptionServiceUnservedTopicSpec` renamed (`git mv`) to
  `SubscriptionServiceEventHoldersSpec`: no event topic is unserved any more.
- 2026-11-27 — owner: the type parameter of produced message classes dropped from the receptor
  model (`Receptor`, `AbstractReceptor`, `CommandReceptor`, `EventReceptor`, `RejectionHandler`,
  `ReceptorMap`, `EventReceivingClassDelegate`, `CommandHandlingClass`,
  `AbstractCommandHandlingClass`) and from every use; `EmptyClass` stays (`EnvelopeWithOrigin`).
