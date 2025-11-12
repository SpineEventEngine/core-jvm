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

package io.spine.server.aggregate.model;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.EventMessage;
import io.spine.server.aggregate.Apply;
import io.spine.server.model.AccessModifier;
import io.spine.server.model.AllowedParams;
import io.spine.server.model.ExtractedArguments;
import io.spine.server.model.MethodParams;
import io.spine.server.model.ParameterSpec;
import io.spine.server.model.ReceptorSignature;
import io.spine.server.model.ReturnTypes;
import io.spine.server.type.EventEnvelope;

import java.lang.reflect.Method;

import static io.spine.server.model.ReturnTypes.onlyVoid;
import static io.spine.server.model.TypeMatcher.classImplementing;

/**
 * The signature of the {@link Applier} method.
 */
final class EventApplierSignature extends ReceptorSignature<Applier, EventEnvelope> {

    EventApplierSignature() {
        super(Apply.class);
    }

    @Override
    protected ReturnTypes returnTypes() {
        return onlyVoid();
    }

    @Override
    public Applier create(Method method, ParameterSpec<EventEnvelope> params) {
        return new Applier(method, params);
    }

    @Override
    protected ImmutableSet<AccessModifier> modifier() {
        return ImmutableSet.of(AccessModifier.PRIVATE);
    }

    @Override
    public AllowedParams<EventEnvelope> params() {
        return EventApplierParams.ALLOWED;
    }

    /**
     * This method never returns any results.
     */
    @Override
    public boolean mayReturnIgnored() {
        return true;
    }

    /**
     * Allowed combinations of parameters for {@link Applier} methods.
     */
    @VisibleForTesting
    @Immutable
    enum EventApplierParams implements ParameterSpec<EventEnvelope> {

        MESSAGE {
            @Override
            public boolean matches(MethodParams params) {
                return params.is(classImplementing(EventMessage.class));
            }

            @Override
            public ExtractedArguments extractArguments(EventEnvelope event) {
                return ExtractedArguments.ofOne(event.message());
            }
        };

        private static final AllowedParams<EventEnvelope> ALLOWED = new AllowedParams<>(MESSAGE);
    }
}
