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

import io.spine.server.event.EventReceiver;
import io.spine.server.type.EventEnvelope;

import java.util.Optional;

/**
 * The helper class for holding messaging information on behalf of another model class.
 *
 * @param <T>
 *         the type of the raw class for obtaining messaging information
 */
public final class ReactorClassDelegate<T extends EventReceiver>
        extends EventReceivingClassDelegate<T, EventReactorMethod>
        implements ReactingClass {

    public ReactorClassDelegate(Class<T> cls) {
        super(cls, new EventReactorSignature());
    }

    @Override
    public Optional<EventReactorMethod> reactorOf(EventEnvelope event) {
        return findReceptorOf(event);
    }
}
