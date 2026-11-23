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

package io.spine.server.command.model;

import io.spine.model.contexts.projects.command.SigAddTaskToProject;
import io.spine.model.contexts.projects.command.SigAssignTask;
import io.spine.model.contexts.projects.command.SigCreateProject;
import io.spine.model.contexts.projects.command.SigCreateTask;
import io.spine.model.contexts.projects.command.SigPauseTask;
import io.spine.model.contexts.projects.command.SigRemoveTaskFromProject;
import io.spine.model.contexts.projects.command.SigSetProjectOwner;
import io.spine.model.contexts.projects.command.SigStartTask;
import io.spine.model.contexts.projects.command.SigStopTask;
import io.spine.model.contexts.projects.event.SigProjectCreated;
import io.spine.model.contexts.projects.event.SigProjectStopped;
import io.spine.model.contexts.projects.event.SigTaskDeleted;
import io.spine.model.contexts.projects.event.SigTaskMoved;
import io.spine.model.contexts.projects.rejection.ProjectRejections;
import io.spine.server.command.model.given.commander.SampleCommander;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.spine.server.command.model.CommanderClass.asCommanderClass;
import static io.spine.testing.server.Assertions.assertCommandClassesExactly;
import static io.spine.testing.server.Assertions.assertEventClassesExactly;

@DisplayName("`CommanderClass` should")
class CommanderClassTest {

    private CommanderClass<?> commanderClass;

    @BeforeEach
    void prepareCommander() {
        commanderClass = asCommanderClass(SampleCommander.class);
    }

    @Nested
    @DisplayName("provide classes of")
    class MessageClasses {

        @Test
        @DisplayName("transformed commands")
        void commands() {
            assertCommandClassesExactly(commanderClass.commands(),
                                        SigCreateTask.class,
                                        SigCreateProject.class,
                                        SigRemoveTaskFromProject.class,
                                        SigAssignTask.class);
        }

        @Test
        @DisplayName("produced commands")
        void outgoingCommands() {
            assertCommandClassesExactly(commanderClass.outgoingCommands(),
                                        SigAddTaskToProject.class,
                                        SigStartTask.class,
                                        SigSetProjectOwner.class,
                                        SigStopTask.class,
                                        SigAssignTask.class,
                                        SigPauseTask.class,
                                        SigRemoveTaskFromProject.class);
        }

        @Test
        @DisplayName("thrown rejections")
        void events() {
            assertEventClassesExactly(commanderClass.rejections(),
                                      ProjectRejections.SigCannotCreateProject.class);
        }

        @Test
        @DisplayName("events (including external) in response to which the commander produces commands")
        void domesticEvents() {
            assertEventClassesExactly(commanderClass.events(),
                                      SigProjectCreated.class,
                                      SigProjectStopped.class,
                                      // External events
                                      SigTaskDeleted.class,
                                      SigTaskMoved.class);
        }

        @Test
        @DisplayName("external events in response to which the commander produces commands")
        void externalEvents() {
            assertEventClassesExactly(commanderClass.externalEvents(),
                                      SigTaskDeleted.class,
                                      SigTaskMoved.class);
        }
    }
}
