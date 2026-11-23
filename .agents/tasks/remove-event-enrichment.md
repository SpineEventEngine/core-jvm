---
slug: remove-event-enrichment
branch: claude/remove-enrichment-feature-70f481
owner: claude
status: in-progress
started: 2026-11-23
---

## Goal

Remove the event enrichment feature (the "Content Enricher" pattern) before 2.0.0 GA.
`Enrichment.Container` keeps one message per type, which limits the feature, and the
current users of the Spine SDK do not use it.

## Context

- `spine.core.Enrichment` is referenced by one Protobuf field only:
  `EventContext.enrichment = 5`.
- No 2.x sibling repository uses the API (only the legacy `core-java-1x` does).
  The `(enrichment_for)` option is already removed from `options.proto` in `base`.
- Two places use the feature, and both need a replacement:
  - The System context: `SystemEnricher` adds the `Command` to `CommandScheduled`,
    and the `ScheduledCommand` projection reads it with `context.get(Command.class)`.
  - The `AirportContext` test fixture (tracing tests): `TimetableRepository` routes
    `FlightRescheduled` and `FlightCanceled` by an enriched `AirportId`.
- `EventContextMixin.readValue()` switches on the field *index*. Removing field 5
  moves `external` and `rejection` from 11 and 12 to 10 and 11.
- `EntityEventStorage.write()` validates the event only as a side effect of
  `clearEnrichments()` rebuilding it. Three tests expect a `ValidationException`.
- The rebuild in `clearEnrichments()` also validated the *contents* of the context
  (`EventContext.Builder.build()`: timestamp, producer, origin), in both
  `EntityEventStorage` and `DefaultEventStore`. The stores no longer check that,
  as the owner decided (2026-11-23). `Event.context` is `(required)` without
  `(validate)`, so `checkValid()` and the `EventBus` validator check only that the
  context is set — as `CommandValidator` does for commands. The framework builds
  every event context with a validating `build()` (`EventFactoryBase.createContext()`),
  so a bad context comes only from hand-built (`buildPartial()`) events or from
  a non-Spine publisher on the integration broker. Validating contexts where such
  input enters, in both buses, is a separate concern. The `EntityEventStorage` KDoc
  no longer promises that an event without a producer is rejected.

## Plan

### Protobuf

- [x] Delete `core/src/main/proto/spine/core/enrichment.proto`.
- [x] `EventContext`: remove the field and its import;
      reserve it as `reserved 5, 7;` and `reserved "enrichment", "rejection_context";`.
- [x] `CommandScheduled`: add `core.Command payload = 3 [(required) = true];`
      (the name mirrors `CommandReceived.payload`).

### `core`

- [x] Delete `Enrichments` and `EnrichableMessageContext`.
- [x] `EventContextMixin`: drop `EnrichableMessageContext`; fix the `readValue()` indexes.
- [x] `EventMixin`: remove `clearEnrichments()` and `clearAllEnrichments()`.

### `server`

- [x] Delete the `io.spine.server.enrich` package, `EventEnricher`, `SystemEnricher`,
      and `EnrichableMessageEnvelope`.
- [x] `EventBus` and its `Builder`: remove the enricher and its accessors;
      dispatch the posted event as is.
- [x] `BoundedContextBuilder`: remove `enrichEventsUsing()`.
- [x] `EventEnvelope`: remove `toEnriched()`, `isEnrichmentEnabled()`, and the helpers.
- [x] `SystemContext`: stop installing the enricher.
- [x] `FlightRecorder.onScheduled()`: put the command into `CommandScheduled.payload`.
      `ScheduledCommand` stores the payload without its `system_properties`. The scheduler
      stamps `system_properties.scheduling_time` on the command before it notifies the
      watcher, and the record keeps that time in its own `scheduling_time` field. The old
      enrichment read the command as posted (from `CommandLog`), which carries no stamp.
      Storing the payload as is failed `ScheduledCommandTest` (2 cases).
- [x] `DefaultEventStore`: store events as is (see Context for why no validation).
- [x] `EntityEventStorage.write()`: validate with `checkValid()` explicitly,
      instead of through `clearEnrichments()`; update the KDoc.
- [x] `RecentEventHistory.toItem()`: drop the enrichment wording from the KDoc.

### Tests and fixtures

- [x] Delete: `EnrichmentsSpec`, `EnrichmentsTest`, `EnricherBuilderTest`, `SingularFnTest`,
      `EventBusEnrichmentTest`, `EventEnricherIntegrationTest`, the
      `io.spine.server.enrich.given` fixtures, the `spine/test/enrichment/*.proto` files,
      `GivenEnrichment` with its test (`core-testlib`).
- [x] Trim: `EventMixinSpec`, `SignalExtrasSpec`, `EventTest`, `EventBusTest`,
      `EventBusBuilderTest`, `DefaultEventStoreTest`, `EntityEventStorageSpec`
      (keep the validation cases; the explicit-identifier case now checks storing as is),
      `BlackBoxTest`, `GivenEvent`.
- [x] `enrichments_test_events.proto`: keep only `EtProjectCreated` (`GivenEvent.message()`);
      `git mv` it to `core_test_events.proto`, as "enrichments" would mislead.
- [x] Airport fixture: add `AirportId from` to `FlightRescheduled` and `FlightCanceled`;
      `FlightAggregate` fills it from its state; `TimetableRepository` routes by it;
      remove `FlightRepository.departureAirport()`.

### Wrap-up

- [x] Bump the version (breaking): `.760`, as `.750` was taken by #1731; then `.770`,
      as #1732 took `.760` on `master`.
- [x] `./gradlew clean build dokkaGenerate`; read the test counts.
- [x] `EventContextMixinSpec`: `readValue()` now must equal `getField()` for every field,
      with `external` and `rejection` set; the old 11/12 indexes fail it (mutation-checked).
- [x] Reviewers: `spine-code-review`, `review-docs`, `kotlin-engineer`; the follow-up
      findings (context validation, explicit-identifier rejection test, the
      `ScheduledCommand` comment) are addressed, the first by the KDoc (see Context).

Not changed: the ADR (a historical record), the archived and other task notes,
`.idea/dictionaries`. No migration guide, as no user relies on the feature.

## Log

- 2026-11-23 — feature mapped; plan written and approved.
- 2026-11-23 — implementation done (uncommitted); reviewers approve.
- 2026-11-23 — `clean build dokkaGenerate`: `ScheduledCommandTest` failed (2 cases, the
  scheduling-time stamp); fixed in `ScheduledCommand`. Rebuild green: `core` 172,
  `core-testlib` 21, `client` 186, `client-testlib` 2, `server` 2589 (1 skipped),
  `server-testlib` 162, `server-otel` 22 tests, 0 failures.
- 2026-11-23 — committed; `master` merged (#1731). Pre-PR review: the stores lost the
  context-content check; owner chose the KDoc fix. `master` merged again (#1732, which
  took `.760`), so re-bumped to `.770`.
