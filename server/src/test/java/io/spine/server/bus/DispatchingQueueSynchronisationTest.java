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

package io.spine.server.bus;

import io.spine.server.bus.given.stock.JowDonsIndex;
import io.spine.server.bus.given.stock.ShareAggregate;
import io.spine.test.bus.Buy;
import io.spine.test.bus.Sell;
import io.spine.test.bus.ShareId;
import io.spine.testing.SlowTest;
import io.spine.testing.server.blackbox.BlackBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static io.spine.base.Identifier.newUuid;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SlowTest
@DisplayName("When posting commands in parallel")
class DispatchingQueueSynchronisationTest {

    @Test
    @DisplayName("`Bus` should not lock with its system counterpart")
    void deadlock() throws InterruptedException {
        var executor = (ThreadPoolExecutor) newFixedThreadPool(10);
        try (var context = BlackBox.singleTenantWith(
                ShareAggregate.class,
                new JowDonsIndex.Repository())
        ) {
            var taskCount = 10;
            var shares =
                    Stream.generate(() -> ShareId.newBuilder()
                                  .setValue(newUuid())
                                  .build())
                          .limit(taskCount)
                          .collect(toImmutableList());
            shares.forEach(share -> executor.execute(() -> {
                var buy = Buy.newBuilder()
                        .setShare(share)
                        .setAmount(42)
                        .build();
                context.receivesCommand(buy);
                sleepUninterruptibly(Duration.ofSeconds(1));
                var sell = Sell.newBuilder()
                        .setShare(share)
                        .setAmount(12)
                        .build();
                context.receivesCommand(sell);
            }));
            executor.awaitTermination(5, SECONDS);
            assertEquals(shares.size(), executor.getCompletedTaskCount(),
                         "Not all tasks have been executed. Most likely, a dead lock is reached.");
        }
    }
}
