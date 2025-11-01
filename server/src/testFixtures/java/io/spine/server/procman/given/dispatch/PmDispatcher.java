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
package io.spine.server.procman.given.dispatch;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.ProcessManagerState;
import io.spine.core.Event;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.entity.EntityLifecycle;
import io.spine.server.entity.EntityLifecycleMonitor;
import io.spine.server.entity.TransactionListener;
import io.spine.server.procman.PmCommandEndpoint;
import io.spine.server.procman.PmEventEndpoint;
import io.spine.server.procman.PmTransaction;
import io.spine.server.procman.ProcessManager;
import io.spine.server.procman.ProcessManagerRepository;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventEnvelope;
import io.spine.server.type.MessageEnvelope;
import io.spine.testing.server.NoOpLifecycle;

import java.util.function.BiFunction;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A test utility for dispatching commands and events to a {@code ProcessManager} in test purposes.
 */
@VisibleForTesting
@SuppressWarnings("rawtypes") // to simplify signatures this test env. class
public final class PmDispatcher {

    private static final
    ImmutableMap<Class<? extends MessageEnvelope>, EndpointFn> endpoints =
            ImmutableMap.<Class<? extends MessageEnvelope>, EndpointFn>
                    builder()
                    .put(CommandEnvelope.class,
                         (p, m) -> TestPmCommandEndpoint.dispatch(p, (CommandEnvelope) m))
                    .put(EventEnvelope.class,
                         (p, m) -> TestPmEventEndpoint.dispatch(p, (EventEnvelope) m))
                    .build();

    /** Prevents this utility class from instantiation. */
    private PmDispatcher() {
    }

    /**
     * Dispatches the {@linkplain CommandEnvelope envelope} to the given {@code ProcessManager}.
     *
     * @return the list of {@linkplain Event events}, being the envelope output.
     */
    @CanIgnoreReturnValue
    public static DispatchOutcome dispatch(ProcessManager<?, ?, ?> pm, MessageEnvelope envelope) {
        checkNotNull(pm);
        checkNotNull(envelope);
        var fn = endpoints.get(envelope.getClass());
        var events = fn.apply(pm, envelope);
        return events;
    }

    /**
     * Functional interface for an entry in the map matching message envelope
     * class with a test endpoint which dispatches such envelopes.
     * @see #endpoints
     */
    private interface EndpointFn
            extends BiFunction<ProcessManager<?, ?, ?>, MessageEnvelope, DispatchOutcome> {
    }

    /**
     * A test-only implementation of an {@link PmCommandEndpoint}, that dispatches
     * commands to an instance of {@code ProcessManager} and returns the list of events.
     *
     * @param <I>
     *         the type of {@code ProcessManager} identifier
     * @param <P>
     *         the type of {@code ProcessManager}
     * @param <S>
     *         the type of {@code ProcessManager} state object
     */
    private static class TestPmCommandEndpoint<I,
                                               P extends ProcessManager<I, S, ?>,
                                               S extends ProcessManagerState<I>>
            extends PmCommandEndpoint<I, P> {

        private TestPmCommandEndpoint(CommandEnvelope cmd) {
            super(new TestPmRepository<>(), cmd);
        }

        private static <I, P extends ProcessManager<I, S, ?>, S extends ProcessManagerState<I>>
        DispatchOutcome dispatch(P manager, CommandEnvelope envelope) {
            var endpoint = new TestPmCommandEndpoint<I, P, S>(envelope);
            return endpoint.runTransactionFor(manager);
        }
    }

    /**
     * A test-only implementation of an {@link PmEventEndpoint}, that dispatches
     * events to an instance of {@code ProcessManager} and returns the list of events.
     *
     * @param <I>
     *         the type of {@code ProcessManager} identifier
     * @param <P>
     *         the type of {@code ProcessManager}
     * @param <S>
     *         the type of {@code ProcessManager} state object
     */
    private static class TestPmEventEndpoint<I,
                                             P extends ProcessManager<I, S, ?>,
                                             S extends ProcessManagerState<I>>
            extends PmEventEndpoint<I, P> {

        private TestPmEventEndpoint(EventEnvelope event) {
            super(new TestPmRepository<>(), event);
        }

        private static <I, P extends ProcessManager<I, S, ?>, S extends ProcessManagerState<I>>
        DispatchOutcome dispatch(P manager, EventEnvelope event) {
            var endpoint = new TestPmEventEndpoint<I, P, S>(event);
            return endpoint.runTransactionFor(manager);
        }
    }

    /**
     * Test-only process manager repository which uses {@link TestPmTransaction} and
     * {@linkplain NoOpLifecycle NO-OP entity lifecycle}.
     */
    private static class TestPmRepository<I,
                                          P extends ProcessManager<I, S, ?>,
                                          S extends ProcessManagerState<I>>
            extends ProcessManagerRepository<I, P, S> {

        @SuppressWarnings("unchecked") // OK for this test implementation.
        @Override
        protected PmTransaction<?, ?, ?> beginTransactionFor(P manager) {
            PmTransaction<?, ?, ?> tx = new TestPmTransaction(manager);
            TransactionListener listener = EntityLifecycleMonitor.newInstance(this, manager.id());
            tx.setListener(listener);
            return tx;
        }

        @Override
        public EntityLifecycle lifecycleOf(I id) {
            return NoOpLifecycle.instance();
        }
    }
}
