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

@file:JvmName("MessageIdExtensions")

package io.spine.server.bus

import com.google.protobuf.Message
import io.spine.annotation.VisibleForTesting
import io.spine.base.Error
import io.spine.base.RejectionThrowable
import io.spine.core.Ack
import io.spine.core.Command
import io.spine.core.CommandId
import io.spine.core.Event
import io.spine.core.MessageId
import io.spine.core.Responses.errorWith
import io.spine.core.Responses.rejectedBecauseOf
import io.spine.core.Responses.statusOk
import io.spine.core.SignalId
import io.spine.core.Status
import io.spine.core.ack
import io.spine.protobuf.pack
import io.spine.server.event.reject

/**
 * Acknowledges the message with this ID (e.g. [MessageId] or [SignalId]) with the OK status.
 *
 * @receiver the ID of the acknowledged message,
 */
public fun Message.acknowledge(): Ack = ackWithStatus(statusOk())

/**
 * Rejects message with this ID (e.g. [MessageId] or [SignalId]) because the passed error occurred.
 *
 * @param cause the error which prevented the message from being handled
 * @receiver the ID of the message which caused the error
 */
@VisibleForTesting
public fun Message.causedError(cause: Error): Ack = ackWithStatus(errorWith(cause))

/**
 * Reject a command with this ID with the passed rejection event.
 *
 * @receiver the ID of the rejected command
 */
public fun CommandId.reject(rejection: Event): Ack = ackWithStatus(rejectedBecauseOf(rejection))

/**
 * Creates a rejection acknowledgement with the passed cause.
 *
 * @receiver the rejected command
 */
public fun Command.reject(cause: RejectionThrowable): Ack {
    val rejection = reject(this, cause)
    val commandId = id()
    return commandId.reject(rejection)
}

/**
 * Creates an acknowledgement with the passed status.
 *
 * @receiver the ID of the message being processed
 */
private fun Message.ackWithStatus(status: Status): Ack {
    return ack {
        messageId = this@ackWithStatus.pack()
        this.status = status
    }
}
