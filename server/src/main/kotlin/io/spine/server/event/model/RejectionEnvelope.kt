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
package io.spine.server.event.model

import io.spine.base.CommandMessage
import io.spine.base.RejectionMessage
import io.spine.core.Command
import io.spine.core.CommandContext
import io.spine.core.Event
import io.spine.core.EventContext
import io.spine.core.EventId
import io.spine.server.type.EventClass
import io.spine.server.type.EventEnvelope
import io.spine.server.type.SignalEnvelope

/**
 * The holder of a rejection `Event` that provides convenient access to its properties.
 */
internal class RejectionEnvelope(delegate: EventEnvelope) :
    SignalEnvelope<EventId, Event, EventContext> by delegate {

    private val delegate: EventEnvelope

    /** Ensures the passed delegate contains a rejection. */
    init {
        require(delegate.isRejection) { "`EventEnvelope` must contain a rejection." }
        this.delegate = delegate
    }

    override fun message(): RejectionMessage = delegate.message() as RejectionMessage

    /** Obtains the rejection message. */
    fun rejectionMessage(): RejectionMessage = message()

    override fun messageClass(): EventClass {
        val eventClass = delegate.messageClass()

        @Suppress("UNCHECKED_CAST") // Ensured by the type of delegate.
        val value = eventClass.value() as Class<out RejectionMessage>
        return EventClass.from(value)
    }

    /** Obtains the command that caused the rejection.  */
    private fun command(): Command = context().rejection.command

    /** Obtains the message of the command that cased the rejection.  */
    fun commandMessage(): CommandMessage = command().enclosedMessage()

    /** Obtains the context of the rejected command.  */
    fun commandContext(): CommandContext = command().context()
}
