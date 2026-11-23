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

package io.spine.server.procman.model;

import io.spine.base.RejectionMessage;
import io.spine.server.entity.rejection.StandardRejections;
import io.spine.server.procman.given.pm.LastSignalMemo;
import io.spine.server.type.EventClass;
import io.spine.test.procman.command.PmAddTask;
import io.spine.test.procman.command.PmCancelIteration;
import io.spine.test.procman.command.PmCreateProject;
import io.spine.test.procman.command.PmPlanIteration;
import io.spine.test.procman.command.PmReviewBacklog;
import io.spine.test.procman.command.PmScheduleRetrospective;
import io.spine.test.procman.command.PmStartIteration;
import io.spine.test.procman.command.PmStartProject;
import io.spine.test.procman.command.PmThrowEntityAlreadyArchived;
import io.spine.test.procman.command.PmThrowRuntimeException;
import io.spine.test.procman.event.PmIterationCompleted;
import io.spine.test.procman.event.PmIterationPlanned;
import io.spine.test.procman.event.PmOwnerChanged;
import io.spine.test.procman.event.PmProjectCreated;
import io.spine.test.procman.event.PmProjectStarted;
import io.spine.test.procman.event.PmTaskAdded;
import io.spine.test.procman.quiz.event.PmQuestionAnswered;
import io.spine.test.procman.quiz.event.PmQuizStarted;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.server.procman.model.ProcessManagerClass.asProcessManagerClass;
import static io.spine.testing.server.Assertions.assertCommandClassesExactly;
import static io.spine.testing.server.Assertions.assertEventClassesExactly;

@DisplayName("`ProcessManagerClass` should")
class ProcessManagerClassTest {

    private ProcessManagerClass<?> processManagerClass;

    @BeforeEach
    void createClass() {
        processManagerClass = asProcessManagerClass(LastSignalMemo.class);
    }

    @Nested
    @DisplayName("provide classes of")
    class MessageClasses {

        @Test
        @DisplayName("handled and transformed commands")
        void commands() {
            assertCommandClassesExactly(
                    processManagerClass.commands(),

                    // Handled commands
                    PmCreateProject.class,
                    PmAddTask.class,
                    PmReviewBacklog.class,
                    PmScheduleRetrospective.class,
                    PmPlanIteration.class,
                    PmStartIteration.class,
                    PmThrowEntityAlreadyArchived.class,
                    PmThrowRuntimeException.class,
                    // Transformed commands
                    PmStartProject.class,
                    PmCancelIteration.class
            );
        }

        @Test
        @DisplayName("events (including rejections) on which the process manager reacts")
        void reactions() {
            assertEventClassesExactly(
                    processManagerClass.events(),

                    PmProjectCreated.class,
                    PmTaskAdded.class,
                    PmProjectStarted.class,
                    // External events
                    PmQuizStarted.class,
                    PmQuestionAnswered.class,
                    // Reactions with commands
                    PmOwnerChanged.class,
                    PmIterationPlanned.class,
                    PmIterationCompleted.class,
                    // Reactions on rejections.
                    StandardRejections.EntityAlreadyArchived.class
            );
        }

        @Test
        @DisplayName("external events on which the process manager reacts")
        void externalEvents() {
            assertEventClassesExactly(
                    processManagerClass.externalEvents(),

                    PmQuizStarted.class,
                    PmQuestionAnswered.class
            );
        }

        @Test
        @DisplayName("commands produced by the process manager")
        void producedCommands() {
            assertCommandClassesExactly(
                    processManagerClass.outgoingCommands(),

                    PmAddTask.class,
                    PmReviewBacklog.class,
                    PmScheduleRetrospective.class,
                    PmPlanIteration.class,
                    PmScheduleRetrospective.class,
                    PmPlanIteration.class,
                    PmStartIteration.class,
                    PmCreateProject.class
            );
        }

        @Test
        @DisplayName("generated rejections")
        void rejections() {
            Class<? extends RejectionMessage> cls = StandardRejections.EntityAlreadyArchived.class;
            var rejectionClass = EventClass.from(cls);
            assertThat(processManagerClass.rejections())
                    .containsExactly(rejectionClass);
        }
    }
}
