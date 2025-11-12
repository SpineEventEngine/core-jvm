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

package io.spine.server.command.model.given.handler;

import com.google.common.collect.ImmutableList;
import io.spine.base.CommandMessage;
import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.command.AbstractAssignee;
import io.spine.testing.server.model.ModelTests;

import java.lang.reflect.Method;
import java.util.List;

import static com.google.common.collect.Lists.newLinkedList;

/**
 * Abstract base for test environment command assignees.
 *
 * <p>Derived classes must declare a method named {@linkplain #ASSIGNEE_METHOD_NAME handleTest}
 * so that the method can be {@linkplain #method() obtained} by the code of tests.
 */
public abstract class TestCommandAssignee extends AbstractAssignee {

    private static final String ASSIGNEE_METHOD_NAME = "handleTest";

    private final List<CommandMessage> handledCommands = newLinkedList();

    protected TestCommandAssignee() {
        super();
        var context = BoundedContextBuilder.assumingTests(true).build();
        registerWith(context);
    }

    @Override
    public final void registerWith(BoundedContext context) {
        super.registerWith(context);
    }

    public final Method method() {
        return ModelTests.getMethod(getClass(), ASSIGNEE_METHOD_NAME);
    }

    protected final void addHandledCommand(CommandMessage cmd) {
        handledCommands.add(cmd);
    }

    public final ImmutableList<CommandMessage> handledCommands() {
        return ImmutableList.copyOf(handledCommands);
    }
}
