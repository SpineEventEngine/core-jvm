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
import io.spine.base.EntityState;
import io.spine.base.EventMessage;
import io.spine.base.Field;
import io.spine.base.FieldPath;
import io.spine.core.BoundedContext;
import io.spine.core.BoundedContextName;
import io.spine.environment.Environment;
import io.spine.environment.Tests;
import io.spine.logging.WithLogging;
import io.spine.server.entity.model.StateClass;
import io.spine.server.model.ArgumentFilter;
import io.spine.server.model.Model;
import io.spine.server.model.ParameterSpec;
import io.spine.server.type.EventEnvelope;
import io.spine.system.server.event.EntityStateChanged;
import io.spine.type.TypeUrl;

import java.lang.reflect.Method;

import static io.spine.core.BoundedContextNames.assumingTests;
import static java.lang.String.format;

/**
 * A handler method which receives an entity state and produces no output.
 */
@Immutable
public final class StateSubscriberMethod extends SubscriberMethod implements WithLogging {

    private static final FieldPath ENTITY_TYPE_URL = Field.parse("entity.type_url").path();

    private final BoundedContextName contextOfSubscriber;
    private final Class<? extends EntityState<?>> stateType;

    StateSubscriberMethod(Method method, ParameterSpec<EventEnvelope> parameterSpec) {
        super(checkNotFiltered(method, "state subscriber"), parameterSpec);
        this.contextOfSubscriber = contextOf(method.getDeclaringClass());
        this.stateType = firstParamType(rawMethod());
        checkExternal();
    }

    private void checkExternal() {
        var originContext = contextOf(stateType());
        var external = !originContext.equals(contextOfSubscriber);
        ensureExternalMatch(external);
    }

    /**
     * Obtains the type of the entity state to which the method is subscribed.
     */
    public Class<? extends EntityState<?>> stateType() {
        return stateType;
    }

    /**
     * Returns the value of {@link #stateType() stateType()} as a {@link StateClass}.
     */
    public StateClass<?> stateClass() {
        return StateClass.of(stateType());
    }

    @Override
    protected ArgumentFilter createFilter() {
        var targetType = TypeUrl.of(stateType);
        return ArgumentFilter.acceptingOnly(ENTITY_TYPE_URL, targetType.value());
    }

    /**
     * Always returns {@link EntityStateChanged}{@code .class}.
     */
    @Override
    protected Class<? extends EventMessage> rawMessageClass() {
        return EntityStateChanged.class;
    }

    @SuppressWarnings("TestOnlyProblems")
    // Checks that the resulting context is not `AssumingTests` in production environment.
    private BoundedContextName contextOf(Class<?> cls) {
        var model = Model.inContextOf(cls);
        var name = model.contextName();
        if (!Environment.instance().is(Tests.class) && name.equals(assumingTests())) {
            logger().atWarning().log(() -> format(
                    "The class `%s` belongs to the Bounded Context named `%s`," +
                    " which is used for testing. As such, it should not be used in production." +
                    " Please see the description of `%s` for instructions on" +
                    " annotating packages with names of Bounded Contexts of your application.",
                    cls.getName(), assumingTests().getValue(), BoundedContext.class.getName()
            ));
        }
        return name;
    }
}
