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

@file:JvmName("MessageIdExtensions")

package io.spine.server.bus

import com.google.protobuf.Message
import io.spine.annotation.VisibleForTesting
import io.spine.base.Error
import io.spine.base.RejectionThrowable
import io.spine.core.Ack
import io.spine.core.Command
import io.spine.core.CommandId
import io.spine.core.ErrorCase
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
 * @receiver the ID of the acknowledged message
 */
public fun Message.acknowledge(): Ack = ackWithStatus(statusOk())

/**
 * Rejects a message with this ID (e.g. [MessageId] or [SignalId]) because of an error
 * of the passed kind.
 *
 * @param errorCase the kind of the error that prevented the message from being handled
 * @receiver the ID of the message that caused the error
 */
public fun Message.causedError(errorCase: ErrorCase): Ack = ackWithStatus(errorWith(errorCase))

/**
 * Rejects a message with this ID (e.g. [MessageId] or [SignalId]) because the passed
 * error occurred.
 *
 * A bus replaces such an acknowledgement given by a filter with the one carrying only
 * the [ErrorCase] of the error.
 *
 * @param cause the error that prevented the message from being handled
 * @receiver the ID of the message that caused the error
 */
@Deprecated(
    "Clients receive only the kind of the error. Please use `causedError(ErrorCase)`.",
    ReplaceWith("causedError(errorCase)")
)
@VisibleForTesting
public fun Message.causedError(cause: Error): Ack =
    // Reason: `Status.error` stays the carrier between a filter and its bus until it is removed.
    @Suppress("DEPRECATION")
    ackWithStatus(errorWith(cause))

/**
 * Rejects a command with this ID with the passed rejection event.
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
