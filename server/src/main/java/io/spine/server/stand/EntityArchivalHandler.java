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
import io.spine.system.server.event.EntityArchived;
import io.spine.type.TypeUrl;

/**
 * Handles the {@link EntityArchived} events in respect to the {@code Subscription}.
 */
final class EntityArchivalHandler extends NoLongerMatchingHandler {

    private static final TypeUrl ENTITY_ARCHIVED = TypeUrl.of(EntityArchived.class);

    /**
     * Creates a new instance for the given target of the passed {@code Subscription}.
     */
    EntityArchivalHandler(Subscription subscription, int targetIndex) {
        super(subscription, targetIndex, ENTITY_ARCHIVED);
    }

    @Override
    protected EntityState<?> lastKnownStateFrom(EventEnvelope event) {
        var message = (EntityArchived) event.message();
        var newState = message.getLastState();
        var result = (EntityState<?>) AnyPacker.unpack(newState);
        return result;
    }
}
