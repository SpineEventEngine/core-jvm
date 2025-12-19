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

import com.google.protobuf.Message;
import com.google.protobuf.Value;
import io.spine.base.Error;
import io.spine.base.EventMessage;
import io.spine.core.EventValidationError;
import io.spine.server.MessageInvalid;
import io.spine.server.type.EventClass;
import io.spine.validation.ConstraintViolation;
import io.spine.validation.ExceptionFactory;

import java.io.Serial;
import java.util.Map;

/**
 * The exception for reporting invalid events.
 *
 * <p>An event is invalid if it's supported (there's a handler for the event), but its
 * attributes are not populated according to framework conventions or validation constraints.
 */
public class InvalidEventException extends EventException implements MessageInvalid {

    private static final long serialVersionUID = 0L;

    private static final String MSG_VALIDATION_ERROR = "Event message does not match " +
                                                       "the validation constraints.";

    private InvalidEventException(String messageText, EventMessage eventMsg, Error error) {
        super(messageText, eventMsg, error);
    }

    /**
     * Creates an exception instance for a event message which has fields that
     * violate validation constraint(s).
     *
     * @param eventMsg   the invalid event message
     * @param violations the constraint violations for the event message
     */
    public static
    InvalidEventException onConstraintViolations(EventMessage eventMsg,
                                                 Iterable<ConstraintViolation> violations) {
        var factory = new Factory(eventMsg, violations);
        return factory.newException();
    }

    /**
     * A helper utility aimed to create an {@code InvalidEventException} to report the
     * event which field values violate validation constraint(s).
     */
    private static final class Factory
            extends ExceptionFactory<InvalidEventException,
                                     EventMessage,
                                     EventClass,
                                     EventValidationError> {

        private final EventClass eventClass;

        private Factory(EventMessage eventMsg, Iterable<ConstraintViolation> violations) {
            super(eventMsg, violations);
            this.eventClass = EventClass.of(eventMsg);
        }

        @Override
        protected EventClass getMessageClass() {
            return eventClass;
        }

        @Override
        protected EventValidationError getErrorCode() {
            return EventValidationError.INVALID_EVENT;
        }

        @Override
        protected String getErrorText() {
            return MSG_VALIDATION_ERROR;
        }

        @Override
        protected Map<String, Value> getMessageTypeAttribute(Message message) {
            return eventTypeAttribute(message);
        }

        @Override
        protected InvalidEventException createException(String exceptionMsg,
                                                        EventMessage event,
                                                        Error error) {
            return new InvalidEventException(exceptionMsg, event, error);
        }
    }
}
