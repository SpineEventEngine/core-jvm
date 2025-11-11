/*
 * Copyright 2025, TeamDev. All rights reserved.
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

package io.spine.server.route

import com.google.protobuf.Descriptors.FieldDescriptor
import io.spine.base.Routable
import io.spine.core.SignalContext
import io.spine.util.Exceptions.newIllegalStateException

/**
 * Routes messages to a single target, which ID is the same as the first field of
 * the routed message.
 *
 * It is expected that the types of the first field and the identifier are the same.
 *
 * @param I The type of the identifiers.
 * @param M The common supertype for messages routable messages.
 * @param C The type of the message contexts.
 */
internal class ByFirstField<I : Any, M : Routable, C : SignalContext>(
    private val idClass: Class<I>
) : Unicast<I, M, C> {

    override fun invoke(message: M, context: C): I {
        val field = fieldIn(message)
        val result = getValue(field, message)
        return result
    }

    /**
     * Obtains a descriptor of the first field of the passed.
     *
     * @throws IllegalStateException if the passed message does not declare fields, or
     *   the field is a repeated field or a map.
     */
    @Suppress("ThrowsCount")
    private fun fieldIn(message: M): FieldDescriptor {
        val type = message.descriptorForType
        val fields = type.fields
        if (fields.isEmpty()) {
            error(
                "Cannot use the type `%s` for routing: it does not declare any field.",
                type.fullName
            )
        }
        val field = fields[0]
        if (field.isMapField) {
            error(
                "The field `%s` is a map and cannot be used for routing.",
                field.fullName
            )
        }
        if (field.isRepeated) {
            error(
                "The field `%s` is repeated and cannot be used for routing.",
                field.fullName
            )
        }
        return field
    }

    private fun error(messageFormat: String, firstArg: String): IllegalStateException {
        throw newIllegalStateException(
            "$messageFormat Please declare a field with the type `%s`.",
            firstArg,
            idClass.canonicalName
        )
    }

    /**
     * Obtains the value of first field making sure the value is of the expected type.
     */
    private fun getValue(field: FieldDescriptor, message: M): I {
        val value = message.getField(field)
        val valueClass: Class<*> = value.javaClass
        if (!idClass.isAssignableFrom(valueClass)) {
            throw newIllegalStateException(
                "The field `%s` has the type `%s` which is not assignable" +
                        " from the expected ID type `%s`.",
                field.fullName,
                valueClass.name,
                idClass.name
            )
        }
        val result = idClass.cast(value)
        return result
    }
}
