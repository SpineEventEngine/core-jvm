/*
 * Copyright 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.server.delivery.given;

import io.spine.server.delivery.DeliveryStrategy;
import io.spine.server.delivery.ShardIndex;
import io.spine.type.TypeUrl;

import java.io.Serializable;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A delivery strategy which always deliver all the items to the first shard.
 */
public class FixedShardStrategy extends DeliveryStrategy implements Serializable {

    private static final long serialVersionUID = 0;

    private final int shardCount;
    private final ShardIndex nonEmptyShard;

    /**
     * Creates a strategy with the specified number of shards.
     *
     * @param shardCount
     *         the number of shards
     */
    public FixedShardStrategy(int shardCount) {
        checkArgument(shardCount > 0);
        this.shardCount = shardCount;
        nonEmptyShard = ShardIndex
                .newBuilder()
                .setIndex(0)
                .setOfTotal(this.shardCount)
                .build();
    }

    @Override
    public ShardIndex indexFor(Object entityId, TypeUrl entityStateType) {
        return nonEmptyShard;
    }

    public ShardIndex nonEmptyShard() {
        return nonEmptyShard;
    }

    @Override
    public int shardCount() {
        return shardCount;
    }
}
