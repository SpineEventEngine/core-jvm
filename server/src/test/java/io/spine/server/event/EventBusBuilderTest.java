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

package io.spine.server.event;

import io.grpc.stub.StreamObserver;
import io.spine.core.Ack;
import io.spine.core.Event;
import io.spine.server.bus.BusBuilderTest;
import io.spine.server.type.EventEnvelope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.grpc.StreamObservers.noOpObserver;

@DisplayName("`EventBus.Builder` should")
class EventBusBuilderTest
        extends BusBuilderTest<EventBus.Builder, EventEnvelope, Event> {

    @Override
    protected EventBus.Builder builder() {
        return EventBus.newBuilder();
    }

    @Nested
    @DisplayName("assign `StreamObserver`")
    class PostObserver {

        @Test
        @DisplayName("assigning `noOpObserver()` if not assigned")
        void assigningDefault() {
            var bus = builder().build();
            var observer = bus.observer();
            assertThat(observer).isInstanceOf(noOpObserver().getClass());
        }

        @Test
        @DisplayName("assign custom observer")
        void customValue() {
            var observer = new StreamObserver<Ack>() {
                @Override
                public void onNext(Ack value) {
                }

                @Override
                public void onError(Throwable t) {
                }

                @Override
                public void onCompleted() {
                }
            };
            var bus = builder().setObserver(observer).build();
            assertThat(bus.observer()).isEqualTo(observer);
        }
    }
}
