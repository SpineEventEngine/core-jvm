/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
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
        var context = BoundedContextBuilder.assumingTests(/* multitenant = */ true).build();
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
