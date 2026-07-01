# Task: `reaction-routing-order` — deliver events to subscribers before reactors

Status: **implemented** (staged on branch `claude/bold-ellis-76a146`, pending commit/PR).
Fixes [core-jvm#925](https://github.com/SpineEventEngine/core-jvm/issues/925).
Delete this file on merge to master.

## Context

Issue #925: an event produced as a **reaction** to an origin event can be **routed before
the origin event has updated the projection that the reaction routing depends on**, so the
reaction is misrouted (to an empty target set) and silently lost.

Canonical reproduction — test `server/src/test/kotlin/io/spine/server/route/EventReactionRoutingSpec.kt`
with fixture `server/src/testFixtures/kotlin/io/spine/server/given/context/users/UsersContext.kt`:

- Origin **A** = `RUserSignedIn` (carries `user` + `session`).
- `SessionProjection` `@Subscribe`s to A → creates the projection, sets `userId` (routed by `e.session`).
- `UserAggregate` `@React`s to A → produces reaction **B** = `RUserConsentRequested` (carries **only** `user`).
- `SessionRepository` routes **B** by a **read-model state query** —
  `findByUserId(e.user)` = `recordStorage().index(userId == …)` — which requires A's effect on
  `SessionProjection` to be **committed**.

The test had been `@Disabled` for years and was re-enabled + "Disabled the test back" **twice** in
history — a persistently unfixed ordering hazard. Enabling it reproduces the failure **~34/50**.

## Root cause (verified against source)

1. Event routing is **eager and synchronous at post time**:
   `EventDispatchingRepository.dispatch` computes `route(event)` then `dispatchTo`, so B's target
   is computed the instant B is posted.
2. B is posted **inside** A's delivery: A → aggregate delivery → `AggregateEndpoint.storeAndPost`
   → `EventProducingRepository.postEvents` → `eventBus().post(B)`.
3. Within one delivery page, A is delivered to **both** its projection target and its aggregate
   target. Delivery order is decided by `LiveDeliveryStation.deduplicateAndSort`, which sorts by
   `InboxMessageComparator.chronologically` = (`whenReceived`, `version`, `uuid`); then
   `Segment.groupByTargetType` preserves that order.
4. `InboxPart.store` stamps `whenReceived` and `version` **per inbox write**, in the order the bus
   iterates dispatchers — a `HashMultimap` in `DispatcherRegistry`. **That hash order is the
   non-determinism.** When the aggregate target is served before the projection target, B is routed
   before `SessionProjection.userId` is committed → the query returns empty → B is lost →
   `userConsentRequested` stays `false`.

## Decision

The maintainer chose a **framework fix (ordering guarantee)** over declaring the behavior
by-design. Two framework approaches were designed and adversarially reviewed:

- **Rejected — buffer/defer the reaction post** (buffer bus posts during delivery, flush after the
  page, gated by a thread-local "inside delivery" flag): **unsound**. The thread-local silently
  no-ops under sharded/multi-node delivery and breaks on the async `new Thread`-per-message hop
  (`LocalDispatchingObserver`); and buffering the *post* converts a durable, at-least-once reaction
  (event written to the inbox eagerly) into a volatile at-most-once one, lost if an exception occurs
  before the flush.
- **Chosen — deterministic intra-page ordering**: within a delivery page, deliver an event to its
  **subscriber** targets before its **reactor** targets, grouped by originating signal. Then A's
  projection commits `userId` before the aggregate produces B. Sound under sync/async/sharded
  delivery, no new shared or thread-local state, preserves durable inbox writes. Coherent semantic:
  *an event updates its read models before its reactors fire.*

## What was built

- `server/src/main/java/io/spine/server/delivery/LiveDeliveryStation.java` —
  `deduplicateAndSort` now returns `subscribersBeforeReactors(result)`. The new
  `@VisibleForTesting` `subscribersBeforeReactors(List<InboxMessage>)` groups the already
  chronologically sorted messages by originating signal (`originOf` = the delivered event's/command's
  id), preserving the first-seen (chronological) group order via `LinkedHashMap`; within each group
  it emits `UPDATE_SUBSCRIBER` messages first (stable), then the rest (stable). Uses the existing
  `InboxLabel` distinction (`UPDATE_SUBSCRIBER` vs `REACT_UPON_EVENT`/`IMPORT_EVENT`).

  **Why a grouped reorder, not a `Comparator`:** a comparator that orders by label only for
  same-origin messages (and chronologically otherwise) is **not transitive** — TimSort throws
  `IllegalArgumentException: Comparison method violates its general contract` intermittently.
  `InboxMessageComparator` is therefore left untouched; the reorder is a stable, non-comparator
  post-pass.

- `server/src/test/kotlin/io/spine/server/route/EventReactionRoutingSpec.kt` — removed `@Disabled`
  (and the now-unused import); refreshed the KDoc. Now passes deterministically.

- `server/src/test/kotlin/io/spine/server/delivery/LiveDeliveryOrderingSpec.kt` — new deterministic
  unit test (5 cases) exercising `subscribersBeforeReactors` directly with crafted pages
  (reactor-before-subscriber, already-ordered, two-origin grouping, only-subscribers-precede,
  single message), so the guarantee is verified without relying on the hash-random dispatch order.

## Verification

- `./gradlew :server:test --tests "io.spine.server.route.EventReactionRoutingSpec"` — green,
  50/50 repetitions (was ~34/50 failing).
- `./gradlew :server:test --tests "io.spine.server.delivery.LiveDeliveryOrderingSpec"` — green (5/5).
- Full `./gradlew build` — green: all module tests + static analysis (Checkstyle, PMD, ErrorProne,
  Kover), no regressions. Requires JDK 17 (`JAVA_HOME=.../amazon-corretto-17.jdk/Contents/Home`).

## Scope & caveats

- Fixes reactions whose routing depends on a read model of the **same** origin event (#925) or of an
  **earlier, already-delivered** event.
- A reaction depending on a read model of a **different, concurrent** event in the same page remains
  eventually consistent by contract — inherently unfixable by in-JVM ordering, out of scope.
- **The reorder acts on a single delivery page.** It establishes the order when the origin's
  subscriber and reactor deliveries share a page — the default single-shard delivery with the default
  page size (500), which is the reported #925 scenario. Two known residuals fall back to
  eventual consistency:
  - **Same shard, split pages** — `InboxStorage.readAll` reads by `(received_at, version)`, which
    follows the hash-random inbox-write order, so a `pageSize=1` (or an exact page-boundary split)
    can still read the reactor record first. Closing this needs the order established at
    dispatch/write time (before paging), which storage's version-ordered reads would then preserve.
  - **Different shards** — when the projection and reactor entities hash to different shards (opt-in
    multi-shard / multi-node), their deliveries run independently; no in-JVM ordering can serialize
    them. This is the read side's eventual-consistency contract.
  A guarantee spanning page/shard boundaries requires ordering the deliveries **before** partitioning
  (see follow-ups) — a larger change to bus dispatch order, deferred out of this fix. Raised by the
  PR #1640 review (P1).
- Cross-origin interleaving within a page changes from strict microsecond-chronological to "grouped
  by originating signal, earliest-origin first" — a refinement of an already reordered,
  eventually-consistent delivery; independent events carry no cross-entity ordering guarantee.

## Out of scope (follow-ups)

- **Order same-origin deliveries before page/shard partitioning** (PR #1640 review, P1). Establish
  subscriber-before-reactor order at dispatch/write time so it survives any page size within a shard.
  The accurate subscriber/reactor signal is the `InboxLabel`, known only after routing; at dispatch
  time the only classifier is `instanceof EventProducingRepository`, which misses standalone
  `AbstractEventReactor`s and changes global bus dispatch order — hence a deliberate design task, not
  a fold-in. The cross-shard case stays eventually consistent regardless.
- Documenting, in the routing guide, that routing a reaction by querying read-model state is only
  reliable for same-origin dependencies (otherwise carry the routing key on the event).
