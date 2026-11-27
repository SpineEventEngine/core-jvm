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

import io.spine.server.type.EventEnvelope;

import java.util.Optional;

/**
 * Provides message handling information on a class that reacts on messages.
 */
public interface ReactingClass extends EventReceiverClass {

    /**
     * Obtains the method that reacts on the events of the passed class.
     *
     * @param event
     *         the event on which the method reacts
     */
    Optional<EventReactorMethod> reactorOf(EventEnvelope event);
}
