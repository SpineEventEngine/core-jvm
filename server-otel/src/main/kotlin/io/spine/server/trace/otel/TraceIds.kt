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

@file:OptIn(ExperimentalUuidApi::class)

package io.spine.server.trace.otel

import io.spine.core.SignalId
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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
 * The result is the 32-character (16-byte) lowercase hex form required by the
 * OpenTelemetry [trace ID][io.opentelemetry.kotlin.tracing.SpanContext.traceId]
 * format.
 *
 * The derived trace ID is always a valid (non-zero) OpenTelemetry trace ID:
 * canonical UUID signal IDs (versions 3 and 4) carry non-zero version and variant
 * bits, and the fallback for non-UUID IDs forces both 64-bit halves to be non-zero.
 *
 * @see spanIdOf
 */
internal fun traceIdOf(rootSignalId: SignalId): String =
    rootSignalId.toUuid().toHexString()

/**
 * Derives a deterministic OpenTelemetry span ID for the synthetic parent span of
 * a causal chain identified by the given root signal ID.
 *
 * The emitted handler spans use this value as their parent, so that they form a
 * coherent tree rooted at the chain's origin. The result is the lower 64 bits of
 * the backing UUID, as a 16-character (8-byte) lowercase hex string.
 *
 * @see traceIdOf
 */
internal fun spanIdOf(rootSignalId: SignalId): String =
    rootSignalId.toUuid().toLongs { _, leastSignificantBits ->
        leastSignificantBits.toHexString()
    }

/**
 * Converts this signal ID to a [Uuid].
 *
 * Signal IDs are normally canonical UUID strings. For an ID that is not a UUID,
 * a deterministic [Uuid] is derived from its bytes, so that the mapping remains
 * stable for any input.
 */
private fun SignalId.toUuid(): Uuid {
    val value = value()
    return if (uuidPattern.matches(value)) {
        Uuid.parse(value)
    } else {
        Uuid.fromByteArray(value.encodeToByteArray().foldToUuidSize())
    }
}

/**
 * Folds this byte array into exactly [Uuid.SIZE_BYTES] bytes, so that it can be
 * turned into a [Uuid]. The fold is deterministic but not cryptographic.
 *
 * Both 64-bit halves of the result are kept non-zero, so that the derived trace ID
 * and span ID are valid (non-zero) OpenTelemetry identifiers — even for inputs that
 * would otherwise fold to all zeros (such as an empty or a uniformly repeated string).
 */
private fun ByteArray.foldToUuidSize(): ByteArray {
    val result = ByteArray(Uuid.SIZE_BYTES)
    forEachIndexed { index, byte ->
        val i = index % Uuid.SIZE_BYTES
        result[i] = (result[i].toInt() xor byte.toInt()).toByte()
    }
    val half = Uuid.SIZE_BYTES / 2
    if ((0 until half).all { result[it].toInt() == 0 }) {
        result[0] = 1
    }
    if ((half until Uuid.SIZE_BYTES).all { result[it].toInt() == 0 }) {
        result[half] = 1
    }
    return result
}
