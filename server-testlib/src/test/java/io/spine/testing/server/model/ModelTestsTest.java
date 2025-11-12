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

package io.spine.testing.server.model;

import io.spine.server.model.Model;
import io.spine.testing.UtilityClassTest;
import io.spine.testing.server.model.given.ModelTestsTestEnv.DuplicatedCommandAssignee;
import io.spine.testing.server.model.given.ModelTestsTestEnv.TestCommandAssignee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.spine.server.command.model.AssigneeClass.asCommandAssigneeClass;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`ModelTests` utility should")
class ModelTestsTest extends UtilityClassTest<ModelTests> {

    ModelTestsTest() {
        super(ModelTests.class);
    }

    @BeforeEach
    void setUp() {
        // The model should not be polluted by the previously executed tests.
        ModelTests.dropAllModels();
    }

    @Test
    @DisplayName("clear all models")
    void clearModel() {
        // This registers a command-handling method for `TuRemoveProject`.
        var cls1 = asCommandAssigneeClass(TestCommandAssignee.class);
        assertNotNull(cls1);

        ModelTests.dropAllModels();

        // This should pass as we cleared the model,
        // i.e. there is no registered command-handling method for `TuRemoveProject`.
        var cls2 = asCommandAssigneeClass(DuplicatedCommandAssignee.class);
        assertNotNull(cls2);
        assertNotEquals(cls1, cls2);
    }

    @Test
    @DisplayName("be the only place for clearing models")
    void theSoleCleaner() {
        assertThrows(SecurityException.class, Model::dropAllModels);
    }
}
