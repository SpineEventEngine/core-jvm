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

package io.spine.server.commandbus;

import io.spine.base.RejectionThrowable;
import io.spine.core.Ack;
import io.spine.server.bus.BusFilter;
import io.spine.server.bus.MessageIdExtensions;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.MessageEnvelope;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Filters commands posted to {@link CommandBus}.
 */
public interface CommandFilter extends BusFilter<CommandEnvelope> {

    /**
     * Rejects the message with a {@linkplain io.spine.base.RejectionMessage rejection} status.
     *
     * <p>This method is a shortcut that can be used in {@link #filter(MessageEnvelope)} when the
     * message does not pass the filter due to a business rejection.
     *
     * <p>Such rejection method can be used when no technical error occurs but due to the business
     * rules the command should be immediately disqualified from being executed. A typical scenario
     * would be when the permissions of the user who made the request aren't broad enough.
     *
     * <p>The acknowledgement of the command carries the rejection event in
     * {@code Status.rejection}. The rejection is also posted to the {@code EventBus}, so it
     * reaches the subscribers to the rejection, including the clients that subscribed to it
     * before posting the command.
     *
     * @param command
     *          the envelope with the message to filter
     * @param cause
     *         the cause of the rejection
     * @return the {@code Optional.of(Ack)} signaling that the message does not pass the filter
     */
    default Optional<Ack> reject(CommandEnvelope command, RejectionThrowable cause) {
        checkNotNull(command);
        checkNotNull(cause);
        var cmd = command.command();
        var ack = MessageIdExtensions.reject(cmd, cause);
        return Optional.of(ack);
    }
}
