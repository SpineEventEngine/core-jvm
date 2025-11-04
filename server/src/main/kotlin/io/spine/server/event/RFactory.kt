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

@file:JvmName("RejectionFactory")

package io.spine.server.event

import com.google.common.base.Throwables
import com.google.protobuf.Any
import io.spine.base.Identifier
import io.spine.base.RejectionThrowable
import io.spine.core.Command
import io.spine.core.Event
import io.spine.core.EventContext
import io.spine.core.rejectionEventContext

/**
 * A placeholder to be used when a producer of a rejection is not available.
 */
private val unknownProducer: Any = Identifier.pack("Unknown")

/**
 * Creates a rejection event for the passed command and the throwable.
 *
 * If the ID of the actor generating the rejection was not [set][RejectionThrowable.initProducer],
 * in the rejection, a placeholder with the string `"Unknown"` will be used as the producer ID.
 *
 * @param command The command to be rejected.
 * @param throwable The reason for the command to be rejected, must implement [RejectionThrowable],
 *   or have its cause implementing this interface.
 * @throws IllegalArgumentException if neither the passed throwable nor its cause
 *   implement [RejectionThrowable].
 */
public fun reject(command: Command, throwable: Throwable): Event {
    val rt = unwrap(throwable)
    val factory = RFactory(command, rt)
    return factory.createRejection()
}

/**
 * Extracts a `RejectionThrowable` from the passed instance.
 */
private fun unwrap(throwable: Throwable): RejectionThrowable {
    if (throwable is RejectionThrowable) {
        return throwable
    }
    val cause = Throwables.getRootCause(throwable)
    require(cause is RejectionThrowable) {
        "The cause of `${throwable}` has the type `${cause.javaClass}`." +
                " Expected: `${RejectionThrowable::class}`."
    }
    return cause
}

/**
 * The factory for producing rejection events.
 */
private class RFactory(val command: Command, val throwable: RejectionThrowable) :
    EventFactoryBase(
        EventOrigin.from(command.asMessageOrigin()),
        throwable.producerId().orElse(unknownProducer)
    ) {

    /**
     * Creates a rejection event which does not have version information.
     */
    fun createRejection(): Event {
        val msg = throwable.messageThrown()
        val ctx = createContext()
        return assemble(msg, ctx)
    }

    /**
     * Creates an event context holding a rejection context.
     */
    private fun createContext(): EventContext =
        newContext(null).apply {
            rejection = rejectionEventContext {
                command = this@RFactory.command
                stacktrace = throwable.stackTraceToString()
            }
        }.build()
}
