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

import io.spine.server.model.AllowedParams;
import io.spine.server.model.Receptor;
import io.spine.server.model.ReceptorSignature;
import io.spine.server.type.EventEnvelope;

import java.lang.annotation.Annotation;

/**
 * An abstract base of signatures for methods that accept {@code Event}s.
 *
 * @param <H> the type of {@link Receptor} whose signature this belongs to
 */
abstract class EventAcceptingSignature<H extends Receptor<?, ?, EventEnvelope>>
        extends ReceptorSignature<H, EventEnvelope> {

    EventAcceptingSignature(Class<? extends Annotation> annotation) {
        super(annotation);
    }

    @Override
    public AllowedParams<EventEnvelope> params() {
        return EventAcceptingMethodParams.allowed();
    }
}
