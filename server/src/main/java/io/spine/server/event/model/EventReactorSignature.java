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

package io.spine.server.event.model;

import com.google.common.reflect.TypeToken;
import io.spine.base.EventMessage;
import io.spine.base.RejectionMessage;
import io.spine.server.event.React;
import io.spine.server.event.Reaction;
import io.spine.server.model.ParameterSpec;
import io.spine.server.model.ReturnTypes;
import io.spine.server.type.EventEnvelope;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * The signature of {@link EventReactorMethod}.
 */
final class EventReactorSignature extends EventAcceptingSignature<EventReactorMethod> {

    private static final ReturnTypes TYPES = new ReturnTypes(
            TypeToken.of(EventMessage.class),
            new TypeToken<Iterable<EventMessage>>() {},
            new TypeToken<Optional<EventMessage>>() {}
    ).butNot(
            TypeToken.of(RejectionMessage.class),
            new TypeToken<Iterable<RejectionMessage>>() {},
            new TypeToken<Optional<RejectionMessage>>() {}
    );

    EventReactorSignature() {
        super(React.class);
    }

    @Override
    protected ReturnTypes returnTypes() {
        return TYPES;
    }

    @Override
    public EventReactorMethod create(Method method, ParameterSpec<EventEnvelope> params) {
        return new EventReactorMethod(method, params);
    }

    /**
     * Tells that the method may state that a reaction isn't needed by returning
     * {@link io.spine.server.event.NoReaction NoReaction}.
     */
    @Override
    public boolean mayReturnIgnored() {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>For {@link Reaction} subclasses, the {@code whenever()} method is assumed to have
     * the {@code @React} annotation even if it's not explicitly present, making it unnecessary
     * for users to add the annotation.
     */
    @Override
    protected boolean skipMethod(Method method) {
        // Check if method is annotated with @React
        if (method.isAnnotationPresent(annotation())) {
            return false;
        }
        
        // For Reaction subclasses, assume @React on the whenever() method
        if (Reaction.class.isAssignableFrom(method.getDeclaringClass()) &&
            "whenever".equals(method.getName())) {
            return false;
        }
        
        return true;
    }
}
