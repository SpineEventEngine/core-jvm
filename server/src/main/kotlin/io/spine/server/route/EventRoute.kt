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

import com.google.common.collect.ImmutableSet
import io.spine.base.EventMessage
import io.spine.core.EventContext

/**
 * Obtains a set of entity IDs for which to deliver an event.
 *
 * @param I The type of entity IDs.
 * @param M The type of event message from which to get the IDs.
 */
public fun interface EventRoute<I : Any, M : EventMessage> : Multicast<I, M, EventContext> {

    public companion object {

        /**
         * Creates an event route that obtains the ID of the event producer from
         * an [EventContext] and returns it as a sole element of the returned route.
         *
         * @param I The type of the entity IDs to which the event would be routed.
         * @return new route.
         */
        @JvmStatic
        public fun <I : Any> byProducerId(): EventRoute<I, EventMessage> = ByProducerId()

        /**
         * Creates an event route that obtains target entity ID from an event message and
         * returns it as a sole element of the returned [EventRoute].
         *
         * @param I The type of the IDs of entities to which the event would be routed.
         * @param idClass The class of identifiers.
         * @return new route.
         */
        @JvmStatic
        public fun <I : Any, E : EventMessage> byFirstMessageField(
            idClass: Class<I>,
            eventClass: Class<E>
        ): EventRoute<I, E> = ByFirstEventField(idClass, eventClass)


        /**
         * Creates an event route that obtains target entity ID from an event message and
         * returns it as a sole element of the returned [EventRoute].
         *
         * @param I The type of the IDs of entities to which the event would be routed.
         * @param idClass The class of identifiers.
         * @return new route that routes events based on the first matching ID field.
         */
        @JvmStatic
        public fun <I : Any> byFirstMessageField(
            idClass: Class<I>
        ): EventRoute<I, EventMessage> = byFirstMessageField(idClass, EventMessage::class.java)

        @JvmStatic
        public fun <I : Any> byProducerIdOrFirstField(
            idClass: Class<I>
        ): EventRoute<I, EventMessage> =
            ByProducerIdOrFirstField(idClass)

        /**
         * Returns the empty immutable set.
         *
         * This is a convenience method for ignoring a type of messages when building
         * a routing schema in a repository.
         */
        @JvmStatic
        public fun <I : Any> noTargets(): Set<I> = ImmutableSet.of()

        /**
         * Creates an immutable singleton set with the given ID.
         *
         * This is a convenience method for customizing routing schemas when the target is
         * only one entity.
         */
        @JvmStatic
        public fun <I : Any> withId(id: I): Set<I> {
            return ImmutableSet.of(id)
        }
    }
}
