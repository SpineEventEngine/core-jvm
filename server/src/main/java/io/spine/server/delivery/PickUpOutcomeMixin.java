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
     * Returns an {@code Optional} containing the {@code ShardSessionRecord} if this outcome
     * indicates that the shard was successfully picked up, or an empty {@code Optional} otherwise.
     */
    default Optional<ShardSessionRecord> session() {
        var session = getSession();
        if (ShardSessionRecord.getDefaultInstance().equals(session)) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    /**
     * Returns {@code ShardAlreadyPickedUp} if this outcome indicates that the shard could not be
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
