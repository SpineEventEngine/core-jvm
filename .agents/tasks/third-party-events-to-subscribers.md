---
slug: third-party-events-to-subscribers
branch: claude/event-subscriptions-fan-out-fix-a10805
owner: claude
status: planned
started: 2026-11-27
---

## Goal

A client subscription to an event type that a `ThirdPartyContext` emits gets each such event
exactly once, whether or not a Bounded Context of the application takes the event.
Issue #1750.

## Context

- Issue: https://github.com/SpineEventEngine/core-jvm/issues/1750
- Base: `claude/event-subscriptions-fan-out-16f8c0` (PR #1752, `baef57e9f95`,
  `2.0.0-SNAPSHOT.820`). There, a subscription to an event type is kept in every context whose
  tenancy fits the topic, so the context of a `ThirdPartyContext` already holds it.
- `ThirdPartyContext.emittedEvent()` publishes the event straight to the integration broker.
  It skips its own `EventBus` since 2019 (`ae594a8ecd8`) so that the event is not stored:
  a bus stores each posted event in its `EventStore`, even a dead one (`DeadEventTap`).
- `EventTap` (the `Stand` listener of a bus) streams only events that did not come through
  the broker, so a subscription held by several contexts gets each event once. The contexts that
  take the event through the broker skip it, and the third-party context never sees it.
- #1748 removes `@External` and lets receptors take events from every context. It keeps the rule
  that one context streams an event: the one where it was posted. This fix relies on that rule
  only, not on how receptors take events.
- `Bus.post()` calls its listeners in the posting thread, and a multi-tenant subscription registry
  reads the current tenant. So a direct call must run for the tenant of the event.

## Decisions (proposed)

1. `ThirdPartyContext.emittedEvent()` keeps publishing to the broker, and also passes the event
   to the `Stand` listener of its own context, for the tenant of the actor context
   (`TenantAwareRunner`). The event is not posted to the bus, so it is still not stored.
2. The KDoc of both `emittedEvent()` overloads says that the event also goes to the client
   subscriptions to its type.
3. No migration guide: a bug fix with no API change. Version `.820` → `.821`.
4. `ThirdPartyContext` keeps its `name` and tenancy instead of a `BoundedContextBuilder`, which it
   held only for the name once the application was built. `Application.Builder.add()` gets
   a builder from `newBuilder()`.
5. No `try`/`catch` around the stand call: `UpdateHandler.deliverUpdate()` already logs whatever
   a subscriber throws, so a failing subscriber does not fail the emission. A spec case checks it.

## Plan

- [x] `ThirdPartyContext.emittedEvent()`: hand the event to the own `Stand` in the tenant scope.
- [x] New `ThirdPartyContextSubscriptionSpec.kt` in `server/src/test/kotlin/io/spine/server/integration/`:
  - a subscriber gets the event once while another context reacts to it;
  - a subscriber gets the event when no context takes it;
  - multi-tenant: a subscriber of the event's tenant gets it, one of another tenant does not;
  - a failing subscriber does not fail the emission.
- [x] `ThirdPartyContext` keeps `name` instead of the builder (decision 4).
- [ ] Bump `version.gradle.kts` to `.821`, regenerate the dependency reports.
- [ ] `./gradlew build dokkaGenerate`; reviewers per `pre-pr`.
- [ ] PR targets `claude/event-subscriptions-fan-out-16f8c0` (stacked on #1752); delete this file
  on merge.
