/*
 * Copyright 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.server.event

import io.spine.base.EventMessage
import io.spine.server.bus.MessageDispatcher
import io.spine.string.joinBackticked

/**
 * An interface common to classes that produce zero or more messages in response
 * to an incoming event.
 *
 * The interface does not define functions to allow `protected abstract`
 * methods in the implementing abstract classes to avoid overexposure of
 * the message processing.
 *
 * @param E the type of the event to be processed.
 */
public interface Whenever<E : EventMessage> : EventReceiver

context(dispatcher: MessageDispatcher<*, *>)
internal fun Whenever<*>.checkAcceptsOneEvent() {
    val events = if (dispatcher is EventDispatcherDelegate) {
        dispatcher.events()
    } else {
        dispatcher.messageClasses()
    }
    check(events.size == 1) {
        "The class `${this::class.qualifiedName}` should accept exactly one event." +
                " Now it handles too many (${events.size}): [${events.joinBackticked()}]." +
                " Please use only `whenever()` method for producing outgoing messages."
    }
}
