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
import io.spine.base.MessageContext;
import io.spine.core.EventContext;
import io.spine.core.UserId;
import io.spine.model.contexts.projects.command.SigStartTask;
import io.spine.model.contexts.projects.event.SigTaskAddedToProject;
import io.spine.model.contexts.projects.event.SigTaskStarted;
import io.spine.model.contexts.projects.rejection.SigCannotCreateProject;
import io.spine.server.command.AbstractCommander;
import io.spine.server.command.Command;
import io.spine.server.event.NoReaction;
import io.spine.server.event.React;
import io.spine.server.model.given.SignatureTestEvent;

import java.io.IOException;

/**
 * A standalone commander which declares invalid {@link Command} reaction methods.
 *
 * <p>Being similar to {@link ValidCommander}, this class also declares duplicate handlers
 * for the same events. Again, this seems to be the simplest way to test invalid signatures
 * by enumerating different invalid options.
 */
public final class InvalidCommander extends AbstractCommander {

    @Command
    SigStartTask noParams() {
        return EventMessages.startTask();
    }

    @Command
    SigStartTask nonEventMessageParam(UserId user) {
        return EventMessages.startTask();
    }

    @Command
    SigStartTask nonMessageParam(int event) {
        return EventMessages.startTask();
    }

    @Command
    SigStartTask threeParams(SigTaskAddedToProject event, EventContext ctx, NoReaction third) {
        return EventMessages.startTask();
    }

    @Command
    SigStartTask wrongSecondParam(SigTaskAddedToProject event, NoReaction message) {
        return EventMessages.startTask();
    }

    @Command
    SigStartTask wrongContext(SigTaskAddedToProject event, MessageContext msg) {
        return EventMessages.startTask();
    }

    @Command
    void voidMethod(SigTaskAddedToProject event) {
        // do nothing.
    }

    @Command
    SigTaskStarted eventResult(SigTaskAddedToProject event) {
        return EventMessages.taskStarted();
    }

    @Command
    int nonMessageResult(SigTaskAddedToProject event) {
        return 18;
    }

    @Command
    SigStartTask justInterface(SignatureTestEvent event) {
        return EventMessages.startTask();
    }

    @Command
    SigStartTask interfaceAndContext(SignatureTestEvent event, EventContext context) {
        return EventMessages.startTask();
    }

    @Command
    Iterable<UserId> wrongIterable(SigTaskAddedToProject event) {
        return ImmutableList.of();
    }

    @React
    @SuppressWarnings("DoNotCallSuggester") // Not relevant in this case of to test data.
    SigTaskStarted declaredThrowable(SigTaskAddedToProject event) throws IOException {
        throw new IOException("An invalid commander method has thrown an exception");
    }

    @React
    @SuppressWarnings("DoNotCallSuggester") // Not relevant in this case of to test data.
    SigTaskStarted declaredRejection(SigTaskAddedToProject e) throws SigCannotCreateProject {
        throw EventMessages.cannotCreateProject();
    }
}
