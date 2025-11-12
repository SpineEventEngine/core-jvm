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

package io.spine.server.event.model;

import com.google.errorprone.annotations.Immutable;
import io.spine.server.model.DispatchKey;
import io.spine.server.model.Receptor;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;
import io.spine.type.MessageClass;

/**
 * A handler method that may handle rejections.
 *
 * @param <T>
 *         the type of the object to which the handler method belongs
 * @param <R>
 *         the type of the produced message classes
 */
@Immutable
interface RejectionHandler<T, R extends MessageClass<?>>
        extends Receptor<T, EventClass, EventEnvelope, R> {

    /**
     * Obtains the specification of parameters for this method.
     */
    EventAcceptingMethodParams parameterSpec();

    /** Tells whether the handler method is for a rejection. **/
    default boolean handlesRejection() {
        return parameterSpec().acceptsCommand();
    }

    /**
     * {@inheritDoc}
     *
     * <p>In case this method is declared to handle a rejection, a rejection-specific
     * dispatch key is created.
     */
    @Override
    default DispatchKey key() {
        if (handlesRejection()) {
            var dispatchKey = RejectionDispatchKeys.of(messageClass(), rawMethod());
            return dispatchKey;
        }
        return Receptor.super.key();
    }
}
