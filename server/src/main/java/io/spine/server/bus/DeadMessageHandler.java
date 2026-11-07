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

package io.spine.server.bus;

import io.spine.annotation.Internal;
import io.spine.server.type.MessageEnvelope;

/**
 * A definition of a handler for a dead message.
 *
 * <p>If no target dispatchers are found for a message passed to the bus, it will result in a call
 * to {@link DeadMessageHandler#handle DeadMessageHandler.handle(MessageEnvelope)}, before the bus
 * refuses the message. The handler processes the message in a bus-specific way, for example,
 * by storing it into the bus store or by recording the problem.
 *
 * @param <E>
 *         the type of the envelopes used to pack the dead message into
 */
@Internal
public interface DeadMessageHandler<E extends MessageEnvelope<?, ?, ?>> {

    /**
     * Handles the dead message in a bus-specific way.
     *
     * @param message the dead message
     */
    void handle(E message);
}
