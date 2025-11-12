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
import io.spine.base.EventMessage;
import io.spine.server.event.EventSubscriber;
import io.spine.server.model.AbstractReceptor;
import io.spine.server.model.MethodParams;
import io.spine.server.model.ParameterSpec;
import io.spine.server.model.VoidMethod;
import io.spine.server.type.EmptyClass;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;

import java.lang.reflect.Method;

/**
 * A method annotated with the {@link io.spine.core.Subscribe @Subscribe} annotation.
 *
 * <p>Such a method may have side effects, but provides no visible output.
 *
 * @see io.spine.core.Subscribe
 */
@Immutable
public abstract class SubscriberMethod
        extends AbstractReceptor<EventSubscriber,
                                 EventMessage,
                                 EventClass,
                                 EventEnvelope,
                                 EmptyClass>
        implements VoidMethod<EventSubscriber, EventClass, EventEnvelope> {



    protected SubscriberMethod(Method method, ParameterSpec<EventEnvelope> parameterSpec) {
        super(method, parameterSpec);
    }

    @Override
    public MethodParams params() {
        return MethodParams.of(this);
    }

    @Override
    public EventClass messageClass() {
        return EventClass.from(rawMessageClass());
    }
}
