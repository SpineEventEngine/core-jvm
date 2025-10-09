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

package io.spine.testing.server;

import com.google.protobuf.Message;
import io.spine.base.CommandMessage;
import io.spine.base.EventMessage;
import io.spine.server.type.CommandClass;
import io.spine.server.type.EventClass;
import io.spine.type.MessageClass;

import java.util.Collection;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Common assertions for server-side testing.
 */
public final class Assertions {

    /** Prevents instantiation of this utility class. */
    private Assertions() {
    }

    /**
     * Asserts that each of the expected command class is available in the given collection.
     */
    @SafeVarargs
    public static void assertCommandClasses(Collection<CommandClass> commandClasses,
                                            Class<? extends CommandMessage>... expected) {
        assertContains(commandClasses, CommandClass::from, expected);
    }

    /**
     * Asserts that each of the expected event class is available in the given collection.
     */
    @SafeVarargs
    public static void assertEventClasses(Collection<EventClass> eventClasses,
                                          Class<? extends EventMessage>... expected) {
        assertContains(eventClasses, EventClass::from, expected);
    }

    @SafeVarargs
    private static <C extends MessageClass<?>, M extends Message>
    void assertContains(Collection<C> collection,
                        Function<Class<M>, C> func,
                        Class<? extends M>...classes) {
        checkNotNull(collection);
        checkNotNull(classes);
        for (var cls : classes) {
            assertNotNull(cls);
            @SuppressWarnings("unchecked") // OK for tests.
            var messageType = (Class<M>) cls;
            var messageClass = func.apply(messageType);
            assertTrue(collection.contains(messageClass));
        }
    }

    /**
     * Asserts that passed event classes are exactly as expected.
     *
     * <p>The order is not taken into account.
     */
    @SafeVarargs
    public static void assertEventClassesExactly(Iterable<EventClass> eventClasses,
                                                 Class<? extends EventMessage>... expected) {
        assertThat(eventClasses).containsExactlyElementsIn(EventClass.setOf(expected));
    }

    /**
     * Asserts that passed command classes are exactly as expected.
     *
     * <p>The order is not taken into account.
     */
    @SafeVarargs
    public static void assertCommandClassesExactly(Iterable<CommandClass> commandClasses,
                                                   Class<? extends CommandMessage>... expected) {
        assertThat(commandClasses).containsExactlyElementsIn(CommandClass.setOf(expected));
    }
}
