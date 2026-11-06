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

package io.spine.server.bus

import io.spine.annotation.Internal
import io.spine.server.type.MessageEnvelope
import io.spine.validation.ValidationError

/**
 * Handles a message that does not pass the validation of a bus.
 *
 * A bus calls the handler before it refuses the message, so that the handler records
 * the violations in a bus-specific way.
 *
 * @param E The type of the envelopes of the handled messages.
 */
@Internal
public fun interface InvalidMessageHandler<E : MessageEnvelope<*, *, *>> {

    /**
     * Handles the invalid message.
     *
     * @param envelope The envelope of the message.
     * @param violations The constraints that the message violates.
     */
    public fun handle(envelope: E, violations: ValidationError)
}
