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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.spine.core.TenantId;

import static io.spine.server.tenant.TenantAwareRunner.with;

/**
 * Utility providing access to the raw contents of the {@link Inbox}es of a {@link Delivery}.
 */
public final class InboxContents {

    private InboxContents() {
    }

    /**
     * Fetches the contents of the {@code Inbox}es of the given delivery for each of
     * the {@code ShardIndex}es.
     */
    public static ImmutableMap<ShardIndex, ImmutableList<InboxMessage>> of(Delivery delivery) {
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
