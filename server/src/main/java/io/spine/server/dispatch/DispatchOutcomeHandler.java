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

package io.spine.server.dispatch;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.base.Error;
import io.spine.core.Command;
import io.spine.core.Event;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A holder of a {@code DispatchOutcome}.
 */
public final class DispatchOutcomeHandler {

    @SuppressWarnings({"rawtypes", "UnnecessaryLambda"})
    private static final OutcomeHandler DO_NOTHING = o -> {
    };

    private final DispatchOutcome outcome;

    private OutcomeHandler<Error> errorHandler = doNothing();
    private OutcomeHandler<Success> successHandler = doNothing();
    private OutcomeHandler<Success> afterSuccessHandler = doNothing();
    private OutcomeHandler<List<Command>> producedCommandsHandler = doNothing();
    private OutcomeHandler<List<Event>> producedEventsHandler = doNothing();
    private OutcomeHandler<Event> rejectionHandler = doNothing();
    private OutcomeHandler<Interruption> interruptionHandler = doNothing();
    private OutcomeHandler<Ignore> ignoreHandler = doNothing();

    private DispatchOutcomeHandler(DispatchOutcome outcome) {
        this.outcome = outcome;
    }

    /**
     * Creates instance for the passed outcome.
     */
    public static DispatchOutcomeHandler from(DispatchOutcome outcome) {
        checkNotNull(outcome);
        return new DispatchOutcomeHandler(outcome);
    }

    /**
     * Accepts {@code Error} case handler.
     */
    @CanIgnoreReturnValue
    public DispatchOutcomeHandler onError(OutcomeHandler<Error> handler) {
        this.errorHandler = checkNotNull(handler);
        return this;
    }

    /**
     * Accepts {@code Interruption} case handler.
     */
    @CanIgnoreReturnValue
    public DispatchOutcomeHandler onInterruption(OutcomeHandler<Interruption> handler) {
        this.interruptionHandler = checkNotNull(handler);
        return this;
    }

    /**
     * Accepts {@code Ignored} case handler.
     */
    @CanIgnoreReturnValue
    public DispatchOutcomeHandler onIgnored(OutcomeHandler<Ignore> handler) {
        this.ignoreHandler = checkNotNull(handler);
        return this;
    }

    /**
     * Accepts {@code Success} case handler.
     *
     * <p>The success case handle is invoked before the specific handlers like {@code onRejection},
     * {@code onCommands} or {@code onEvents}.
     *
     * @see #afterSuccess(OutcomeHandler)
     */
    @CanIgnoreReturnValue
    public DispatchOutcomeHandler onSuccess(OutcomeHandler<Success> handler) {
        this.successHandler = checkNotNull(handler);
        return this;
    }

    /**
     * Accepts {@code Success} case handler.
     *
     * <p>The handler is invoked after the specific handlers like {@code onRejection},
     * {@code onCommands} or {@code onEvents}.
     *
     * @see #onSuccess(OutcomeHandler)
     */
    @CanIgnoreReturnValue
    public DispatchOutcomeHandler afterSuccess(OutcomeHandler<Success> handler) {
        this.afterSuccessHandler = checkNotNull(handler);
        return this;
    }

    /**
     * Accepts {@code rejection} success case handler.
     */
    @CanIgnoreReturnValue
    public DispatchOutcomeHandler onRejection(OutcomeHandler<Event> handler) {
        this.rejectionHandler = checkNotNull(handler);
        return this;
    }

    /**
     * Accepts {@code ProducedCommands} success case handler.
     */
    @CanIgnoreReturnValue
    public DispatchOutcomeHandler onCommands(OutcomeHandler<List<Command>> handler) {
        this.producedCommandsHandler = checkNotNull(handler);
        return this;
    }

    /**
     * Accepts {@code ProducedEvents} success case handler.
     */
    @CanIgnoreReturnValue
    public DispatchOutcomeHandler onEvents(OutcomeHandler<List<Event>> handler) {
        this.producedEventsHandler = checkNotNull(handler);
        return this;
    }

    /**
     * Handles the {@code outcome} with the configured handlers and returns the {@code outcome}
     * to the caller.
     *
     * <p>If no handlers were configured or the outcome does not match the configured handlers,
     * just returns the outcome.
     */
    @CanIgnoreReturnValue
    public DispatchOutcome handle() {
        switch (outcome.getResultCase()) {
            case SUCCESS:
                handleSuccess(outcome.getSuccess());
                break;
            case ERROR:
                errorHandler.handle(outcome.getError());
                break;
            case INTERRUPTED:
                interruptionHandler.handle(outcome.getInterrupted());
                break;
            case IGNORED:
                ignoreHandler.handle(outcome.getIgnored());
                break;
            case RESULT_NOT_SET:
            default:
                break;
        }
        return outcome;
    }

    private void handleSuccess(Success success) {
        successHandler.handle(success);
        switch (success.getExhaustCase()) {
            case PRODUCED_EVENTS:
                producedEventsHandler.handle(success.getProducedEvents()
                                                    .getEventList());
                break;
            case PRODUCED_COMMANDS:
                producedCommandsHandler.handle(success.getProducedCommands()
                                                      .getCommandList());
                break;
            case REJECTION:
                rejectionHandler.handle(success.getRejection());
                break;
            case EXHAUST_NOT_SET:
            default:
                break;
        }
        afterSuccessHandler.handle(success);
    }

    /**
     * Creates a new no-op handler.
     */
    @SuppressWarnings("unchecked")
    private static <T> OutcomeHandler<T> doNothing() {
        return (OutcomeHandler<T>) DO_NOTHING;
    }
}
