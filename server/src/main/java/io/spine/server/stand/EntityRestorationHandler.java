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
import io.spine.client.Subscription;
import io.spine.protobuf.AnyPacker;
import io.spine.server.type.EventEnvelope;
import io.spine.system.server.event.EntityRestored;
import io.spine.type.TypeUrl;

/**
 * Handles the {@link EntityRestored} events in respect to the {@code Subscription}.
 */
final class EntityRestorationHandler extends ResurrectionHandler {

    private static final TypeUrl ENTITY_RESTORED = TypeUrl.of(EntityRestored.class);

    /**
     * Creates a new instance for the passed {@code Subscription}.
     */
    EntityRestorationHandler(Subscription subscription) {
        super(subscription, ENTITY_RESTORED);
    }

    @Override
    EntityState<?> stateFrom(EventEnvelope event) {
        var packed = packedStateFrom(event);
        var result = (EntityState<?>) AnyPacker.unpack(packed);
        return result;
    }

    @Override
    Any packedStateFrom(EventEnvelope event) {
        var message = (EntityRestored) event.message();
        var result = message.getState();
        return result;
    }
}
