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

@file:JvmName("GivenUpdates")

package io.spine.client

import com.google.protobuf.Message
import io.spine.protobuf.pack
import io.spine.validation.NonValidated

/**
 * Creates an update of the first target of a subscription, which carries the given
 * entity updates.
 */
internal fun entityUpdatesOf(vararg updates: EntityStateUpdate): @NonValidated SubscriptionUpdate =
    SubscriptionUpdate.newBuilder()
        .setEntityUpdates(entityUpdates(updates.asList()))
        .buildPartial()

/**
 * Creates an entity update telling that the entity with the given ID no longer matches
 * its target.
 */
internal fun noLongerMatching(entity: Message): EntityStateUpdate =
    entityStateUpdate {
        id = packedEntityId(entity)
        noLongerMatching = true
    }

/**
 * Creates an entity update with the new state of the entity with the given ID.
 */
internal fun stateChanged(entity: Message, state: Message): EntityStateUpdate =
    entityStateUpdate {
        id = packedEntityId(entity)
        this.state = state.pack()
    }

private fun entityUpdates(updates: List<EntityStateUpdate>): @NonValidated EntityUpdates =
    EntityUpdates.newBuilder()
        .addAllUpdate(updates)
        .buildPartial()

private fun packedEntityId(entity: Message) = entityId { id = entity.pack() }.pack()
