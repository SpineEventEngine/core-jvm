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

package io.spine.server.delivery;

import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import io.spine.base.Time;

/**
 * The station which removes the delivered messages, unless they are
 * {@linkplain InboxMessage#getKeepUntil() set} to be kept for longer.
 */
final class CleanupStation extends Station {

    private static final DeliveryErrors NO_ERRORS = DeliveryErrors.newBuilder()
                                                                  .build();
    private static final Result NOTHING_DELIVERED = new Result(0, NO_ERRORS);

    /**
     * Looks up through the passed conveyor in order to find the messages which may already be
     * removed.
     *
     * <p>Always reports that no messages were delivered and, therefore, no delivery errors were
     * observed.
     */
    @Override
    public Result process(Conveyor conveyor) {
        for (var message : conveyor) {
            if (message.getStatus() == InboxMessageStatus.DELIVERED) {
                var keepUntil = message.getKeepUntil();
                if (keepUntil.equals(Timestamp.getDefaultInstance()) || isInPast(keepUntil)) {
                    conveyor.remove(message);
                }
            }
        }
        return NOTHING_DELIVERED;
    }

    private static boolean isInPast(Timestamp keepUntil) {
        return Timestamps.compare(Time.currentTime(), keepUntil) > 0;
    }
}
