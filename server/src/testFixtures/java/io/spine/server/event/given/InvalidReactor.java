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
import io.spine.base.MessageContext;
import io.spine.core.CommandContext;
import io.spine.core.EventContext;
import io.spine.core.UserId;
import io.spine.model.contexts.projects.command.SigCreateProject;
import io.spine.model.contexts.projects.command.SigStartTask;
import io.spine.model.contexts.projects.event.SigProjectCreated;
import io.spine.model.contexts.projects.event.SigTaskAddedToProject;
import io.spine.model.contexts.projects.event.SigTaskStarted;
import io.spine.model.contexts.projects.rejection.ProjectRejections;
import io.spine.model.contexts.projects.rejection.SigCannotCreateProject;
import io.spine.server.event.AbstractEventReactor;
import io.spine.server.event.NoReaction;
import io.spine.server.event.React;
import io.spine.server.model.given.SignatureTestEvent;

import java.io.IOException;

/**
 * A standalone reactor which declares invalid {@linkplain React event-reacting handlers} for
 * the purposes of {@link io.spine.server.event.model.EventReactorSignatureTest
 * EventReactorSignatureTest}.
 *
 * <p>Being similar to {@link ValidReactor}, this class also declares duplicate handlers
 * for the same events. This is the way to avoid lots of small classes enumerating
 * all possible combinations or params.
 *
 * @see io.spine.server.event.model.EventReactorSignatureTest
 */
public final class InvalidReactor extends AbstractEventReactor {

    @React
    SigTaskStarted noParams() {
        return taskStarted();
    }

    @React
    SigTaskStarted nonEventMessageParam(UserId user) {
        return taskStarted();
    }

    @React
    SigTaskStarted nonMessageParam(int event) {
        return taskStarted();
    }

    @React
    SigTaskStarted wrongThreeParams(SigTaskAddedToProject e, EventContext ctx, NoReaction third) {
        return taskStarted();
    }

    @React
    SigProjectCreated rejectionAndThreeMoreParams(ProjectRejections.SigCannotCreateProject r,
                                                  SigCreateProject cmd,
                                                  CommandContext ctx,
                                                  UserId user) {
        return projectCreated();
    }

    @React
    SigTaskStarted wrongSecondParam(SigTaskAddedToProject event, NoReaction message) {
        return taskStarted();
    }

    @React
    SigTaskStarted wrongContext(SigTaskAddedToProject event, MessageContext msg) {
        return taskStarted();
    }

    @React
    void voidMethod(SigTaskAddedToProject event) {
        // do nothing.
    }

    @React
    SigStartTask commandResult(SigTaskAddedToProject event) {
        return SigStartTask.getDefaultInstance();
    }

    @React
    int nonMessageResult(SigTaskAddedToProject event) {
        return 18;
    }

    @React
    SigTaskStarted justInterface(SignatureTestEvent event) {
        return taskStarted();
    }

    @React
    SigTaskStarted interfaceAndContext(SignatureTestEvent event, EventContext context) {
        return taskStarted();
    }

    @React
    Iterable<UserId> wrongIterable(SigTaskAddedToProject event) {
        return ImmutableList.of();
    }

    @React
    @SuppressWarnings("DoNotCallSuggester") // Always throws for the purpose of the tests.
    SigTaskStarted declaredThrowable(SigTaskAddedToProject event) throws IOException {
        throw new IOException("An invalid reactor method has thrown an exception");
    }

    @React
    @SuppressWarnings("DoNotCallSuggester") // Always throws for the purpose of the tests.
    SigTaskStarted declaredRejection(SigTaskAddedToProject e) throws SigCannotCreateProject {
        throw SigCannotCreateProject.newBuilder()
                                    .build();
    }

    private static SigTaskStarted taskStarted() {
        return SigTaskStarted.getDefaultInstance();
    }

    private static SigProjectCreated projectCreated() {
        return SigProjectCreated.getDefaultInstance();
    }
}
