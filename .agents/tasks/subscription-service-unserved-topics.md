---
slug: subscription-service-unserved-topics
branch: claude/github-issue-1712-fd97f3
owner: claude
status: in-progress
started: 2026-11-12
---

## Goal

`SubscriptionService` activates and cancels a subscription to a topic that no Bounded Context
serves in exactly the contexts that have the subscription, under the tenant of its topic, and
refuses the request only if none has it. Issue #1712.

## Context

- Issue: https://github.com/SpineEventEngine/core-jvm/issues/1712
- For such a topic, `subscribe()` registers the subscription in every context of the service.
  `activate()` then activated it in every context in turn: a context without the subscription
  threw, the contexts before it stayed activated, and the client received
  `UNSUPPORTED_REQUEST`. Contexts disagree when one context is added to two services.
- `cancel()` already found the holders first, but called `Stand.hasSubscription()` outside
  a tenant-aware operation, which throws in a multitenant context (`INTERNAL_ERROR`).

## Decisions

1. `Stand.hasSubscription()` takes the `Subscription` (was `SubscriptionId`) and looks it up under
   the tenant of its topic, like `activate()` and `cancel()`. The method is `@Internal`.
2. Activation and cancellation share one no-context path in `SubscriptionDelegate`: validate the
   subscription (`INVALID_REQUEST`), find the holders (none → `UNSUPPORTED_REQUEST`), serve each
   holder, and answer with one acknowledgement.
3. A cancellation of an invalid subscription that no context serves is now refused with
   `INVALID_REQUEST`, like an activation (it was `UNSUPPORTED_REQUEST`).

## Plan

- [x] Specs: `SubscriptionServiceUnservedTopicSpec` (single-tenant: a context in two services;
      multitenant: activate, cancel, another tenant); `SubscriptionServiceErrorCaseSpec`:
      an invalid subscription when cancelling.
- [x] `Stand.hasSubscription(Subscription)` under the tenant of the topic.
- [x] `SubscriptionDelegate.serveNoContext()` shared by activation and cancellation.
- [x] Bump the version (`.702`, not breaking).
- [x] Verify with `./gradlew check dokkaGenerate`.
- [x] `./gradlew build dokkaGenerate` after the bump.

## Log

- 2026-11-12 — plan written; implementation started.
- 2026-11-12 — before the fix, 3 of the new cases failed as expected: single-tenant activation
  (`UNSUPPORTED_REQUEST`), multitenant cancellation (`INTERNAL_ERROR`), invalid cancellation
  (`UNSUPPORTED_REQUEST`). A context that exposes no type is unknown to the service, so each
  spec context has a repository.
- 2026-11-12 — `./gradlew check dokkaGenerate` green: `server` 2597 tests, 0 failures
  (1 skipped); `client` 186, `core` 188, `server-testlib` 163. Review fixes re-checked with
  the `SubscriptionService*` specs (47), `:server:detekt`, and `:server:dokkaGenerate`.
  Version bump and commits wait for the owner.
- 2026-11-12 — bumped to `.702`; `./gradlew build dokkaGenerate` green with the same counts;
  dependency reports updated.
