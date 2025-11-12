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

package io.spine.server.delivery;

import com.google.protobuf.Timestamp;
import io.spine.annotation.GeneratedMixin;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A mixin of the {@link PickUpOutcome} providing a convenience API over the Proto message.
 */
@GeneratedMixin
public interface PickUpOutcomeMixin extends PickUpOutcomeOrBuilder {

    /**
     * Creates a new {@code PickUpOutcome} of successfully picked up shard
     * with the given {@code ShardSessionRecord}.
     */
    @SuppressWarnings("ClassReferencesSubclass" /* This is a mixin of the type. */)
    static PickUpOutcome pickedUp(ShardSessionRecord session) {
        checkNotNull(session);
        return PickUpOutcome.newBuilder()
                .setSession(session)
                .build();
    }

    /**
     * Creates a new {@code PickUpOutcome} indicating that the shard is already picked up
     * by the given worker in {@code pickedUp} message.
     */
    @SuppressWarnings("ClassReferencesSubclass" /* This is a mixin of the type. */)
    static PickUpOutcome alreadyPicked(WorkerId worker, Timestamp whenPicked) {
        checkNotNull(worker);
        checkNotNull(whenPicked);
        var pickedUp = ShardAlreadyPickedUp.newBuilder()
                .setWorker(worker)
                .setWhenPicked(whenPicked)
                .build();
        return PickUpOutcome.newBuilder()
                .setAlreadyPicked(pickedUp)
                .build();
    }

    /**
     * Returns {@code ShardProcessingSession} if this outcome indicates that shard is successfully
     * picked up, or empty {@code Optional} otherwise.
     */
    default Optional<ShardSessionRecord> session() {
        var session = getSession();
        if (ShardSessionRecord.getDefaultInstance().equals(session)) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    /**
     * Returns {@code ShardAlreadyPickedUp} if this outcome indicates that shard could not be
     * picked up as it's already picked up by another worker, or empty {@code Optional} otherwise.
     */
    default Optional<ShardAlreadyPickedUp> alreadyPicked() {
        var pickedUp = getAlreadyPicked();
        if (ShardAlreadyPickedUp.getDefaultInstance().equals(pickedUp)) {
            return Optional.empty();
        }
        return Optional.of(pickedUp);
    }
}
