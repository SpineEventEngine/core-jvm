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

package io.spine.server;

import com.google.protobuf.Any;
import io.spine.core.Version;
import io.spine.server.event.NoReaction;
import io.spine.server.model.Nothing;

/**
 * An object with identity that produces events.
 */
public interface EventProducer {

    /**
     * The object identity packed into {@link Any}.
     */
    Any producerId();

    /**
     * The version of the object to be put into events.
     *
     * <p>If {@linkplain Version#getDefaultInstance() empty}, no version will be added to
     * the generated events.
     */
    Version version();

    /**
     * Obtains the {@link Nothing} event message.
     *
     * <p>This event should be returned if there is no value for the domain to produce an actual
     * event. Note that a {@link Nothing} event is never actually posted into
     * the {@link io.spine.server.event.EventBus EventBus}.
     *
     * @return the default instance of {@link Nothing}
     * @deprecated please use {@link #noReaction()} instead.
     */
    @Deprecated
    default Nothing nothing() {
        return Nothing.getDefaultInstance();
    }

    /**
     * Obtains the {@link NoReaction} event message.
     *
     * <p>This event should be returned if there is no value for the domain to produce an actual
     * event. Note that a {@link NoReaction} event is never actually posted into
     * the {@link io.spine.server.event.EventBus EventBus}.
     *
     * @return the default instance of {@link NoReaction}
     */
    default NoReaction noReaction() {
        return NoReaction.getDefaultInstance();
    }
}
