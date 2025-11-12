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

import io.spine.base.EventMessage
import io.spine.core.EventContext

/**
 * An event routing strategy that first attempts to route by producer ID and falls back
 * to routing by the first field if no producer ID is found.
 *
 * This routing combines two strategies:
 *  1. [ByProducerId] — attempts to route using the producer ID from event context.
 *  2. [ByFirstEventField] — falls back to using the first field of the event message.
 *
 * @param I The type of entity identifiers this route produces.
 * @param idClass The class object representing the type of entity identifiers.
 */
internal class ByProducerIdOrFirstField<I : Any>(idClass: Class<I>) : EventRoute<I, EventMessage> {

    private val byProducerId = ByProducerId<I>()
    private val byFirstField = ByFirstEventField(idClass, EventMessage::class.java)

    /**
     * Routes the event message by first attempting to use the producer ID,
     * falling back to the first matching field if no producer ID is found.
     *
     * @param message The event message to route.
     * @param context The context of the event message.
     * @return A set of entity identifiers determined by the routing strategy.
     */
    override fun invoke(message: EventMessage, context: EventContext): Set<I> {
        var ids = byProducerId(message, context)
        if (ids.isEmpty()) {
            ids = byFirstField(message, context)
        }
        return ids
    }
}
