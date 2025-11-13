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

package io.spine.server.delivery;

import io.spine.server.delivery.event.CatchUpStarted;
import io.spine.server.event.EventComparator;
import io.spine.type.TypeUrl;

import java.io.Serial;
import java.io.Serializable;
import java.util.Comparator;

/**
 * The comparator which sorts the messages chronologically, but ensures that if there is
 * a {@link CatchUpStarted} event in the sorted batch, it goes on top.
 */
final class CatchUpMessageComparator
        implements Comparator<InboxMessage>, Serializable {

    @Serial
    private static final long serialVersionUID = 0L;
    private static final TypeUrl CATCH_UP_STARTED =
            TypeUrl.from(CatchUpStarted.getDescriptor());

    @Override
    public int compare(InboxMessage m1, InboxMessage m2) {
        if (m1.hasEvent() && m2.hasEvent()) {
            var e1 = m1.getEvent();
            var typeOfFirst = e1.getMessage().getTypeUrl();
            if (typeOfFirst.equals(CATCH_UP_STARTED.toString())) {
                return -1;
            }
            var e2 = m2.getEvent();
            var typeOfSecond = e2.getMessage().getTypeUrl();
            if (typeOfSecond.equals(CATCH_UP_STARTED.toString())) {
                return 1;
            }
            return EventComparator.chronological().compare(e1, e2);
        } else {
            return InboxMessageComparator.chronologically.compare(m1, m2);
        }
    }
}
