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

package io.spine.server.aggregate;

import io.spine.core.Command;
import io.spine.grpc.StreamObservers;
import io.spine.server.BoundedContext;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

/**
 * Tests how {@code Aggregate} handles the case when one of events, emitted by a command,
 * corrupts the {@code Aggregate}'s state.
 *
 * @see AbstractAggregateResilienceTest
 * @see CachedAggregateResilienceTest
 */
@DisplayName("Resilient `Aggregate` should")
final class AggregateResilienceTest extends AbstractAggregateResilienceTest {

    /**
     * This method dispatches the passed commands directly to the context's
     * {@code CommandBus} <i>one by one</i>.
     */
    @Override
    void dispatch(List<Command> commands, BoundedContext context) {
        var commandBus = context.commandBus();
        for (var cmd : commands) {
            commandBus.post(cmd, StreamObservers.noOpObserver());
        }
    }
}
