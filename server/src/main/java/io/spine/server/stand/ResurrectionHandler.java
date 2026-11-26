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
import io.spine.client.EntityStateUpdate;
import io.spine.client.Subscription;
import io.spine.client.SubscriptionUpdate;
import io.spine.server.type.EventEnvelope;
import io.spine.type.TypeUrl;

import java.util.Optional;

/**
 * An abstract base for handlers dealing with {@code Entity} changes related to their
 * resurrection from being removed or archived.
 */
abstract class ResurrectionHandler extends AbstractEntityUpdateHandler {

    /**
     * Creates a new instance for the given {@code Subscription}
     * and the corresponding {@code TypeUrl} of system event.
     */
    ResurrectionHandler(Subscription subscription, int targetIndex, TypeUrl eventType) {
        super(subscription, targetIndex, eventType);
    }

    @Override
    final Optional<SubscriptionUpdate> detectUpdate(EventEnvelope event) {
        SubscriptionUpdate result = null;

        if (typeMatches(event)) {
            if (includeAll()) {
                result = stateUpdate(event);
            } else {
                if (idMatches(event) && stateMatches(stateFrom(event))) {
                    result = stateUpdate(event);
                }
            }
        }
        return Optional.ofNullable(result);
    }

    /**
     * Returns the unpacked {@code Entity} state after it was resurrected.
     */
    abstract EntityState<?> stateFrom(EventEnvelope event);

    /**
     * Returns the packed {@code Entity} state after it was resurrected.
     */
    abstract Any packedStateFrom(EventEnvelope event);

    private SubscriptionUpdate stateUpdate(EventEnvelope event) {
        var theEvent = asEntityLifecycleEvent(event);
        var packedId = packId(theEvent);
        var packedState = packedStateFrom(event);
        var stateUpdate = EntityStateUpdate.newBuilder()
                .setId(packedId)
                .setState(packedState)
                .build();
        return toSubscriptionUpdate(stateUpdate);
    }
}
