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

package io.spine.server.bus;

import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import com.google.protobuf.Message;
import io.spine.annotation.Internal;
import io.spine.core.Signal;
import io.spine.core.SignalId;
import io.spine.server.BoundedContext;
import io.spine.server.ContextAware;
import io.spine.server.type.SignalEnvelope;
import io.spine.type.MessageClass;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.util.Collection;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@code Bus}, which delivers a single message to multiple dispatchers.

 * @param <M> the type of outer objects (containing messages of interest) that are posted to the bus
 * @param <E> the type of envelopes for outer objects used by this bus
 * @param <C> the type of message class
 * @param <D> the type of dispatches used by this bus
 */
public abstract class MulticastBus<M extends Signal<?, ?, ?>,
                                   E extends SignalEnvelope<?, M, ?>,
                                   C extends MessageClass<? extends Message>,
                                   D extends MessageDispatcher<C, E>>
        extends Bus<M, E, C, D>
        implements ContextAware {

    /**
     * The listener of the dispatching, set when the bus is
     * {@linkplain #registerWith(BoundedContext) registered} with a context.
     */
    private @MonotonicNonNull MulticastDispatchListener listener;

    protected MulticastBus(BusBuilder<?, M, E, C, D> builder) {
        super(builder);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Obtains the listener of the dispatching from the {@code Delivery} of the context.
     */
    @Override
    @Internal
    @OverridingMethodsMustInvokeSuper
    public void registerWith(BoundedContext context) {
        this.listener = context.internalAccess()
                               .delivery()
                               .dispatchListener();
    }

    private MulticastDispatchListener listener() {
        checkState(listener != null,
                   "The bus is not registered with a Bounded Context.");
        return listener;
    }

    /**
     * Calls the dispatchers for the {@code messageEnvelope}.
     *
     * @param messageEnvelope
     *         the message envelope to pass to the dispatchers
     * @return the number of the dispatchers called or {@code 0} if there weren't any
     */
    @SuppressWarnings("ResultOfMethodCallIgnored"
            /* Dispatching outcomes are reported via system events, if needed. */)
    protected int callDispatchers(E messageEnvelope) {
        Collection<D> dispatchers = registry().dispatchersOf(messageEnvelope);
        for (var dispatcher : dispatchers) {
            dispatcher.dispatch(messageEnvelope);
        }
        return dispatchers.size();
    }

    @Override
    protected final void onDispatchingStarted(SignalId signal) {
        listener().onStarted(signal);
    }

    @Override
    protected final void onDispatched(SignalId signal) {
        listener().onCompleted(signal);
    }
}
