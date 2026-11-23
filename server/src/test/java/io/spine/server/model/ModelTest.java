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

package io.spine.server.model;

import io.spine.server.model.given.ModelTestEnv.FaultyCommander;
import io.spine.test.reflect.command.RefCreateProject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tres.quattro.Counter;
import uno.dos.Encounter;

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
}
