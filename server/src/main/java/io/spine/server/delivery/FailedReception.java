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

import io.spine.annotation.SPI;
import io.spine.base.Error;

/**
 * The evidence of an {@link InboxMessage} which has failed to be handled
 * by its receptor, such as an event or a command handler method.
 *
 * <p>End-users may choose to do one of the following:
 *
 * <ul>
 *     <li>{@linkplain #markDelivered() mark} the message as delivered;
 *     <li>{@linkplain #repeatDispatching() repeat dispatching} of the message immediately.
 * </ul>
 *
 * <p>Alternatively, end-users may choose to define their own way of reacting
 * to reception failures by implementing a custom {@code FailedReception.Action},
 * and returning it via the corresponding
 * {@linkplain DeliveryMonitor#onReceptionFailure(FailedReception) API} of {@code DeliveryMonitor}.
 */
public final class FailedReception {

    private final InboxMessage message;
    private final Error error;
    private final Conveyor conveyor;
    private final RepeatDispatching repeat;

    /**
     * Creates an instance of the failed reception.
     *
     * @param message
     *         the message which caused the failure
     * @param error
     *         the details of the failure
     * @param conveyor
     *         the conveyor holding the {@code InboxMessage}s currently being delivered;
     *         used to manipulate the message upon end-user's choice
     * @param repeat
     *         a callback invoked to repeat the dispatching
     *         of the {@code InboxMessage} immediately
     */
    FailedReception(InboxMessage message,
                    Error error,
                    Conveyor conveyor,
                    RepeatDispatching repeat) {
        this.message = message;
        this.error = error;
        this.conveyor = conveyor;
        this.repeat = repeat;
    }

    /**
     * Returns the original {@code InboxMessage}.
     */
    public InboxMessage message() {
        return message;
    }

    /**
     * Returns the failure.
     */
    public Error error() {
        return error;
    }

    /**
     * Returns an action which marks the message
     * as {@linkplain InboxMessageStatus#DELIVERED delivered}.
     *
     * <p>The message will be automatically removed from its inbox
     * at the end of the delivery stage.
     */
    @SuppressWarnings("WeakerAccess" /* Part of the public API. */)
    public Action markDelivered() {
        return () -> conveyor.markDelivered(message);
    }

    /**
     * Returns an action immediately repeats the dispatching of the message.
     */
    @SuppressWarnings("WeakerAccess" /* Part of the public API. */)
    public Action repeatDispatching() {
        return repeat::dispatchAgain;
    }

    /**
     * An action to take in relation to the failed reception.
     */
    @SPI
    @FunctionalInterface
    public interface Action {

        /**
         * Executes an action.
         */
        void execute();
    }
}
