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

package io.spine.server.event.given;

import com.google.common.collect.ImmutableList;
import io.spine.base.EventMessage;
import io.spine.core.CommandContext;
import io.spine.core.EventContext;
import io.spine.model.contexts.projects.command.SigCreateProject;
import io.spine.model.contexts.projects.event.SigProjectCreated;
import io.spine.model.contexts.projects.event.SigTaskAddedToProject;
import io.spine.model.contexts.projects.event.SigTaskPaused;
import io.spine.model.contexts.projects.event.SigTaskRemovedFromProject;
import io.spine.model.contexts.projects.event.SigTaskStarted;
import io.spine.model.contexts.projects.event.SigTaskStopped;
import io.spine.model.contexts.projects.rejection.ProjectRejections;
import io.spine.server.event.AbstractEventReactor;
import io.spine.server.event.NoReaction;
import io.spine.server.event.React;
import io.spine.server.tuple.EitherOf3;
import io.spine.server.tuple.Pair;

import java.util.Optional;

/**
 * A standalone event reactor which declares valid {@linkplain React event-reacting handlers}
 * for the purposes of {@link io.spine.server.event.model.EventReactorSignatureTest
 * EventReactorSignatureTest}.
 *
 * <p>This class declares the duplicate handlers for some events, hence it cannot be
 * registered in any Bounded Context. This is done for simplicity of enumerating all possible
 * combinations of parameters.
 */
public final class ValidReactor extends AbstractEventReactor {

    @React
    SigTaskStarted singleMsgSingleResult(SigTaskAddedToProject event) {
        return taskStarted();
    }

    @React
    Optional<SigTaskStarted> singleMsgOptionalResult(SigTaskAddedToProject event) {
        return Optional.empty();
    }

    @React
    SigTaskStarted msgWithCtxSingleResult(SigTaskAddedToProject event, EventContext ctx) {
        return taskStarted();
    }

    @React
    SigProjectCreated justRejection(ProjectRejections.SigCannotCreateProject rejection) {
        return projectCreated();
    }

    @React
    SigProjectCreated
    rejectionWithCtx(ProjectRejections.SigCannotCreateProject rejection, CommandContext ctx) {
        return projectCreated();
    }

    @React
    SigProjectCreated rejectionWithCommand(ProjectRejections.SigCannotCreateProject rejection,
                                           SigCreateProject command) {
        return projectCreated();
    }

    @React
    SigProjectCreated rejectionWithCommandAndCtx(ProjectRejections.SigCannotCreateProject r,
                                                 SigCreateProject cmd,
                                                 CommandContext ctx) {
        return projectCreated();
    }

    @React
    Pair<SigTaskAddedToProject, SigTaskStarted>
    singleMsgPairResult(SigTaskAddedToProject event) {
        return Pair.of(taskAddedToProject(),
                       taskStarted());
    }

    @React
    Pair<SigTaskAddedToProject, Optional<SigTaskStarted>>
    pairWithOptionalResult(SigTaskAddedToProject event) {
        return Pair.withNullable(taskAddedToProject(), null);
    }

    @React
    EitherOf3<SigTaskPaused, SigTaskStopped, NoReaction>
    eitherOf3Result(SigTaskRemovedFromProject event) {
        return EitherOf3.withC(noReaction());
    }

    @React
    Iterable<EventMessage> iterableResult(SigTaskAddedToProject event) {
        return ImmutableList.of(taskStarted());
    }

    @React
    @SuppressWarnings("MethodMayBeStatic")
    private SigTaskStarted privateHandler(SigTaskAddedToProject event) {
        return taskStarted();
    }

    @React
    @SuppressWarnings({"ProtectedMemberInFinalClass", "ProtectedMembersInFinalClass"})
    // It is a use-case-under-test. IDEA's warning is singular, ErrorProne's is plural.
    protected SigTaskStarted protectedHandler(SigTaskAddedToProject event) {
        return taskStarted();
    }

    @React
    public SigTaskStarted publicHandler(SigTaskAddedToProject event) {
        return taskStarted();
    }

    private static SigProjectCreated projectCreated() {
        return SigProjectCreated.getDefaultInstance();
    }

    private static SigTaskAddedToProject taskAddedToProject() {
        return SigTaskAddedToProject.getDefaultInstance();
    }

    private static SigTaskStarted taskStarted() {
        return SigTaskStarted.getDefaultInstance();
    }
}
