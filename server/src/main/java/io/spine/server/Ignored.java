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

package io.spine.server;

import io.spine.annotation.Internal;
import io.spine.core.Where;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.dispatch.Ignore;
import io.spine.server.model.ModelClass;
import io.spine.server.type.EventEnvelope;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

/**
 * A factory of {@code DispatchOutcome} with the {@code ignored} outcome.
 */
@Internal
public final class Ignored {

    /**
     * Prevents the utility class instantiation.
     */
    private Ignored() {
    }

    /**
     * Creates a new {@code ignored} outcome.
     *
     * <p>The ignoring reason points towards the {@linkplain Where argument filters}.
     *
     * @param handler
     *         the class of the event handler
     * @param event
     *         the dispatched event
     * @return {@code ignored} outcome
     */
    public static DispatchOutcome ignored(ModelClass<?> handler, EventEnvelope event) {
        checkNotNull(handler);
        checkNotNull(event);
        var reason = format(
                "`@%s` filters in `%s` rejected event %s[%s]",
                Where.class.getSimpleName(), handler, event.messageTypeName(), event.id().value()
        );
        return DispatchOutcome
                .newBuilder()
                .setPropagatedSignal(event.messageId())
                .setIgnored(Ignore.newBuilder().setReason(reason))
                .build();
    }
}
