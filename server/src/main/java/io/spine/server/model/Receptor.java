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

package io.spine.server.model;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.dispatch.Success;
import io.spine.server.type.MessageEnvelope;
import io.spine.type.MessageClass;

import org.jspecify.annotations.Nullable;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * Describes a method that accepts a message and optionally its context.
 *
 * @param <T>
 *         the type of the target object
 * @param <C>
 *         the type of the incoming message class
 * @param <E>
 *         the type of the {@link MessageEnvelope} wrapping the method arguments
 * @param <P>
 *         the type of the produced message classes
 */
@Immutable
public interface Receptor<T,
                          C extends MessageClass<?>,
                          E extends MessageEnvelope<?, ?, ?>,
                          P extends MessageClass<?>> {
    /**
     * Obtains the type of the incoming message class.
     */
    C messageClass();

    void discoverAttributes();

    /**
     * Obtains parameters of the method.
     */
    MethodParams params();

    /**
     * Obtains the set of method attributes configured for this method.
     */
    Set<Attribute<?>> attributes();

    /**
     * Obtains the handling method.
     */
    Method rawMethod();

    /**
     * Obtains the filter to apply to the messages received by this method.
     */
    ArgumentFilter filter();

    /**
     * Retrieves the message classes produced by this receptor.
     */
    Set<P> producedMessages();

    /**
     * Converts the raw method result to a {@linkplain Success successful propagation outcome}.
     *
     * @param rawResult
     *         the return value of the method
     * @param target
     *         the method receiver
     * @param handledSignal
     *         the handled signal
     * @return the method result
     * @throws IllegalOutcomeException
     *         if the method produced a result of an unexpected format
     */
    Success toSuccessfulOutcome(@Nullable Object rawResult, T target, E handledSignal)
            throws IllegalOutcomeException;

    /**
     * Invokes the method to handle a message and its context passed in the {@code envelope}.
     *
     * @param target
     *         the target object on which call the method
     * @param envelope
     *         the {@link MessageEnvelope} wrapping the method arguments
     * @return the result of message handling
     */
    @CanIgnoreReturnValue
    DispatchOutcome invoke(T target, E envelope);

    /**
     * Tells if the passed method is {@linkplain ExternalAttribute#EXTERNAL external}.
     */
    default boolean isExternal() {
        return attributes().contains(ExternalAttribute.EXTERNAL);
    }

    /**
     * Tells if this receptor handles a domestic message, which means that the method
     * is not marked as {@linkplain ExternalAttribute#EXTERNAL external}).
     */
    default boolean isDomestic() {
        return !isExternal();
    }

    /**
     * Ensures that the {@code external} attribute of the method is the one expected.
     *
     * <p>This method is for checking that an {@code external} attribute of a message context
     * matches the one set for the handler method.
     *
     * @param expectedValue an expected value of the {@code external} attribute
     * @throws SignalOriginMismatchError is thrown if the value does not meet the expectation
     * @see ExternalAttribute
     */
    default void ensureExternalMatch(boolean expectedValue) throws SignalOriginMismatchError {
        var actualValue = isExternal();
        if (actualValue != expectedValue) {
            throw new SignalOriginMismatchError(this, expectedValue, actualValue);
        }
    }

    /**
     * Creates a dispatch key for the receptor by the {@linkplain #messageClass() message class}.
     */
    default DispatchKey key() {
        var rawCls = messageClass().value();
        return new DispatchKey(rawCls, filter(), null);
    }
}
