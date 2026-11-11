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

import io.spine.base.Identifier;
import io.spine.server.NodeId;
import io.spine.server.storage.memory.InMemoryStorageFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;

@DisplayName("`Delivery` factory methods should")
class DeliveryFactoryTest {

    @Test
    @DisplayName("create an instance of `Delivery` " +
            "which delivers the messages from their shards asynchronously")
    void createLocalAsync(){
        var nodeId = NodeId.newBuilder()
                .setValue(Identifier.newUuid())
                .build();
        var delivery = Delivery.localAsync(nodeId, InMemoryStorageFactory.newInstance());

        var observers = delivery.shardObservers();
        assertThat(observers).hasSize(1);

        var observer = observers.get(0);
        assertThat(observer).isInstanceOf(LocalDispatchingObserver.class);

        var localObserver = (LocalDispatchingObserver) observer;
        assertThat(localObserver.isAsync()).isTrue();
    }
}
