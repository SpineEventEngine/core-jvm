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

    @SuppressWarnings("WeakerAccess")   // A part of the public API.
    public final ShardIndex determineIndex(Object entityId, TypeUrl entityStateType) {
        if (entityStateType.equals(ShardMaintenanceProcess.TYPE)) {
            return (ShardIndex) entityId;
        }
        return indexFor(entityId, entityStateType);
    }

    /**
     * Creates a new {@code ShardIndex} according to the passed shard index
     *
     * <p>The passed shard index value must be less than the total number of shards specified.
     *
     * <p>Both passed values must not be negative.
     *
     * @param indexValue
     *         the value of the shard index, zero-based
     * @param ofTotal
     *         the total number of shard
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
