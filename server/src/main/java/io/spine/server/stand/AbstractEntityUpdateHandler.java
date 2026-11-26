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
import io.spine.base.Identifier;
import io.spine.client.EntityId;
import io.spine.client.EntityStateUpdate;
import io.spine.client.EntityUpdates;
import io.spine.client.Subscription;
import io.spine.client.SubscriptionUpdate;
import io.spine.server.type.EventEnvelope;
import io.spine.system.server.event.EntityLifecycleEvent;
import io.spine.type.TypeUrl;

/**
 * Abstract base for handlers of {@code Subscriptions} for {@code Entity}-related updates.
 */
abstract class AbstractEntityUpdateHandler extends UpdateHandler {

    AbstractEntityUpdateHandler(Subscription subscription, int targetIndex, TypeUrl eventType) {
        super(subscription, targetIndex, eventType);
    }

    @Override
    protected final boolean typeMatches(EventEnvelope event) {
        var expectedTypeUrl = target().getType();
        var actualTypeUrl = asEntityLifecycleEvent(event).getEntity()
                                                         .getTypeUrl();
        return expectedTypeUrl.equals(actualTypeUrl);
    }

    @Override
    protected final Any extractId(EventEnvelope event) {
        var entityId = asEntityLifecycleEvent(event).getEntity()
                                                    .getId();
        return entityId;
    }

    /**
     * Checks if the entity state matches the subscription filters.
     */
    boolean stateMatches(EntityState<?> state) {
        var filters = target().getFilters();
        var result = filters
                .getFilterList()
                .stream()
                .allMatch(f -> f.test(state));
        return result;
    }

    /**
     * Wraps the {@code Entity} identifier from the passed event
     * into {@link EntityId} and packs it to {@code Any}.
     *
     * @param event
     *         the event of {@code Entity} lifecycle
     * @return packed identifier of the {@code Entity}
     */
    static Any packId(EntityLifecycleEvent event) {
        var entityId = EntityId.newBuilder()
                .setId(event.getEntity()
                            .getId())
                .build();
        return Identifier.pack(entityId);
    }

    protected static EntityLifecycleEvent asEntityLifecycleEvent(EventEnvelope event) {
        return (EntityLifecycleEvent) event.message();
    }

    /**
     * Creates a subscription update telling that the {@code Entity} no longer matches
     * the subscription criteria.
     *
     * @param event
     *         original event telling about the lifecycle of the {@code Entity}
     * @return new instance of {@code SubscriptionUpdate}
     */
    protected SubscriptionUpdate noLongerMatching(EventEnvelope event) {
        var theEvent = asEntityLifecycleEvent(event);
        var packedId = packId(theEvent);
        var stateUpdate = EntityStateUpdate.newBuilder()
                .setId(packedId)
                .setNoLongerMatching(true)
                .build();
        return toSubscriptionUpdate(stateUpdate);
    }

    /**
     * Creates a subscription update basing on the passed {@code Entity} state update.
     *
     * @param stateUpdate
     *         the update to post as a {@code SubscriptionUpdate}
     * @return new instance of {@code SubscriptionUpdate}
     */
    protected SubscriptionUpdate toSubscriptionUpdate(EntityStateUpdate stateUpdate) {
        var updates = EntityUpdates.newBuilder()
                .addUpdate(stateUpdate)
                .build();
        var result = newUpdate()
                .setEntityUpdates(updates)
                .build();
        return result;
    }
}
