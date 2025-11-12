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

package io.spine.server.command.model.given.reaction;

import com.google.common.collect.ImmutableList;
import io.spine.base.CommandMessage;
import io.spine.core.CommandContext;
import io.spine.core.EventContext;
import io.spine.model.contexts.projects.command.SigAddTaskToProject;
import io.spine.model.contexts.projects.command.SigCreateProject;
import io.spine.model.contexts.projects.command.SigPauseTask;
import io.spine.model.contexts.projects.command.SigStartTask;
import io.spine.model.contexts.projects.command.SigStopTask;
import io.spine.model.contexts.projects.event.SigTaskAddedToProject;
import io.spine.model.contexts.projects.rejection.ProjectRejections;
import io.spine.server.command.AbstractCommander;
import io.spine.server.command.Command;
import io.spine.server.command.DoNothing;
import io.spine.server.tuple.EitherOf3;
import io.spine.server.tuple.Pair;

import java.util.Optional;

/**
 * A standalone commander which declares valid {@link Command} reaction methods.
 *
 * <p>This class declares the duplicate handlers for some events, hence it cannot be
 * registered in any Bounded Context. This is done for simplicity of enumerating all possible
 * combinations of parameters.
 */
@SuppressWarnings("MethodMayBeStatic")
public final class ValidCommander extends AbstractCommander {

    @Command
    SigStartTask singleMsgSingleResult(SigTaskAddedToProject event) {
        return EventMessages.startTask();
    }

    @Command
    Optional<SigStartTask> singleMsgOptionalResult(SigTaskAddedToProject event) {
        return Optional.empty();
    }

    @Command
    SigStartTask msgWithCtxSingleResult(SigTaskAddedToProject event, EventContext ctx) {
        return EventMessages.startTask();
    }

    @Command
    SigCreateProject justRejection(ProjectRejections.SigCannotCreateProject rejection) {
        return EventMessages.createProject();
    }

    @Command
    SigCreateProject rejectionWithCtx(ProjectRejections.SigCannotCreateProject rejection,
                                      CommandContext context) {
        return EventMessages.createProject();
    }

    @Command
    Pair<SigAddTaskToProject, SigStartTask> singleMsgPairResult(SigTaskAddedToProject event) {
        return Pair.of(EventMessages.addTaskToProject(), EventMessages.startTask());
    }

    @Command
    Pair<SigAddTaskToProject, Optional<SigStartTask>>
    pairWithOptionalResult(SigTaskAddedToProject event) {
        return Pair.withNullable(EventMessages.addTaskToProject(), null);
    }

    @Command
    EitherOf3<SigPauseTask, SigStopTask, DoNothing>
    eitherOf3Result(SigTaskAddedToProject event) {
        return EitherOf3.withC(doNothing());
    }

    @Command
    Iterable<CommandMessage> iterableResult(SigTaskAddedToProject event) {
        return ImmutableList.of(EventMessages.startTask());
    }

    @Command
    private SigStartTask privateHandler(SigTaskAddedToProject event) {
        return EventMessages.startTask();
    }

    @Command
    private SigStartTask protectedHandler(SigTaskAddedToProject event) {
        return EventMessages.startTask();
    }

    @Command
    public SigStartTask publicHandler(SigTaskAddedToProject event) {
        return EventMessages.startTask();
    }
}
