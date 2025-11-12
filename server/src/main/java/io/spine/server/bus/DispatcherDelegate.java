/*
 * Copyright 2023, TeamDev. All rights reserved.
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

import io.spine.server.type.MessageEnvelope;
import io.spine.type.MessageClass;

/**
 * A delegate of a {@link DelegatingDispatcher}.
 *
 * <p>This is a common interface for objects which need to dispatch messages of
 * one kind (e.g., events), but are unable to implement {@link MessageDispatcher} because
 * they already implement this interface with generic parameters for another
 * kind of messages (e.g., commands).
 *
 * @param <C>
 *         the type of the dispatched message class
 * @param <E>
 *         the type of the message envelope
 * @apiNote
 *      This interface does not extend {@link MessageDispatcher} in order to allow implementing
 *      "native" dispatching interface and this delegation interface in one class.
 *
 *      <p>The generic parameters are the same as the ones of {@link MessageDispatcher} and
 *      a {@link DelegatingDispatcher} to which this delegate belongs.
 *      Even though they are not used in this interface directly,
 *      they bring semantic meaning to the interface and code clarity at the usage sites.
 * @see DelegatingDispatcher
 */
@SuppressWarnings("unused") // see the API note above.
public interface DispatcherDelegate<C extends MessageClass<?>, E extends MessageEnvelope<?, ?, ?>> {
}
