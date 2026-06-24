/*
 * Copyright 2026, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.server.trace.otel

import io.spine.core.SignalId
import java.util.UUID

/**
 * The canonical form of a UUID string, e.g. `123e4567-e89b-12d3-a456-426614174000`.
 */
private val uuidPattern =
    Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")

/**
 * Derives a deterministic OpenTelemetry trace ID from the given root signal ID.
 *
 * All signals caused by a single root signal share the same root ID, and
 * therefore the same trace ID. This reproduces the "one trace per causal chain"
 * behavior: a command and all the events and further commands it triggers appear
 * under a single trace.
 *
 * The result is a 32-character (16-byte) lowercase hex string, as required by
 * the OpenTelemetry [trace ID][io.opentelemetry.kotlin.tracing.SpanContext.traceId]
 * format. Each half of the backing UUID is zero-padded to 16 hex characters so
 * that the value is always exactly 32 characters long.
 *
 * Signal IDs are version 3 or version 4 UUIDs, whose version and variant bits keep
 * both halves non-zero; the derived trace ID is therefore always a valid (non-zero)
 * OpenTelemetry trace ID.
 *
 * @see spanIdOf
 */
internal fun traceIdOf(rootSignalId: SignalId): String {
    val uuid = uuidOf(rootSignalId)
    return "%016x%016x".format(uuid.mostSignificantBits, uuid.leastSignificantBits)
}

/**
 * Derives a deterministic OpenTelemetry span ID for the synthetic parent span of
 * a causal chain identified by the given root signal ID.
 *
 * The emitted handler spans use this value as their parent, so that they form a
 * coherent tree rooted at the chain's origin. The result is a 16-character
 * (8-byte) lowercase hex string.
 *
 * @see traceIdOf
 */
internal fun spanIdOf(rootSignalId: SignalId): String {
    val uuid = uuidOf(rootSignalId)
    return "%016x".format(uuid.leastSignificantBits)
}

/**
 * Converts the given signal ID to a [UUID].
 *
 * Signal IDs are normally canonical UUID strings. For an ID that is not a UUID,
 * a stable name-based UUID is derived from its bytes, so that the mapping remains
 * deterministic for any input.
 */
private fun uuidOf(signalId: SignalId): UUID {
    val value = signalId.value()
    return if (uuidPattern.matches(value)) {
        UUID.fromString(value)
    } else {
        UUID.nameUUIDFromBytes(value.encodeToByteArray())
    }
}
