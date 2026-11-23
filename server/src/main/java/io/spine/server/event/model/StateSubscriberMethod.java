/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.server.event.model;

import com.google.errorprone.annotations.Immutable;
import io.spine.base.EntityState;
import io.spine.base.EventMessage;
import io.spine.base.Field;
import io.spine.base.FieldPath;
import io.spine.core.BoundedContext;
import io.spine.core.BoundedContextName;
import io.spine.core.BoundedContextNames;
import io.spine.environment.Environment;
import io.spine.environment.Tests;
import io.spine.logging.WithLogging;
import io.spine.server.entity.model.StateClass;
import io.spine.server.model.ArgumentFilter;
import io.spine.server.model.ParameterSpec;
import io.spine.server.type.EventEnvelope;
import io.spine.system.server.event.EntityStateChanged;
import io.spine.type.TypeUrl;

import java.lang.reflect.Method;

import static io.spine.core.BoundedContextNames.assumingTests;
import static java.lang.String.format;

/**
 * A handler method that receives an entity state and produces no output.
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
        var name = BoundedContextNames.contextOf(cls);
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
