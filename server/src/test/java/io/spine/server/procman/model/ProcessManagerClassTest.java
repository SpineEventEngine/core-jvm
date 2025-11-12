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
import io.spine.testing.server.model.ModelTests;
import org.junit.jupiter.api.BeforeAll;
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

    @BeforeAll
    static void clearModel() {
        ModelTests.dropAllModels();
    }

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
