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

package io.spine.server.event;

import com.google.protobuf.Any;
import io.spine.server.type.CommandEnvelope;
import io.spine.test.command.event.MandatoryFieldEvent;
import io.spine.testing.TestValues;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.validation.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.spine.base.Identifier.pack;
import static io.spine.testing.TestValues.newUuidValue;
import static io.spine.testing.TestValues.nullRef;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`EventFactory` should")
class EventFactoryTest {

    private final TestActorRequestFactory requestFactory = new TestActorRequestFactory(getClass());

    private Any producerId;
    private CommandEnvelope origin;

    @BeforeEach
    void setUp() {
        producerId = pack(newUuidValue());
        origin = CommandEnvelope.of(requestFactory.generateCommand());
    }

    @Test
    @DisplayName("require producer ID")
    void requireProducerId() {
        assertThrows(NullPointerException.class, () -> EventFactory.on(origin, nullRef()));
    }

    @Test
    @DisplayName("require origin")
    void requireOrigin() {
        assertThrows(NullPointerException.class,
                     () -> EventFactory.on(TestValues.<CommandEnvelope>nullRef(), producerId));
    }

    @Test
    @DisplayName("validate event messages before creation")
    void validateCreatedMessages() {
        var factory = EventFactory.on(origin, producerId);
        assertThrows(ValidationException.class,
                     () -> factory.createEvent(MandatoryFieldEvent.getDefaultInstance(), null));
    }
}
