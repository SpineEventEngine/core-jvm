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
import com.google.protobuf.Message
import io.spine.base.EntityState
import io.spine.base.Identifier
import io.spine.core.EventContext
import io.spine.protobuf.defaultInstance
import io.spine.util.Exceptions.newIllegalStateException
import java.util.concurrent.ConcurrentHashMap

/**
 * Obtains the route as a value of the first field matching the type of
 * the identifiers of the entities to which a state update is delivered.
 *
 * Descriptors of discovered fields are cached.
 *
 * If a passed message does not have a field of the required ID type,
 * `IllegalStateException` will be thrown.
 *
 * @param I The type of the entities to which deliver the updates.
 */
internal class DefaultStateRoute<I : Any>
private constructor(private val idClass: Class<I>) : StateUpdateRoute<I, EntityState<*>> {

    /**
     * Descriptors of fields matching the ID class by message type.
     */
    private val fields: MutableMap<Class<out Message>, FieldDescriptor> = ConcurrentHashMap()

    fun supports(stateType: Class<out EntityState<*>>): Boolean {
        val type = stateType.defaultInstance.descriptorForType
        val idField = Identifier.findField(idClass, type)
        return idField.isPresent
    }

    /**
     * Obtains the ID from the first field of the passed message that matches the type
     * of identifiers used by this route.
     *
     * If such a field is discovered, its descriptor is remembered and associated
     * with the class of the state so that subsequent calls are faster.
     *
     * If a field matching the ID type is not found, the method
     * throws `IllegalStateException`.
     *
     * @param message The entity state message.
     * @param context The context of the update event, not used, in this default route.
     * @return a one-element set with the ID.
     * @throws IllegalStateException if the passed state instance does not have
     *   a field of the required ID type.
     */
    override fun invoke(message: EntityState<*>, context: EventContext): Set<I> {
        val messageClass: Class<out EntityState<*>> = message.javaClass
        val field = fields[messageClass]
        if (field != null) {
            return fieldToSet(field, message)
        }

        val fd = Identifier.findField(idClass, message.descriptorForType)
            .orElseThrow {
                newIllegalStateException(
                    "Unable to find a field matching the type `%s`" +
                            " in the message of the type `%s`.",
                    idClass.canonicalName, messageClass.canonicalName
                )
            }
        fields[messageClass] = fd
        val result = fieldToSet(fd, message)
        return result
    }

    private fun fieldToSet(field: FieldDescriptor, state: EntityState<*>): Set<I> {
        val fieldValue = state.getField(field)
        val id = idClass.cast(fieldValue)
        return EventRoute.withId(id)
    }

    companion object {

        /**
         * Creates a new instance.
         *
         * @param idClass The class of identifiers used for the routing.
         */
        fun <I : Any> newInstance(idClass: Class<I>): DefaultStateRoute<I> =
            DefaultStateRoute(idClass)
    }
}
