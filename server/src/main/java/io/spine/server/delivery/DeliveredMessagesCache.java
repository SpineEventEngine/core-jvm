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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.protobuf.Timestamp;
import io.spine.base.Time;

import java.util.Set;

/**
 * A cache of the messages locally delivered within the instance of {@link Delivery}.
 *
 * <p>The cache is limited in size, aiming to hunt down only the duplicates of the recently
 * delivered messages. The idea behind it is that the messages were read locally anyway,
 * so as well their identifiers may be reused for deduplication instead of just wasting
 * the effort and feeding the garbage collector.
 */
final class DeliveredMessagesCache {

    private final Cache<DispatchingId, Timestamp> cache =
            CacheBuilder.newBuilder()
                        .maximumSize(1_000)
                        .build();

    /**
     * Returns the set of the identifiers of the delivered messages.
     */
    Set<DispatchingId> allDelivered() {
        return cache.asMap()
                    .keySet();
    }

    /**
     * Records the delivery of the message.
     */
    void recordDelivered(InboxMessage message) {
        var id = new DispatchingId(message);
        cache.put(id, Time.currentTime());
    }
}
