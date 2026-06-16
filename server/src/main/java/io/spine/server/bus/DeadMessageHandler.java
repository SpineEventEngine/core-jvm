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

package io.spine.server.bus;

import io.spine.annotation.Internal;
import io.spine.server.type.MessageEnvelope;

/**
 * A definition of a handler for a dead message.
 *
 * <p> If no target dispatchers are found for a message passed to the bus, it will result in a call
 * to {@link DeadMessageHandler#handle DeadMessageHandler.handle(MessageEnvelope)}. The method
 * produces {@link MessageUnhandled} instance describing the dead message. It may also process the
 * given message (e.g. store it into the bus store).
 *
 * @param <E>
 *         the type of the envelopes used to pack the dead message into
 */
@Internal
public interface DeadMessageHandler<E extends MessageEnvelope<?, ?, ?>> {

    /**
     * Handles the dead message in a bus-specific way and produces an {@link MessageUnhandled} 
     * that may be converted to a {@link Error} for notifying the poster about the absence of
     * dispatchers.
     *
     * @param message the dead message
     * @return the {@link MessageUnhandled} instance describing the dead message
     */
    MessageUnhandled handle(E message);
}
