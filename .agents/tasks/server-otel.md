# Task: `server-otel` — OpenTelemetry-based Spine tracer

Status: **implemented** (branch `server-otel`). Delete this file on merge to master.

## Context

`gcloud-jvm`'s `stackdriver-trace` module implements Spine's tracing SPI against
Google Cloud's proprietary Stackdriver / Cloud Trace gRPC API. Google now steers
users to OpenTelemetry, so we are dropping the proprietary coupling.

OpenTelemetry has no direct Google Cloud binding, so the replacement is a generic,
**backend-agnostic** module — `server-otel` — that maps Spine signal processing to
OTel spans and lets the *consumer* choose the backend (OTLP collector, the Google
Cloud OTel exporter, Jaeger, in-memory, …). It lives in **core-jvm**, next to the
Trace SPI it implements (`io.spine.server.trace`), and is written in **Kotlin against
the Kotlin OpenTelemetry API** (`io.opentelemetry.kotlin`, currently alpha /
`@ExperimentalApi`) to be Kotlin-only from day one, with an eventual KMP move in mind.

Retiring `stackdriver-trace` in `gcloud-jvm` is a **separate** follow-up (different
repo); this task only adds the new module.

## What was built

New module `core-jvm/server-otel`, package `io.spine.server.trace.otel`:

- `OtelTracerFactory` — `TracerFactory` built via `newBuilder().setOpenTelemetry(otel)`.
  Holds a caller-supplied `OpenTelemetry` and derives one OTel `Tracer`. `close()` only
  flips `isOpen()`; it does **not** shut down the caller-owned `OpenTelemetry`.
- `OtelTracer` — `AbstractTracer`; each `processedBy(...)` starts and immediately ends
  one span. `close()` is a no-op (export is the SDK's job).
- `SignalSpan` — maps `(context, signal, receiver, receiverType)` to a span: start =
  signal timestamp, end = now, name `"<Receiver> handles <Signal>"` (≤128 chars),
  attributes, and a synthetic sampled parent context carrying the derived trace ID.
- `SpanAttribute` — six attributes under the `spine.` namespace: `spine.bounded_context`,
  `spine.tenant`, `spine.entity.id`, `spine.signal.id`, `spine.entity.type`,
  `spine.signal.type` (values truncated to 256 chars). Renamed from the old
  `spine.io/Xxx` keys to OTel-idiomatic dotted form (no backward-compat constraint,
  since `stackdriver-trace` is being retired).
- `TraceIds` — deterministic trace/span IDs from the **root** signal ID, so all signals
  of one causal chain share a trace. Each UUID half is zero-padded to 16 hex chars
  (32-char trace ID / 16-char span ID), fixing the un-padded `Long.toHexString` gap in
  the old code; non-UUID IDs fall back to a stable name-based UUID.

Behavioral parity with `stackdriver-trace`, minus the GCP coupling: export, batching,
and sampling are delegated to the caller's `OpenTelemetry` SDK (no manual batch-write,
no GCP credentials, no sync/async service, no `forbidMultiThreading`). The parent
context is marked **sampled** so a parent-based sampler records the spans, matching the
old always-export behavior.

## Build wiring

- `buildSrc/.../io/spine/dependency/lib/OpenTelemetryKotlin.kt` — version `0.4.0`,
  artifacts `api` (production), and `core` + `implementation` (test SDK). No BOM is
  published, so versions are embedded in the coordinates.
- `server-otel/build.gradle.kts` — `module` + `io.spine.core-jvm`; `api(project(":server"))`
  + `api(OpenTelemetryKotlin.api)`; tests use the SDK + `testFixtures(project(":server"))`.
  The `@ExperimentalApi` opt-in is per-file (`@file:OptIn(...)`).
- `settings.gradle.kts` — registers `server-otel`.

## Tests (`server-otel/src/test/kotlin`, all green — 20 specs)

- `TraceIdsSpec` — determinism, 32/16-char hex, zero-padding, non-UUID fallback.
- `OtelTracerFactorySpec` — builder requires `OpenTelemetry`; `trace()` returns
  `OtelTracer`; `isOpen()`/`close()`.
- `OtelTracerSpec` — `processedBy` records a span with the expected name, start/end,
  attributes, derived trace ID, and synthetic parent span ID; two handlings share a trace.
- `OtelTracingSpec` — integration: wires the factory into the `Airport` sample context
  (reused from `server` test fixtures), posts a command, and asserts the whole causal
  chain lands under a single trace ID.
- `given/RecordingSpanProcessor`, `given/TestOtel` — in-memory recording SDK.

## Verification

`./gradlew :server-otel:build :server-otel:dokkaGenerate :server-otel:detektMain
:server-otel:detektTest` — all pass (tests, Kover, Checkstyle, PMD, Detekt, Dokka).
Requires JDK 17+ (`JAVA_HOME=.../amazon-corretto-17.jdk/Contents/Home`).

## Out of scope (follow-ups)

- Retiring / removing `stackdriver-trace` in `gcloud-jvm`.
- A GCP-specific convenience module wiring `server-otel` to Cloud Trace.
- Compiling to non-JVM KMP targets (kept KMP-friendly; stays JVM here).
