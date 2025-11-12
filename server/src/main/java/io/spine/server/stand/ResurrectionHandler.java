/*
 * Copyright 2023, TeamDev. All rights reserved.
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
    ResurrectionHandler(Subscription subscription, TypeUrl eventType) {
        super(subscription, eventType);
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
