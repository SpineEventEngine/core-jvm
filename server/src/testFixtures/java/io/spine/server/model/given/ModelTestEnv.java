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

package io.spine.server.model.given;

import io.spine.core.External;
import io.spine.server.command.Command;
import io.spine.server.procman.ProcessManager;
import io.spine.server.test.shared.EmptyProcess;
import io.spine.test.reflect.command.RefCreateProject;
import io.spine.test.reflect.command.RefStartProject;

/**
 * Test environment for {@linkplain io.spine.server.model.ModelTest Model tests}.
 */
public class ModelTestEnv {

    /** Prevents instantiation of this utility class. */
    private ModelTestEnv() {
    }

    /**
     * A commander that declares an {@code external} command substitution method, which is
     * forbidden by the {@linkplain io.spine.server.command.model.CommanderClass model}.
     */
    public static class FaultyCommander
            extends ProcessManager<String, EmptyProcess, EmptyProcess.Builder> {

        private FaultyCommander(String id) {
            super(id);
        }

        @Command
        RefStartProject handle(@External RefCreateProject cmd) {
            return RefStartProject.getDefaultInstance();
        }
    }
}
