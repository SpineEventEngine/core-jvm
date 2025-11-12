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

package io.spine.server.command.model.given.handler;

import com.google.common.collect.ImmutableList;
import io.spine.base.MessageContext;
import io.spine.core.CommandContext;
import io.spine.model.contexts.projects.command.SigAddTaskToProject;
import io.spine.model.contexts.projects.command.SigAssignTask;
import io.spine.model.contexts.projects.command.SigCreateProject;
import io.spine.model.contexts.projects.command.SigSetProjectOwner;
import io.spine.model.contexts.projects.event.SigProjectStarted;
import io.spine.model.contexts.projects.event.SigTaskAssigned;
import io.spine.model.contexts.projects.event.SigTaskStarted;
import io.spine.server.command.AbstractAssignee;
import io.spine.server.command.Assign;
import io.spine.server.event.NoReaction;
import io.spine.server.command.DoNothing;
import io.spine.server.model.given.SignatureTestCommand;
import io.spine.server.tuple.EitherOf3;

import java.util.Optional;

import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * A standalone assignee which declares invalid {@link Assign command-handling methods}.
 *
 * <p>Being similar to {@link ValidAssignee}, this class also declares duplicate handlers
 * for the same commands. This is the way to avoid myriads of small classes which enumerate
 * all possible combinations or params.
 */
public final class InvalidAssignee extends AbstractAssignee {

    @Assign
    SigTaskStarted noParams() {
        return EventMessages.taskStarted();
    }

    @Assign
    SigTaskStarted nonCommandMessageParam(NoReaction command) {
        return EventMessages.taskStarted();
    }

    @Assign
    SigTaskStarted nonMessageParam(int command) {
        return EventMessages.taskStarted();
    }

    @Assign
    Optional<SigTaskStarted> optionalResult(SigAssignTask command) {
        return Optional.empty();
    }

    @Assign
    SigTaskStarted threeParams(SigAssignTask command, CommandContext ctx, SigAssignTask third) {
        return EventMessages.taskStarted();
    }

    @Assign
    SigTaskStarted wrongFirstParam(NoReaction command, MessageContext msg) {
        return EventMessages.taskStarted();
    }

    @Assign
    SigTaskStarted wrongSecondParam(SigAssignTask command, NoReaction message) {
        return EventMessages.taskStarted();
    }

    @Assign
    SigTaskStarted wrongContext(SigAssignTask command, MessageContext msg) {
        return EventMessages.taskStarted();
    }

    @Assign
    void voidMethod(SigAssignTask command) {
        // do nothing.
    }

    @Assign
    DoNothing commandResult(SigAssignTask command) {
        return DoNothing.getDefaultInstance();
    }

    @Assign
    int nonMessageResult(SigAssignTask command) {
        return 42;
    }

    @Assign
    SigProjectStarted justInterface(SignatureTestCommand command) {
        return EventMessages.projectStarted();
    }

    @Assign
    SigTaskStarted interfaceAndContext(SignatureTestCommand command, CommandContext context) {
        return EventMessages.taskStarted();
    }

    @Assign
    EitherOf3<SigTaskAssigned, SigTaskStarted, NoReaction>
    eitherWithNothing(SigAddTaskToProject command) {
        return EitherOf3.withC(noReaction());
    }

    @Assign
    Iterable<DoNothing> wrongIterable(SigAddTaskToProject command) {
        return ImmutableList.of();
    }

    @Assign
    SigSetProjectOwner wrongThrowable(SigCreateProject command) throws RuntimeException {
        throw newIllegalStateException("Command assignee has declared an illegal exception.");
    }
}
