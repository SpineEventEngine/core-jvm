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

package io.spine.server.aggregate.given.dispatch;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.EntityState;
import io.spine.core.Command;
import io.spine.core.Event;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.AggregateRepository;
import io.spine.server.aggregate.AggregateTestSupport;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.entity.EntityLifecycle;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventEnvelope;
import io.spine.testing.server.NoOpLifecycle;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A test utility to dispatch commands to an {@code Aggregate} in test purposes.
 */
@VisibleForTesting
public class AggregateMessageDispatcher {

    /** Prevents instantiation of this utility class. */
    private AggregateMessageDispatcher() {
    }

    /**
     * Dispatches the {@linkplain CommandEnvelope command envelope} and applies the resulting events
     * to the given {@code Aggregate}.
     *
     * @return the list of event messages.
     */
    @CanIgnoreReturnValue
    public static DispatchOutcome
    dispatchCommand(Aggregate<?, ?, ?> aggregate, CommandEnvelope command) {
        checkNotNull(aggregate);
        checkNotNull(command);
        return AggregateTestSupport
                .dispatchCommand(new TestAggregateRepository<>(), aggregate, command);
    }

    /**
     * Dispatches the {@linkplain CommandEnvelope command envelope} and applies the resulting events
     * to the given {@code Aggregate}.
     *
     * @return the list of event messages.
     */
    @CanIgnoreReturnValue
    public static <I, A extends Aggregate<I, S, ?>, S extends EntityState<I>> DispatchOutcome
    dispatchCommand(A aggregate, AggregateRepository<I, A, S> repository, CommandEnvelope command) {
        checkNotNull(aggregate);
        checkNotNull(command);
        return AggregateTestSupport.dispatchCommand(repository, aggregate, command);
    }

    /**
     * Dispatches the command and applies the resulting events
     * to the given {@code Aggregate}.
     *
     * @return the list of event messages.
     */
    @CanIgnoreReturnValue
    public static DispatchOutcome
    dispatchCommand(Aggregate<?, ?, ?> aggregate, Command command) {
        checkNotNull(aggregate);
        checkNotNull(command);
        var ce = CommandEnvelope.of(command);
        return AggregateTestSupport
                .dispatchCommand(new TestAggregateRepository<>(), aggregate, ce);
    }

    /**
     * Dispatches the {@linkplain EventEnvelope event envelope} and applies the resulting events
     * to the given {@code Aggregate}.
     *
     * @return the list of event messages.
     */
    @CanIgnoreReturnValue
    public static DispatchOutcome
    dispatchEvent(Aggregate<?, ?, ?> aggregate, EventEnvelope event) {
        checkNotNull(aggregate);
        checkNotNull(event);
        return AggregateTestSupport
                .dispatchEvent(new TestAggregateRepository<>(), aggregate, event);
    }

    /**
     * Dispatches the event and applies the resulting events
     * to the given {@code Aggregate}.
     *
     * @return the list of event messages.
     */
    @CanIgnoreReturnValue
    public static DispatchOutcome
    dispatchEvent(Aggregate<?, ?, ?> aggregate, Event event) {
        checkNotNull(aggregate);
        checkNotNull(event);
        var env = EventEnvelope.of(event);
        return AggregateTestSupport.dispatchEvent(new TestAggregateRepository<>(), aggregate, env);
    }

    /**
     * Test-only aggregate repository that uses {@linkplain NoOpLifecycle NO-OP entity lifecycle}.
     */
    private static class TestAggregateRepository<I,
                                                 A extends Aggregate<I, S, ?>,
                                                 S extends EntityState<I>>
            extends AggregateRepository<I, A, S> {

        @Override
        public EntityLifecycle lifecycleOf(I id) {
            return NoOpLifecycle.instance();
        }
    }
}
