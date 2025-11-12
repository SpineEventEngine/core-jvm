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

package io.spine.server.command;

import com.google.common.collect.Lists;
import com.google.protobuf.Message;
import io.spine.core.Command;
import io.spine.core.CommandContext;
import io.spine.server.type.CommandEnvelope;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test utility for keeping the history of command messages and their contexts
 * handled by an aggregate.
 */
public final class CommandHistory {

    private final List<Message> messages = Lists.newArrayList();
    private final List<CommandContext> contexts = Lists.newArrayList();

    public void add(Message message, CommandContext context) {
        messages.add(message);
        contexts.add(context);
    }

    public void add(CommandEnvelope e) {
        add(e.message(), e.context());
    }

    public boolean contains(Command command) {
        Message message = command.enclosedMessage();

        if (messages.contains(message)) {
            var messageIndex = messages.indexOf(message);
            var actualContext = command.context();
            var storedContext = contexts.get(messageIndex);
            return actualContext.equals(storedContext);
        }

        return false;
    }

    public boolean contains(Class<? extends Message> commandClass) {
        return messages.stream()
                       .anyMatch( (m) -> commandClass.equals(m.getClass()));
    }

    public void clear() {
        messages.clear();
        contexts.clear();
    }

    public void assertHandled(Command expected) {
        var cmdName = expected.enclosedMessage()
                              .getClass()
                              .getName();
        assertTrue(contains(expected), "Expected but wasn't handled, command: " + cmdName);
    }
}
