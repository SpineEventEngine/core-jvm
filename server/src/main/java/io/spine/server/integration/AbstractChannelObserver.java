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
package io.spine.server.integration;

import com.google.protobuf.Message;
import io.grpc.stub.StreamObserver;
import io.spine.annotation.SPI;
import io.spine.core.BoundedContextName;
import io.spine.logging.WithLogging;
import io.spine.server.transport.Subscriber;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static io.spine.util.Exceptions.newIllegalStateException;
import static java.lang.String.format;

/**
 * Base routines for the {@linkplain Subscriber#addObserver(StreamObserver) subscriber observers}.
 */
@SPI
public abstract class AbstractChannelObserver
        implements StreamObserver<ExternalMessage>, WithLogging {

    private final BoundedContextName boundedContextName;
    private final Class<? extends Message> messageClass;
    private final AtomicBoolean completed = new AtomicBoolean(false);

    /**
     * Creates a new instance of the observer.
     *
     * @param context
     *         the name of the Bounded Context in which the created observer exists
     * @param messageClass
     *         the type of the observed messages, which are transferred wrapped
     *         into {@code ExternalMessage}
     */
    protected AbstractChannelObserver(BoundedContextName context,
                                      Class<? extends Message> messageClass) {
        this.boundedContextName = context;
        this.messageClass = messageClass;
    }

    /**
     * Handles the {@linkplain ExternalMessage message} received via this channel.
     *
     * <p>This behaviour is specific to the particular channel observer implementation.
     *
     * @param message
     *         the received message
     */
    protected abstract void handle(ExternalMessage message);

    @Override
    public void onError(Throwable t) {
        var wasCompleted = !completed.compareAndSet(false, true);
        if (wasCompleted) {
            logger().atWarning().log(() -> format(
                    "Observer for `%s` received an error despite being closed.",
                    messageClass.getName()));
        }
        throw newIllegalStateException("Error caught when observing the incoming " +
                                               "messages of type %s", messageClass);
    }

    @Override
    public void onCompleted() {
        var wasNotCompleted = completed.compareAndSet(false, true);
        checkState(wasNotCompleted, "Observer of `%s` is already closed.", messageClass.getName());
    }

    @Override
    public final void onNext(ExternalMessage message) {
        checkNotNull(message);
        var messageClass = message.getClass().getName();
        var originalMessage = message.getOriginalMessage().getTypeUrl();
        checkState(!completed.get(),
                   "Channel `%s` received message (%s[%s]) despite being closed.",
                   this,
                   messageClass,
                   originalMessage);
        var source = message.getBoundedContextName();
        var sameContext = boundedContextName.equals(source)
                || boundedContextName.isSystemOf(source)
                || source.isSystemOf(boundedContextName);
        if (!sameContext) {
            handle(message);
        }
    }

    /**
     * Returns the name of the Bounded Context in scope of which this observer exists.
     */
    protected final BoundedContextName contextName() {
        return boundedContextName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AbstractChannelObserver that)) {
            return false;
        }
        return Objects.equals(boundedContextName, that.boundedContextName) &&
                Objects.equals(messageClass, that.messageClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(boundedContextName, messageClass);
    }
}
