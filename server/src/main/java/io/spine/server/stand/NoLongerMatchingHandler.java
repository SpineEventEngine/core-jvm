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

import io.spine.base.EntityState;
import io.spine.client.EntityStateUpdate;
import io.spine.client.Subscription;
import io.spine.client.SubscriptionUpdate;
import io.spine.server.type.EventEnvelope;
import io.spine.type.TypeUrl;

import java.util.Optional;

/**
 * Abstract base for handlers which detect changes in {@code Entity} state,
 * leading to concluding it as {@linkplain EntityStateUpdate#getNoLongerMatching()
 * no longer matching} the subscription criteria.
 */
abstract class NoLongerMatchingHandler extends AbstractEntityUpdateHandler {

    /**
     * Creates a new instance for the given {@code Subscription}
     * and the corresponding {@code TypeUrl} of system event.
     */
    NoLongerMatchingHandler(Subscription subscription, TypeUrl eventType) {
        super(subscription, eventType);
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
