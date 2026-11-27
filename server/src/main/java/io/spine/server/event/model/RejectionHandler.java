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
import io.spine.server.model.DispatchKey;
import io.spine.server.model.Receptor;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;

/**
 * A handler method that may handle rejections.
 *
 * @param <T>
 *         the type of the object to which the handler method belongs
 */
@Immutable
interface RejectionHandler<T>
        extends Receptor<T, EventClass, EventEnvelope> {

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
