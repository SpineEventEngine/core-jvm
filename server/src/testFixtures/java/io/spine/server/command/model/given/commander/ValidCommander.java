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
import io.spine.base.CommandMessage;
import io.spine.core.CommandContext;
import io.spine.model.contexts.projects.command.SigAddTaskToProject;
import io.spine.model.contexts.projects.command.SigAssignTask;
import io.spine.model.contexts.projects.command.SigCreateProject;
import io.spine.model.contexts.projects.command.SigCreateTask;
import io.spine.model.contexts.projects.command.SigPauseTask;
import io.spine.model.contexts.projects.command.SigRemoveTaskFromProject;
import io.spine.model.contexts.projects.command.SigSetProjectOwner;
import io.spine.model.contexts.projects.command.SigStartTask;
import io.spine.model.contexts.projects.command.SigStopTask;
import io.spine.model.contexts.projects.rejection.SigCannotCreateProject;
import io.spine.server.command.AbstractCommander;
import io.spine.server.command.Command;
import io.spine.server.tuple.EitherOf2;
import io.spine.server.tuple.Pair;

import java.util.Optional;

import static io.spine.server.command.model.given.commander.TestCommandMessage.startTask;

/**
 * A standalone commander which declares valid {@link Command} substitution methods.
 *
 * <p>This class declares the duplicate handlers for some commands, hence it cannot be
 * registered in any Bounded Context. This is done for simplicity of enumerating all possible
 * combinations of parameters.
 */
public final class ValidCommander extends AbstractCommander {

    @Command
    SigStartTask singleMsgSingleResult(SigAssignTask command) {
        return startTask();
    }

    @Command
    @SuppressWarnings("DoNotCallSuggester") // Not relevant in this case of to test data.
    SigSetProjectOwner declaredRejection(SigCreateProject command) throws SigCannotCreateProject {
        throw SigCannotCreateProject.newBuilder().build();
    }

    @Command
    SigStartTask msgWithCtxSingleResult(SigAssignTask command, CommandContext ctx) {
        return startTask();
    }

    @Command
    Pair<SigAssignTask, SigStartTask> singleMsgPairResult(SigAddTaskToProject command) {
        return Pair.of(TestCommandMessage.assignTask(), startTask());
    }

    @Command
    Pair<SigAssignTask, SigStartTask>
    msgWithCtxPairResult(SigAddTaskToProject command, CommandContext ctx) {
        return Pair.of(TestCommandMessage.assignTask(), startTask());
    }

    @Command
    Pair<SigAddTaskToProject, Optional<SigStartTask>>
    pairWithOptionalResult(SigCreateTask command) {
        return Pair.withNullable(TestCommandMessage.addTask(), null);
    }

    @Command
    Pair<SigAddTaskToProject, Optional<SigStartTask>>
    msgWithCtxPairWithOptional(SigCreateTask command, CommandContext ctx) {
        return Pair.withNullable(TestCommandMessage.addTask(), null);
    }

    @Command
    EitherOf2<SigStopTask, SigPauseTask> singleMsgEitherOf2(SigRemoveTaskFromProject cmd) {
        return EitherOf2.withB(TestCommandMessage.pauseTask());
    }

    @Command
    EitherOf2<SigStopTask, SigPauseTask>
    msgWithCtxEitherOf2(SigRemoveTaskFromProject cmd, CommandContext ctx) {
        return EitherOf2.withB(TestCommandMessage.pauseTask());
    }

    @Command
    Iterable<CommandMessage> singleMsgIterableResult(SigAssignTask command) {
        return ImmutableList.of(startTask());
    }

    @Command
    Iterable<CommandMessage> msgWithCtxIterableResult(SigAssignTask command, CommandContext ctx) {
        return ImmutableList.of(startTask());
    }

    @Command
    @SuppressWarnings("MethodMayBeStatic")
    // It is a use-case-under-test.
    private SigStartTask privateHandler(SigAssignTask command) {
        return startTask();
    }

    @Command
    @SuppressWarnings({"ProtectedMemberInFinalClass", "ProtectedMembersInFinalClass"})
    // testing the visibility level. IDEA's warning is singular, ErrorProne's is plural.
    protected SigStartTask protectedHandler(SigAssignTask command) {
        return startTask();
    }

    @Command
    public SigStartTask publicHandler(SigAssignTask command) {
        return startTask();
    }
}
