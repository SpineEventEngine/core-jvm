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

package io.spine.server.aggregate;

import com.google.protobuf.Message;
import io.spine.base.Error;
import io.spine.server.bus.MessageUnhandled;
import io.spine.server.type.EventEnvelope;

import java.io.Serial;

import static io.spine.core.EventValidationError.UNSUPPORTED_EVENT_VALUE;
import static io.spine.server.event.EventException.eventTypeAttribute;
import static java.lang.String.format;

/**
 * Thrown when there are no aggregates that accept an event message for
 * {@linkplain io.spine.server.aggregate.Apply#allowImport() import}.
 */
public final class UnsupportedImportEventException
        extends RuntimeException
        implements MessageUnhandled {

    @Serial
    private static final long serialVersionUID = 0L;
    private final Error error;

    UnsupportedImportEventException(EventEnvelope event) {
        super(messageFormat(event));
        this.error = unsupportedImportEvent(event.message(), getMessage());
    }

    private static String messageFormat(EventEnvelope event) {
        var eventClass = event.messageClass();
        var typeName = eventClass.typeName();
        var result = format(
            "None of the aggregates declare importing appliers for " +
                    "the event of the class: `%s` (proto type: `%s`).",
            eventClass, typeName
        );
        return result;
    }

    @Override
    public Error asError() {
        return error;
    }

    @Override
    public Throwable asThrowable() {
        return this;
    }

    private static Error unsupportedImportEvent(Message eventMessage, String errorMessage) {
        var result = Error.newBuilder()
                .setType(UnsupportedImportEventException.class.getName())
                .setCode(UNSUPPORTED_EVENT_VALUE)
                .setMessage(errorMessage)
                .putAllAttributes(eventTypeAttribute(eventMessage))
                .build();
        return result;
    }
}
