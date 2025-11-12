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

package io.spine.server.delivery;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.spine.core.TenantId;
import io.spine.server.ServerEnvironment;

import static io.spine.server.tenant.TenantAwareRunner.with;

/**
 * Utility providing access to the raw contents of the {@link Inbox}es in the current
 * {@linkplain io.spine.server.ServerEnvironment server environment}.
 */
public final class InboxContents {

    private InboxContents() {
    }

    /**
     * Fetches the contents of the {@code Inbox}es for each of the {@code ShardIndex}es.
     */
    public static ImmutableMap<ShardIndex, ImmutableList<InboxMessage>> get() {
        var delivery = ServerEnvironment.instance()
                                        .delivery();
        var storage = delivery.inboxStorage();
        var shardCount = delivery.shardCount();
        ImmutableMap.Builder<ShardIndex, ImmutableList<InboxMessage>> builder =
                ImmutableMap.builder();
        for (var shardIndex = 0; shardIndex < shardCount; shardIndex++) {
            var index = DeliveryStrategy.newIndex(shardIndex, shardCount);
            var page = with(TenantId.getDefaultInstance())
                    .evaluate(() -> storage.readAll(index, Integer.MAX_VALUE));
            builder.put(index, page.contents());
        }
        return builder.build();
    }
}
