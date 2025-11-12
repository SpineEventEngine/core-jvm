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

package io.spine.server.event;

import com.google.protobuf.Any;
import io.spine.base.EventMessage;
import io.spine.core.ActorContext;
import io.spine.core.Event;
import io.spine.core.Version;
import io.spine.server.type.MessageEnvelope;
import io.spine.validate.ValidationException;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.server.event.EventOrigin.fromAnotherMessage;
import static io.spine.validate.Validate.check;

/**
 * Produces events.
 */
public class EventFactory extends EventFactoryBase {

    protected EventFactory(EventOrigin origin, Any producerId) {
        super(origin, producerId);
    }

    /**
     * Creates a new event factory for producing events in response to the passed message.
     *
     * @param origin
     *         the message in response to which events will be generated
     * @param producerId
     *         the ID of the entity producing the events
     * @return new event factory
     */
    public static EventFactory on(MessageEnvelope<?, ?, ?> origin, Any producerId) {
        checkNotNull(origin);
        checkNotNull(producerId);
        var eventOrigin = fromAnotherMessage(origin);
        return new EventFactory(eventOrigin, producerId);
    }

    /**
     * Creates a new event factory for producing events to be imported into a Bounded Context.
     *
     * @param actorContext
     *         the description of the actor who imports the events
     * @param producerId
     *         the ID of the system which produced the events
     * @return new event factory
     */
    public static EventFactory forImport(ActorContext actorContext, Any producerId) {
        checkNotNull(actorContext);
        checkNotNull(producerId);
        check(actorContext);

        var origin = EventOrigin.forImport(actorContext);
        return new EventFactory(origin, producerId);
    }

    /**
     * Creates an event for the passed event message.
     *
     * <p>The message passed is validated according to the constraints set in its Protobuf
     * definition. If the message is invalid, an {@linkplain ValidationException
     * exception} is thrown.
     *
     * @param msg
     *         the event message
     * @param version
     *         the version of the entity which produces the event, or
     *         {@code null} if the version information is not available
     * @throws ValidationException
     *         if the passed message does not satisfy the validation constraints
     * @see #createEvent(EventMessage)
     */
    public Event createEvent(EventMessage msg, Version version)
            throws ValidationException {
        var context = createContext(version);
        return assemble(msg, context);
    }

    /**
     * Creates an event without version information.
     *
     * @throws ValidationException
     *         if the passed message does not satisfy the validation constraints
     * @see #createEvent(EventMessage, Version)
     */
    public Event createEvent(EventMessage msg) throws ValidationException {
        var context = createContext(null);
        return assemble(msg, context);
    }
}
