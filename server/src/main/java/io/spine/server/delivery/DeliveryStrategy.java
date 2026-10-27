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

import io.spine.annotation.Internal;
import io.spine.type.TypeUrl;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Determines the {@linkplain ShardIndex index of a shard} for the given identifier of an entity.
 *
 * <p>The idea is to avoid the concurrent modification of the same {@code Entity} instance
 * on several app nodes. Therefore an entity is put into a shard, which in turn is designed to
 * process all the shard-incoming messages on a single application node at a time.
 */
public abstract class DeliveryStrategy {

    /**
     * Determines the shard index for the messages heading to the entity with the specified target
     * identifier.
     *
     * @param entityId
     *         the identifier of the entity, to which the messages are dispatched
     * @param entityStateType
     *         the type URL of the entity, to which the messages are dispatched
     * @return the shard index
     */
    protected abstract ShardIndex indexFor(Object entityId, TypeUrl entityStateType);

    /**
     * Tells how many shards there are according to this strategy.
     *
     * @return total count of shards
     */
    protected abstract int shardCount();

    /**
     * Determines the shard index for the given target of a message.
     *
     * <p>The messages of the shard maintenance name the shard to process in the ID of their
     * target. For them, the index is taken from that ID, and the strategy is not asked.
     *
     * @param entityId
     *         the identifier of the entity, to which the message is dispatched
     * @param entityStateType
     *         the type URL of the entity, to which the message is dispatched
     * @return the shard index
     */
    @SuppressWarnings("WeakerAccess")   // A part of the public API.
    public final ShardIndex determineIndex(Object entityId, TypeUrl entityStateType) {
        if (entityStateType.equals(ShardMaintenanceProcess.TYPE)) {
            return ((ShardMaintenanceId) entityId).getShard();
        }
        return indexFor(entityId, entityStateType);
    }

    /**
     * Creates a new {@code ShardIndex} according to the passed shard index.
     *
     * <p>The passed shard index value must be less than the total number of shards specified.
     *
     * <p>Both passed values must not be negative.
     *
     * @param indexValue
     *         the value of the shard index, zero-based
     * @param ofTotal
     *         the total number of shards
     * @return a new instance of the {@code ShardIndex}
     */
    @Internal
    public static ShardIndex newIndex(int indexValue, int ofTotal) {
        checkArgument(indexValue < ofTotal,
                      "The index of the shard `%s` must be less" +
                              " than the total number of shards `%s`.",
                      indexValue, ofTotal);
        var result = ShardIndex.newBuilder()
                .setIndex(indexValue)
                .setOfTotal(ofTotal)
                .build();
        return result;
    }
}
