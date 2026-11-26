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

package io.spine.server.stand;

import com.google.protobuf.Any;
import io.spine.base.EntityState;
import io.spine.client.Subscription;
import io.spine.protobuf.AnyPacker;
import io.spine.server.type.EventEnvelope;
import io.spine.system.server.event.EntityUnarchived;
import io.spine.type.TypeUrl;

/**
 * Handles the {@link EntityUnarchived} events in respect to the {@code Subscription}.
 */
final class EntityUnarchivalHandler extends ResurrectionHandler {

    private static final TypeUrl ENTITY_UNARCHIVED = TypeUrl.of(EntityUnarchived.class);

    /**
     * Creates a new instance for the given target of the passed {@code Subscription}.
     */
    EntityUnarchivalHandler(Subscription subscription, int targetIndex) {
        super(subscription, targetIndex, ENTITY_UNARCHIVED);
    }

    @Override
    EntityState<?> stateFrom(EventEnvelope event) {
        var packed = packedStateFrom(event);
        EntityState<?> result = (EntityState<?>) AnyPacker.unpack(packed);
        return result;
    }

    @Override
    Any packedStateFrom(EventEnvelope event) {
        var message = (EntityUnarchived) event.message();
        var result = message.getState();
        return result;
    }
}
