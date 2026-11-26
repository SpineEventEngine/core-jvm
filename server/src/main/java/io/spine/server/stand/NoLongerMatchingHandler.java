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

import io.spine.base.EntityState;
import io.spine.client.EntityStateUpdate;
import io.spine.client.Subscription;
import io.spine.client.SubscriptionUpdate;
import io.spine.server.type.EventEnvelope;
import io.spine.type.TypeUrl;

import java.util.Optional;

/**
 * Abstract base for handlers that detect changes in {@code Entity} state,
 * leading to concluding it as {@linkplain EntityStateUpdate#getNoLongerMatching()
 * no longer matching} the subscription criteria.
 */
abstract class NoLongerMatchingHandler extends AbstractEntityUpdateHandler {

    /**
     * Creates a new instance for the given {@code Subscription}
     * and the corresponding {@code TypeUrl} of system event.
     */
    NoLongerMatchingHandler(Subscription subscription, int targetIndex, TypeUrl eventType) {
        super(subscription, targetIndex, eventType);
    }

    @Override
    @SuppressWarnings("PMD.CollapsibleIfStatements" /* For better readability. */)
    final Optional<SubscriptionUpdate> detectUpdate(EventEnvelope event) {
        SubscriptionUpdate result = null;
        if (typeMatches(event)) {
            if (includeAll() ||
                    (idMatches(event) && stateMatches(lastKnownStateFrom(event)))) {
                result = noLongerMatching(event);
            }
        }
        return Optional.ofNullable(result);
    }

    /**
     * Extracts the last known state of the {@code Entity} from the passed event.
     */
    protected abstract EntityState<?> lastKnownStateFrom(EventEnvelope event);
}
