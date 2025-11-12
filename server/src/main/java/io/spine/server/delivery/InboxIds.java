/*
 * Copyright 2022, TeamDev. All rights reserved.
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

package io.spine.server.delivery;

import io.spine.base.Identifier;
import io.spine.client.EntityId;
import io.spine.string.Stringifiers;
import io.spine.type.TypeUrl;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.base.Identifier.pack;

/**
 * Utilities for working with {@linkplain io.spine.server.delivery.InboxId inbox identifiers}.
 */
final class InboxIds {

    /** Prevents instantiation of this utility class. */
    private InboxIds() {
    }

    /**
     * Obtains the inbox ID from the given entity ID.
     *
     * @param id
     *         the ID of the entity
     * @param entityType
     *         the type of the entity
     * @param <T>
     *         the type of the ID class
     * @return the {@link InboxId}
     */
    static <T> InboxId wrap(T id, TypeUrl entityType) {
        checkNotNull(id);
        checkNotNull(entityType);

        var entityId = EntityId.newBuilder()
                .setId(pack(id))
                .build();
        var inboxId = InboxId.newBuilder()
                .setEntityId(entityId)
                .setTypeUrl(entityType.value())
                .build();
        return inboxId;
    }

    /**
     * Obtains the entity ID from the given {@link InboxId}.
     *
     * @param inboxId
     *         the ID of the inbox
     * @return the extracted entity ID
     */
    static Object unwrap(InboxId inboxId) {
        checkNotNull(inboxId);
        var idValue = inboxId.getEntityId().getId();
        var unpackedId = Identifier.unpack(idValue);
        return unpackedId;
    }

    /**
     * Creates a new {@code InboxSignalId}.
     *
     * @param targetId
     *         the ID of the target to which the signal is dispatched
     * @param uuid
     *         the UUID of the signal
     * @return the new instance of {@code InboxSignalId}
     */
    static InboxSignalId newSignalId(Object targetId, String uuid) {
        checkNotNull(targetId);
        checkNotNull(uuid);

        var rawValue = uuid + '@' + Stringifiers.toString(targetId);
        var result = InboxSignalId.newBuilder()
                .setValue(rawValue)
                .build();
        return result;
    }
}
