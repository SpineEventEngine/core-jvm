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

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Message;
import io.spine.type.TypeUrl;

import java.io.Serializable;
import java.nio.charset.Charset;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.abs;

/**
 * The strategy of splitting the entities into a number of shards uniformly.
 *
 * <p>Uses a hash code of the entity identifier and the remainder of its division by the total
 * number of shards to determine the index of a shard, at which the modification is allowed.
 * To reach the consistency of the hash code values across JVMs,
 * the {@linkplain Hashing#murmur3_32_fixed() MurmurHash3, x86 variant} is used.
 */
@Immutable
public final class UniformAcrossAllShards extends DeliveryStrategy implements Serializable {

    private static final long serialVersionUID = 0L;

    /**
     * The hash function to use for the shard index calculation.
     */
    private static final HashFunction HASHER = Hashing.murmur3_32_fixed();

    private final int numberOfShards;

    /**
     * Creates an instance of this strategy.
     *
     * @param numberOfShards
     *         a number of shards; must be greater than zero
     */
    private UniformAcrossAllShards(int numberOfShards) {
        super();
        checkArgument(numberOfShards > 0, "Number of shards must be positive");
        this.numberOfShards = numberOfShards;
    }

    /**
     * Creates a strategy of uniform target distribution across shards, for a given shard number.
     *
     * @param totalShards
     *         a number of shards
     * @return a uniform distribution strategy instance for a given shard number
     */
    public static DeliveryStrategy forNumber(int totalShards) {
        var result = new UniformAcrossAllShards(totalShards);
        return result;
    }

    @Override
    public ShardIndex indexFor(Object entityId, TypeUrl entityStateType) {
        if (1 == numberOfShards) {
            return newIndex(0);
        }
        var hashValue = hash(entityId);
        var totalShards = shardCount();
        var indexValue = abs(hashValue % totalShards);
        var result = newIndex(indexValue);
        return result;
    }

    private static int hash(Object entityId) {
        byte[] bytes;
        if (entityId instanceof Message) {
            bytes = ((Message) entityId).toByteArray();
        } else {
            bytes = entityId.toString()
                            .getBytes(Charset.defaultCharset());
        }
        var value = HASHER.hashBytes(bytes)
                          .asInt();
        return value;
    }

    @Override
    public int shardCount() {
        return numberOfShards;
    }

    private ShardIndex newIndex(int indexValue) {
        return newIndex(indexValue, shardCount());
    }

    private enum SingleShard {
        INSTANCE;
        private final UniformAcrossAllShards strategy = new UniformAcrossAllShards(1);
    }

    /**
     * Returns a pre-defined strategy instance, which defines a single shard and puts all
     * the targets into it.
     *
     * @return a strategy that puts all entities in a single shard
     */
    public static DeliveryStrategy singleShard() {
        return SingleShard.INSTANCE.strategy;
    }
}
