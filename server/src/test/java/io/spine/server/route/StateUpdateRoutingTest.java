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

package io.spine.server.route;

import com.google.common.collect.ImmutableSet;
import com.google.common.testing.NullPointerTester;
import io.spine.core.EventContext;
import io.spine.core.MessageId;
import io.spine.protobuf.AnyPacker;
import io.spine.protobuf.TypeConverter;
import io.spine.server.given.context.switchman.LogState;
import io.spine.server.type.given.GivenEvent;
import io.spine.system.server.event.EntityStateChanged;
import io.spine.testing.core.given.GivenVersion;
import io.spine.type.TypeUrl;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Time.currentTime;

@DisplayName("`StateUpdateRouting` should")
class StateUpdateRoutingTest {

    private static final EventContext emptyContext = EventContext.getDefaultInstance();

    @Test
    @DisplayName("not accept `null`s")
    void notAcceptNulls() {
        new NullPointerTester()
                .setDefault(EventContext.class, emptyContext)
                .testAllPublicInstanceMethods(StateUpdateRouting.newInstance(Long.class));
    }
    
    @Test
    @DisplayName("route messages with defined routes")
    void routeMessagesByRoutes() {
        var counterKey = "sample_key";
        var routing = StateUpdateRouting.newInstance(Long.class)
                .route(LogState.class, (log, context) ->
                        ImmutableSet.of((long) log.getCountersOrThrow(counterKey))
                );
        var counter = 42;
        var log = LogState.newBuilder()
                .putCounters(counterKey, counter)
                .build();
        var targets = routing.invoke(log, emptyContext);
        assertThat(targets).containsExactly((long)counter);
    }

    @Test
    @DisplayName("compose an `EventRoute` for `EntityStateChanged` events")
    void createEventRoute() {
        var counterKey = "test_key";
        var routing = StateUpdateRouting.newInstance(Long.class)
                .route(LogState.class, (log, context) ->
                               ImmutableSet.of((long) log.getCountersOrThrow(counterKey))
                );
        var counter = 42;
        var builder = LogState.newBuilder()
                .putCounters(counterKey, counter);
        var log = builder.build();
        var oldState = builder.putCounters(counterKey, 147).build();
        var entityId = entityId();
        var event = EntityStateChanged.newBuilder()
                .setEntity(entityId)
                .setOldState(AnyPacker.pack(oldState))
                .setNewState(AnyPacker.pack(log))
                .addSignalId(GivenEvent.arbitrary().messageId())
                .setWhen(currentTime())
                .build();
        var eventRoute = routing.eventRoute();
        var targets = eventRoute.invoke(event, emptyContext);
        assertThat(targets).containsExactly((long) counter);
    }

    @NonNull
    private static MessageId entityId() {
        return MessageId.newBuilder()
                .setId(TypeConverter.toAny(112))
                .setTypeUrl(TypeUrl.of(LogState.class)
                                   .value())
                .setVersion(GivenVersion.withNumber(1))
                .build();
    }
}
