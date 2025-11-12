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

package io.spine.server.command.model.given.commander;

import com.google.common.collect.ImmutableList;
import io.spine.core.External;
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
import io.spine.model.contexts.projects.rejection.SigCannotCreateProject;
import io.spine.server.command.AbstractCommander;
import io.spine.server.command.Command;
import io.spine.server.tuple.EitherOf2;
import io.spine.server.tuple.Pair;

import java.util.List;
import java.util.Optional;

import static io.spine.server.command.model.given.commander.TestCommandMessage.addTask;
import static io.spine.server.command.model.given.commander.TestCommandMessage.pauseTask;
import static io.spine.server.command.model.given.commander.TestCommandMessage.startTask;

/**
 * A standalone commander which declares valid {@link Command} substitution methods.
 */
public final class SampleCommander extends AbstractCommander {

    @Command
    Pair<SigAddTaskToProject, Optional<SigStartTask>>
    pairWithOptionalResult(SigCreateTask command) {
        return Pair.withNullable(addTask(), null);
    }

    @Command
    @SuppressWarnings("DoNotCallSuggester") // Always throws for the purpose of the tests.
    SigSetProjectOwner declaredRejection(SigCreateProject command) throws SigCannotCreateProject {
        throw SigCannotCreateProject.newBuilder()
                                    .build();
    }

    @Command
    SigPauseTask transform(SigRemoveTaskFromProject cmd) {
        return pauseTask();
    }

    @Command
    List<SigStartTask> toList(SigAssignTask command) {
        return ImmutableList.of(startTask());
    }

    @Command
    SigSetProjectOwner byEvent(SigProjectCreated event) {
        return SigSetProjectOwner.newBuilder().build();
    }

    @Command
    EitherOf2<SigStopTask, SigAssignTask> byEvent(SigProjectStopped event) {
        return EitherOf2.withA(SigStopTask.getDefaultInstance());
    }

    @Command
    SigRemoveTaskFromProject byExternalEvent(@External SigTaskDeleted event) {
        return SigRemoveTaskFromProject.newBuilder().build();
    }

    @Command
    SigRemoveTaskFromProject byExternalEvent(@External SigTaskMoved event) {
        return SigRemoveTaskFromProject.newBuilder().build();
    }
}
