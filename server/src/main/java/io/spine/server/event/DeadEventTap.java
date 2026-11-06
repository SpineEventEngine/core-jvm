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

package io.spine.server.event;

import io.spine.core.TenantId;
import io.spine.server.bus.DeadMessageHandler;
import io.spine.server.type.EventEnvelope;
import io.spine.system.server.SystemWriteSide;
import io.spine.system.server.UnsupportedEventReceived;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Handles a dead event by saving it to the {@link EventStore}.
 *
 * <p>We must store dead events as they can still be emitted by some entities and therefore are
 * a part of the history for the current Bounded Context.
 *
 * <p>A dead event that comes from another Bounded Context is also recorded
 * as {@link UnsupportedEventReceived} in the System context. A domestic event without
 * subscribers is not a problem, and is not recorded.
 */
final class DeadEventTap implements DeadMessageHandler<EventEnvelope> {

    private final Supplier<EventStore> store;
    private final Function<TenantId, SystemWriteSide> system;

    DeadEventTap(Supplier<EventStore> store, Function<TenantId, SystemWriteSide> system) {
        this.store = store;
        this.system = system;
    }

    @Override
    public void handle(EventEnvelope event) {
        eventStore().append(event.outerObject());
        if (event.isExternal()) {
            var unsupported = UnsupportedEventReceived.newBuilder()
                    .setId(event.id())
                    .setTypeUrl(event.typeUrl().value())
                    .build();
            system.apply(event.tenantId())
                  .postEvent(unsupported, event.asMessageOrigin());
        }
    }

    private EventStore eventStore() {
        return store.get();
    }
}
