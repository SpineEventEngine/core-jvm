/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
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
    static SystemEventFactory forMessage(EventMessage message, Origin origin, boolean multiTenant) {
        var aggregateId = aggregateIdFrom(message);
        var producerId = Identifier.pack(aggregateId);
        EventOrigin eventOrigin;
        if (isNotDefault(origin)) {
            eventOrigin = EventOrigin.from(origin);
        } else {
            var factory = SystemRequestFactory.instance(multiTenant);
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
