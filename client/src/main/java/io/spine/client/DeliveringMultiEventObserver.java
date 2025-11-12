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

package io.spine.client;

import com.google.common.collect.ImmutableMap;
import io.grpc.stub.StreamObserver;
import io.spine.base.EventMessage;
import io.spine.core.Event;
import io.spine.logging.WithLogging;
import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Delivers events to their consumers.
 *
 * @see #onNext(Event)
 * @see MultiEventConsumers
 */
final class DeliveringMultiEventObserver implements StreamObserver<Event>, WithLogging {

    private final ImmutableMap<Class<? extends EventMessage>, StreamObserver<Event>> observers;
    private final ErrorHandler streamingErrorHandler;

    /**
     * Creates new instance.
     *
     * @param consumers
     *         event consumers
     * @param streamingErrorHandler
     *         the handler for errors which is used when a {@linkplain #onError(Throwable)
     *         streaming error occurs}. When this handler is invoked no further delivery of
     *         events is expected. If {@code null} is passed, default implementation would
     *         log the error.
     */
    DeliveringMultiEventObserver(MultiEventConsumers consumers,
                                 @Nullable ErrorHandler streamingErrorHandler) {
        this.observers = consumers.toObservers();
        this.streamingErrorHandler = nullToDefault(streamingErrorHandler);
    }

    private ErrorHandler nullToDefault(@Nullable ErrorHandler handler) {
        if (handler != null) {
            return handler;
        }
        return throwable -> logger().atError()
                                    .withCause(throwable)
                                    .log(() -> "Error receiving event.");
    }

    @Override
    public void onNext(Event e) {
        var observer = observers.get(e.type());
        requireNonNull(observer);
        observer.onNext(e);
    }

    /** Passes the {@code Throwable} to the configured error handler. */
    @Override
    public void onError(Throwable t) {
        streamingErrorHandler.accept(t);
    }

    /** Does nothing. */
    @Override
    public void onCompleted() {
        // Do nothing.
    }
}
