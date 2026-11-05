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
import io.spine.base.Error
import io.spine.core.Ack
import io.spine.core.MessageId
import io.spine.core.Responses.errorWith
import io.spine.core.Responses.statusOk
import io.spine.core.SignalId
import io.spine.core.Status
import io.spine.core.ack
import io.spine.protobuf.pack

/**
 * Acknowledges the message with this ID (e.g. [MessageId] or [SignalId]) with the OK status.
 *
 * @receiver the ID of the acknowledged message
 */
public fun Message.acknowledge(): Ack = ackWithStatus(statusOk())

/**
 * Rejects a message with this ID (e.g. [MessageId] or [SignalId]) because the passed
 * error occurred.
 *
 * @param cause the error that prevented the message from being handled
 * @receiver the ID of the message that caused the error
 */
public fun Message.causedError(cause: Error): Ack = ackWithStatus(errorWith(cause))

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
