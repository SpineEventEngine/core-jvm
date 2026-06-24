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

import io.spine.protobuf.AnyPacker.unpack
import io.spine.string.Stringifiers
import io.spine.type.toCompactJson

/**
 * The Spine-specific attributes attached to each emitted span.
 *
 * Keys follow the OpenTelemetry attribute naming convention: lowercase, dotted,
 * and namespaced under `spine.`.
 */
internal enum class SpanAttribute(val key: String) {

    /**
     * The fully-qualified name of the bounded context that handled the signal.
     */
    BOUNDED_CONTEXT("spine.bounded_context") {
        override fun valueIn(span: SignalSpan): String =
            span.contextName.value
    },

    /**
     * The tenant on behalf of which the signal is handled, as compact JSON.
     */
    TENANT("spine.tenant") {
        override fun valueIn(span: SignalSpan): String =
            span.signal.tenant().toCompactJson()
    },

    /**
     * The string form of the ID of the entity that handled the signal.
     */
    ENTITY_ID("spine.entity.id") {
        override fun valueIn(span: SignalSpan): String {
            val id = unpack(span.receiver.id)
            return Stringifiers.toString(id)
        }
    },

    /**
     * The ID of the handled signal.
     */
    SIGNAL_ID("spine.signal.id") {
        override fun valueIn(span: SignalSpan): String =
            span.signal.id().value()
    },

    /**
     * The type URL of the entity that handled the signal.
     */
    ENTITY_TYPE("spine.entity.type") {
        override fun valueIn(span: SignalSpan): String =
            span.receiver.typeUrl
    },

    /**
     * The type URL of the handled signal.
     */
    SIGNAL_TYPE("spine.signal.type") {
        override fun valueIn(span: SignalSpan): String =
            span.signal.enclosedTypeUrl().value()
    };

    /**
     * Computes the value of this attribute for the given signal span.
     */
    abstract fun valueIn(span: SignalSpan): String

    /**
     * Computes the value of this attribute, truncated to the maximum length.
     */
    fun truncatedValueIn(span: SignalSpan): String =
        valueIn(span).truncated(ATTRIBUTE_VALUE_MAX_LENGTH)

    private companion object {

        /**
         * The maximum length of a string attribute value, in characters.
         *
         * Longer values are truncated to keep span payloads bounded.
         */
        const val ATTRIBUTE_VALUE_MAX_LENGTH = 256
    }
}

/**
 * Returns this string limited to at most [maxLength] UTF-16 code units.
 *
 * If the cut falls between the two halves of a surrogate pair, the trailing
 * high surrogate is dropped as well, so the result never ends with a broken character.
 */
internal fun String.truncated(maxLength: Int): String {
    if (length <= maxLength) {
        return this
    }
    val end = if (Character.isHighSurrogate(this[maxLength - 1])) maxLength - 1 else maxLength
    return substring(0, end)
}
