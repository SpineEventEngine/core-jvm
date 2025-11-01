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

import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.memory.InMemoryShardedWorkRegistry;
import io.spine.server.storage.StorageFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static com.google.protobuf.util.Durations.fromMinutes;
import static io.spine.testing.Assertions.assertIllegalArgument;
import static io.spine.testing.Assertions.assertNpe;
import static io.spine.testing.TestValues.nullRef;

@SuppressWarnings("WriteOnlyObject") /* Some test methods call `builder()` only for writing. */
@DisplayName("`DeliveryBuilder` should")
class DeliveryBuilderTest {

    private static DeliveryBuilder builder() {
        return Delivery.newBuilder();
    }

    @Nested
    @DisplayName("not accept `null`")
    class NotAcceptNull {

        @Test
        @DisplayName("delivery strategy")
        void strategy() {
            assertNpe(() -> builder().setStrategy(nullRef()));
        }

        @Test
        @DisplayName("Inbox storage")
        void inboxStorage() {
            assertNpe(() -> builder().setInboxStorage(nullRef()));
        }

        @Test
        @DisplayName("Catch-up storage")
        void catchUpStorage() {
            assertNpe(() -> builder().setCatchUpStorage(nullRef()));
        }

        @Test
        @DisplayName("work registry")
        void workRegistry() {
            assertNpe(() -> builder().setWorkRegistry(nullRef()));
        }

        @Test
        @DisplayName("deduplication window")
        void deduplicationWindow() {
            assertNpe(() -> builder().setDeduplicationWindow(nullRef()));
        }

        @Test
        @DisplayName("delivery monitor")
        void deliveryMonitor() {
            assertNpe(() -> builder().setMonitor(nullRef()));
        }
    }

    @Test
    @DisplayName("accept only positive page size")
    void acceptOnlyPositivePageSize() {
        assertIllegalArgument(() -> builder().setPageSize(0));
        assertIllegalArgument(() -> builder().setPageSize(-3));
    }

    @Test
    @DisplayName("accept only positive catch-up page size")
    void acceptOnlyPositiveCatchUpPageSize() {
        assertIllegalArgument(() -> builder().setCatchUpPageSize(0));
        assertIllegalArgument(() -> builder().setCatchUpPageSize(-3));
    }

    @Nested
    @DisplayName("return set")
    class ReturnSet {

        private StorageFactory factory;
        @BeforeEach
        void createStorageFactory() {
            factory = ServerEnvironment.instance().storageFactory();            
        }
        
        @Test
        @DisplayName("delivery strategy")
        void strategy() {
            var strategy = UniformAcrossAllShards.forNumber(42);
            assertThat(builder().setStrategy(strategy).getStrategy())
                  .isEqualTo(strategy);
        }

        @Test
        @DisplayName("Inbox storage")
        void inboxStorage() {
            var storage = new InboxStorage(factory, false);
            assertThat(builder().setInboxStorage(storage).getInboxStorage())
                  .isEqualTo(storage);
        }

        @Test
        @DisplayName("Catch-up storage")
        void catchUpStorage() {
            var storage = new CatchUpStorage(factory, false);
            assertThat(builder().setCatchUpStorage(storage).getCatchUpStorage())
                  .isEqualTo(storage);
        }

        @Test
        @DisplayName("work registry")
        void workRegistry() {
            var registry = new InMemoryShardedWorkRegistry();
            assertThat(builder().setWorkRegistry(registry)
                                       .getWorkRegistry())
                  .isEqualTo(registry);
        }

        @Test
        @DisplayName("deduplication window")
        void deduplicationWindow() {
            var duration = fromMinutes(123);
            assertThat(builder().setDeduplicationWindow(duration).getDeduplicationWindow())
                    .isEqualTo(duration);
        }

        @Test
        @DisplayName("delivery monitor")
        void deliveryMonitor() {
            var monitor = DeliveryMonitor.alwaysContinue();
            assertThat(builder().setMonitor(monitor).getDeliveryMonitor())
                    .isEqualTo(monitor);
        }

        @Test
        @DisplayName("page size")
        void pageSize() {
            var pageSize = 42;
            assertThat(builder().setPageSize(pageSize).getPageSize())
                    .isEqualTo(pageSize);
        }

        @Test
        @DisplayName("catch-up page size")
        void catchUpPageSize() {
            var catchUpPageSize = 499;
            assertThat(builder().setCatchUpPageSize(catchUpPageSize).getCatchUpPageSize())
                    .isEqualTo(catchUpPageSize);
        }
    }

    @Nested
    @DisplayName("throw `NullPointerException` if attempting to get the unset value of")
    class ThrowNpe {

        @Test
        @DisplayName("delivery strategy")
        void strategy() {
            assertNpe(() -> builder().getStrategy());
        }

        @Test
        @DisplayName("Inbox storage")
        void inboxStorage() {
            assertNpe(() -> builder().getInboxStorage());
        }

        @Test
        @DisplayName("Catch-up storage")
        void catchUpStorage() {
            assertNpe(() -> builder().getCatchUpStorage());
        }

        @Test
        @DisplayName("work registry")
        void workRegistry() {
            assertNpe(() -> builder().getWorkRegistry());
        }

        @Test
        @DisplayName("deduplication window")
        void deduplicationWindow() {
            assertNpe(() -> builder().getDeduplicationWindow());
        }

        @Test
        @DisplayName("delivery monitor")
        void deliveryMonitor() {
            assertNpe(() -> builder().getDeliveryMonitor());
        }

        @Test
        @DisplayName("page size")
        void pageSize() {
            assertNpe(() -> builder().getPageSize());
        }
    }
}
