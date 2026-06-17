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
