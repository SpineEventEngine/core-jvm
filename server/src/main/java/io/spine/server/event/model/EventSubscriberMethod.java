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
import io.spine.server.event.EventSubscriber;
import io.spine.server.model.ParameterSpec;
import io.spine.server.type.EventEnvelope;

import java.lang.reflect.Method;

/**
 * A wrapper for an event subscriber method.
 */
@Immutable
public final class EventSubscriberMethod extends SubscriberMethod
        implements RejectionHandler<EventSubscriber> {

    /** Creates a new instance. */
    EventSubscriberMethod(Method method, ParameterSpec<EventEnvelope> parameterSpec) {
        super(method, parameterSpec);
    }

    @Override
    public EventAcceptingMethodParams parameterSpec() {
        return (EventAcceptingMethodParams) super.parameterSpec();
    }

    @Override
    protected void checkAttributesMatch(EventEnvelope event) throws IllegalArgumentException {
        super.checkAttributesMatch(event);
        ensureExternalMatch(event.isExternal());
    }
}
