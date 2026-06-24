# Spine OpenTelemetry tracing (`server-otel`)

> ⚠️ **Experimental module.** `server-otel` is built on the alpha
> [Kotlin OpenTelemetry API][otel-kotlin] (`io.opentelemetry.kotlin`). Its public API is
> marked with the `@ExperimentalOtelTracing` Kotlin opt-in marker and with
> `@io.spine.annotation.Experimental`, and may change in a backward-incompatible way, or
> be removed, in a future release.

## Overview

`server-otel` implements the Spine Trace API (`io.spine.server.trace`) on top of
OpenTelemetry. It records the handling of each signal (a command or an event) by an entity
as an OpenTelemetry **span**, and groups all signals of a single causal chain under one
**trace**.

The module is **backend-agnostic**: it maps Spine signals onto OpenTelemetry spans and
leaves the export to a caller-supplied `OpenTelemetry` instance. To send traces to a
particular backend — an OTLP collector, Google Cloud Trace, Jaeger, etc. — configure the
corresponding span processor and exporter on that instance. The module thus replaces the
Google-Cloud-specific `stackdriver-trace` module without coupling to Google Cloud.

Each handler invocation becomes its own span that carries the following attributes:

| Attribute | Meaning |
|-----------|---------|
| `spine.bounded_context` | the bounded context that handled the signal |
| `spine.tenant` | the tenant, as compact JSON |
| `spine.entity.id` | the ID of the receiving entity |
| `spine.entity.type` | the type URL of the receiving entity |
| `spine.signal.id` | the ID of the handled signal |
| `spine.signal.type` | the type URL of the handled signal |

## Dependency

```kotlin
dependencies {
    implementation("io.spine:spine-server-otel:$spineVersion")

    // Plus a Kotlin OpenTelemetry SDK and exporter of your choice — see the
    // OpenTelemetry Kotlin documentation (linked under "Usage") for the artifacts.
}
```

## Usage

Tracing is configured once, when the server starts. Because the API is experimental, opt in
explicitly with `@OptIn(ExperimentalOtelTracing::class)`:

```kotlin
import io.opentelemetry.kotlin.OpenTelemetry
import io.spine.environment.Production
import io.spine.server.ServerEnvironment
import io.spine.server.trace.otel.ExperimentalOtelTracing
import io.spine.server.trace.otel.OtelTracerFactory

@OptIn(ExperimentalOtelTracing::class)
fun configureTracing(openTelemetry: OpenTelemetry) {
    val factory = OtelTracerFactory(openTelemetry)
    ServerEnvironment.`when`(Production::class.java)
        .use(factory)
}
```

`openTelemetry` is a configured `OpenTelemetry` instance — with a span processor and an
exporter — that **you** create and own; see the [Kotlin OpenTelemetry documentation][otel-kotlin]
for SDK and exporter setup. Closing the factory does not shut that instance down.

The instrumentation scope name defaults to `io.spine.server.trace.otel`. Pass a second
argument to `OtelTracerFactory` to override it:

```kotlin
OtelTracerFactory(openTelemetry, instrumentationScopeName = "my.app.tracing")
```

## Notes

- Tracing applies to entities — aggregates, process managers, and projections. Standalone
  commanders, reactors, and subscribers are not traced.
- Each handling is recorded as its own span rather than as a span event: OpenTelemetry
  [deprecated the Span Events API][span-events] in March 2026 in favor of log-based events,
  so per-handling data is carried by dedicated spans and their attributes.

[otel-kotlin]: https://opentelemetry.io/docs/languages/kotlin/
[span-events]: https://opentelemetry.io/blog/2026/deprecating-span-events/
