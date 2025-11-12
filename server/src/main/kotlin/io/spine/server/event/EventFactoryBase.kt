/*
 * Copyright 2022, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.server.event

import com.google.protobuf.Any
import io.spine.base.EventMessage
import io.spine.core.Event
import io.spine.core.EventContext
import io.spine.core.Events
import io.spine.core.Version
import io.spine.protobuf.AnyPacker.pack
import io.spine.validate.checkValid

/**
 * Base class for event factories.
 */
internal open class EventFactoryBase protected constructor(
    val origin: EventOrigin,
    val producerId: Any
) {
    /**
     * Creates a new event context with an optionally passed version of the entity
     * which produced the event.
     */
    protected fun createContext(version: Version?): EventContext =
        newContext(version).build()

    /**
     * Creates a builder for a new context of the event with optionally set
     * version of an entity which is generating the event.
     */
    protected fun newContext(version: Version?): EventContext.Builder {
        val builder =
            origin.contextBuilder()
                  .setProducerId(producerId)
        if (version != null) {
            builder.version = version
        }
        return builder
    }

    /**
     * Creates a new `Event` instance.
     */
    protected fun assemble(message: EventMessage, context: EventContext): Event {
        message.checkValid()
        val eventId = Events.generateId()
        val packed = pack(message)
        return with(Event.newBuilder()) {
            id = eventId
            this.message = packed
            this.context = context
            build()
        }
    }
}
