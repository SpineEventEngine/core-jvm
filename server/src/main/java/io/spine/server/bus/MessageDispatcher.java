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

package io.spine.server.bus;

import com.google.common.collect.ImmutableSet;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.type.MessageEnvelope;
import io.spine.type.MessageClass;

/**
 * A dispatcher of a message.
 *
 * @param <C>
 *         the type of message class of dispatched messages
 * @param <E>
 *         the type of the message envelopes
 */
public interface MessageDispatcher<C extends MessageClass<?>, E extends MessageEnvelope<?, ?, ?>> {

    /**
     * Obtains a set of message classes that can be processed by this dispatcher.
     *
     * @return non-empty set of message classes
     */
    ImmutableSet<C> messageClasses();

    /**
     * Dispatches the message contained in the given envelope and returns the outcome.
     *
     * @param envelope
     *         the envelope with the message
     * @return the outcome of dispatching
     */
    DispatchOutcome dispatch(E envelope);

    /**
     * Checks if this dispatcher can dispatch the given message.
     *
     * <p>This method does not check that the type of the message is one of
     * the {@linkplain #messageClasses() dispatched message classes}. Instead, it validates
     * the message upon some custom rules of this dispatcher.
     *
     * <p>Extend this method to forbid messages from being dispatched to this instance.
     *
     * @param envelope
     *         the message to check
     * @return {@code true} is the given message can be dispatched by this dispatcher,
     *         {@code false} otherwise
     */
    default boolean canDispatch(E envelope) {
        return true;
    }
}
