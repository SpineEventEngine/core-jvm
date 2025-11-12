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

package io.spine.system.server;

import com.google.protobuf.Any;
import com.google.protobuf.Message;
import io.spine.base.EventMessage;
import io.spine.base.Identifier;
import io.spine.core.EventContext;
import io.spine.core.Origin;
import io.spine.server.event.EventFactory;
import io.spine.server.event.EventOrigin;
import io.spine.server.route.EventRoute;

import static com.google.common.base.Preconditions.checkArgument;
import static io.spine.protobuf.Messages.isNotDefault;

/**
 * Creates events that will be imported into system aggregates.
 */
final class SystemEventFactory extends EventFactory {

    private SystemEventFactory(EventOrigin origin, Any producerId) {
        super(origin, producerId);
    }

    /**
     * Creates a new factory of system events.
     *
     * @param message
     *         the system event message
     * @param origin
     *         the origin of this message
     * @return new instance of {@code SystemEventFactory}
     */
    static SystemEventFactory forMessage(EventMessage message, Origin origin, boolean multitenant) {
        var aggregateId = aggregateIdFrom(message);
        var producerId = Identifier.pack(aggregateId);
        EventOrigin eventOrigin;
        if (isNotDefault(origin)) {
            eventOrigin = EventOrigin.from(origin);
        } else {
            var factory = SystemRequestFactory.instance(multitenant);
            var importContext = factory.newActorContext();
            eventOrigin = EventOrigin.forImport(importContext);
        }
        return new SystemEventFactory(eventOrigin, producerId);
    }

    private static Message aggregateIdFrom(EventMessage systemEvent) {
        var routingOut =
                EventRoute.byFirstMessageField(Object.class)
                          .invoke(systemEvent, EventContext.getDefaultInstance());
        checkArgument(routingOut.size() == 1,
                      "System event message must have entity ID in the first field.");
        var id = routingOut.iterator()
                           .next();
        checkArgument(id instanceof Message, "System entity ID must be a `Message`.");
        return (Message) id;
    }
}
