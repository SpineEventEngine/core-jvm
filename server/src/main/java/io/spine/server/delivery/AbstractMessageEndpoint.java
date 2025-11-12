/*
 * Copyright 2023, TeamDev. All rights reserved.
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

import io.spine.annotation.Internal;
import io.spine.base.Error;
import io.spine.base.Errors;
import io.spine.base.Identifier;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.type.SignalEnvelope;
import io.spine.string.Stringifiers;

import static java.lang.String.format;

/**
 * An abstract base for message endpoints.
 *
 * <p>Upon a run-time dispatching, catches all {@code Exception}s thrown,
 * and transforms them into a {@link DispatchOutcome}.
 *
 * @param <I>
 *         the type of target identifier
 * @param <M>
 *         the type of message envelope being delivered
 */
@Internal
public abstract class AbstractMessageEndpoint<I, M extends SignalEnvelope<?, ?, ?>>
        implements MessageEndpoint<I, M> {

    /** The message which needs to dispatched. */
    private final M envelope;

    protected AbstractMessageEndpoint(M envelope) {
        this.envelope = envelope;
    }

    /**
     * {@inheritDoc}
     *
     * <p>All {@code Exception}s which may occur during
     * the {@linkplain #performDispatch(Object) dispatching} are caught
     * and transformed into an erroneous {@code DispatchOutcome}.
     */
    @Override
    @SuppressWarnings("OverlyBroadCatchBlock")  /* Handling all exceptions, by design. */
    public final DispatchOutcome dispatchTo(I targetId) {
        try {
            var outcome = performDispatch(targetId);
            return outcome;
        } catch (Exception e) {
            return onFailedDispatch(e, targetId);
        }
    }

    /**
     * Performs actual dispatching of the signal to the respective target by the passed ID.
     *
     * @param targetId
     *         the ID of the target to which the signal should be dispatched
     * @return an outcome of the dispatch operation
     */
    protected abstract DispatchOutcome performDispatch(I targetId);

    private DispatchOutcome onFailedDispatch(Exception failure, I targetId) {
        var error = failureToError(failure, targetId);
        var outcome = DispatchOutcome.newBuilder()
                .setPropagatedSignal(envelope.messageId())
                .setError(error)
                .build();
        return outcome;
    }

    private Error failureToError(Exception failure, I targetId) {
        var error = Errors.fromThrowable(failure);
        var repoName = repository().getClass().getSimpleName();
        var message =
                format("Runtime error when dispatching signal `%s` " +
                               "to the entity with ID `%s` of repository `%s`. %s",
                       Stringifiers.toString(envelope.message()),
                       Identifier.toString(targetId),
                       repoName,
                       error.getMessage());
        var result = error.toBuilder()
                .setMessage(message)
                .build();
        return result;
    }

    /**
     * Obtains the envelope of the message processed by this endpoint.
     */
    protected final M envelope() {
        return envelope;
    }
}
