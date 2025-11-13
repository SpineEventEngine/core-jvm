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

package io.spine.server.event;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Message;
import com.google.protobuf.Value;
import io.spine.base.Error;
import io.spine.base.EventMessage;
import io.spine.server.MessageError;
import io.spine.type.TypeName;

import java.io.Serial;
import java.util.Map;

/**
 * A base for exceptions related to events.
 */
public abstract class EventException extends RuntimeException implements MessageError {

    @Serial
    private static final long serialVersionUID = 0L;

    public static final String ATTR_EVENT_TYPE_NAME = "eventType";

    /**
     * The event message.
     */
    private final EventMessage eventMessage;

    /**
     * The error passed with the exception.
     */
    private final Error error;

    /**
     * Creates a new instance.
     *
     * @param messageText
     *         the error message text
     * @param eventMessage
     *         the related event message
     * @param error
     *          the error occurred
     */
    protected EventException(String messageText, EventMessage eventMessage, Error error) {
        super(messageText);
        this.eventMessage = eventMessage;
        this.error = error;
    }

    /**
     * Returns a map with an event type attribute.
     *
     * @param eventMessage
     *         an event message to get the type from
     */
    public static Map<String, Value> eventTypeAttribute(Message eventMessage) {
        var type = TypeName.of(eventMessage)
                           .value();
        var value = Value.newBuilder()
                .setStringValue(type)
                .build();
        Map<String, Value> result = ImmutableMap.of(ATTR_EVENT_TYPE_NAME, value);
        return result;
    }

    /**
     * Returns a related event message.
     */
    public EventMessage getEventMessage() {
        return eventMessage;
    }

    @Override
    public Error asError() {
        return error;
    }

    @Override
    public Throwable asThrowable() {
        return this;
    }
}
