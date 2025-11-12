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

package io.spine.server.model;

import io.spine.server.model.given.ModelTestEnv.FaultyCommander;
import io.spine.server.model.given.ModelTestEnv.MAggregate;
import io.spine.server.model.given.ModelTestEnv.MCommandAssignee;
import io.spine.server.model.given.ModelTestEnv.MProcessManager;
import io.spine.test.reflect.command.RefCreateProject;
import io.spine.test.reflect.command.RefStartProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tres.quattro.Counter;
import uno.dos.Encounter;

import static io.spine.server.aggregate.model.AggregateClass.asAggregateClass;
import static io.spine.server.command.model.AssigneeClass.asCommandAssigneeClass;
import static io.spine.server.procman.model.ProcessManagerClass.asProcessManagerClass;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests of {@link Model}.
 */
@DisplayName("Model should")
@SuppressWarnings("ErrorNotRethrown")
class ModelTest {

    @BeforeEach
    void setUp() {
        Model.dropAllModels();
    }

    @Test
    @DisplayName("check for duplicated command-handling methods in command assignee class")
    @SuppressWarnings("CheckReturnValue") // returned values are not used in this test
    void checkDuplicateCmdHandler() {
        try {
            asAggregateClass(MAggregate.class);
            asCommandAssigneeClass(MCommandAssignee.class);
            failErrorNotThrown();
        } catch (DuplicateCommandReceptorError error) {
            assertContainsClassName(error, RefCreateProject.class);
            assertContainsClassName(error, MAggregate.class);
            assertContainsClassName(error, MCommandAssignee.class);
        }
    }

    @Test
    @DisplayName("check for duplicated command-handling methods in process manager class")
    @SuppressWarnings("CheckReturnValue") // returned values are not used in this test
    void checkDuplicateInProcman() {
        try {
            asAggregateClass(MAggregate.class);
            asProcessManagerClass(MProcessManager.class);
            failErrorNotThrown();
        } catch (DuplicateCommandReceptorError error) {
            assertContainsClassName(error, RefCreateProject.class);
            assertContainsClassName(error, RefStartProject.class);
            assertContainsClassName(error, MAggregate.class);
            assertContainsClassName(error, MProcessManager.class);
        }
    }

    @Test
    @DisplayName("check for command receiving methods marked as external")
    void checkExternalCommandHandlers() {
        try {
            asProcessManagerClass(FaultyCommander.class);
            fail(ExternalCommandReceiverMethodError.class.getName() + " is expected");
        } catch (ExternalCommandReceiverMethodError error) {
            assertContainsClassName(error, RefCreateProject.class);
        }
    }

    /**
     * Tests that:
     * <ol>
     *   <li>{@code Model} obtains {@code BoundedContextName} specified in a package annotation.
     *   <li>Packages that do not have a common “root”, can be annotated with the same Bounded
     *       Context name.
     * </ol>
     */
    @Test
    @DisplayName("find BoundedContext package annotation")
    void findBoundedContextAnnotation() {
        var ctx1 = Model.findContext(Counter.class);
        var ctx2 = Model.findContext(Encounter.class);
        assertTrue(ctx1.isPresent());
        assertEquals("Counting", ctx1.get()
                                     .getValue());
        assertEquals(ctx1, ctx2);
    }

    private static void assertContainsClassName(ModelError error, Class<?> cls) {
        var errorMessage = error.getMessage();
        assertTrue(errorMessage.contains(cls.getName()));
    }

    private static void failErrorNotThrown() {
        fail(DuplicateCommandReceptorError.class.getName() + " should be thrown");
    }
}
