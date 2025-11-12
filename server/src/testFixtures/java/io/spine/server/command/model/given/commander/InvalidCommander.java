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
import io.spine.base.MessageContext;
import io.spine.core.CommandContext;
import io.spine.model.contexts.projects.command.SigAddTaskToProject;
import io.spine.model.contexts.projects.command.SigAssignTask;
import io.spine.model.contexts.projects.command.SigCreateProject;
import io.spine.model.contexts.projects.command.SigSetProjectOwner;
import io.spine.model.contexts.projects.command.SigStartTask;
import io.spine.server.command.AbstractCommander;
import io.spine.server.command.Command;
import io.spine.server.command.DoNothing;
import io.spine.server.event.NoReaction;
import io.spine.server.model.given.SignatureTestCommand;
import io.spine.server.tuple.EitherOf3;

import java.util.Optional;

import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * A standalone commander which declares invalid {@link Command} substitution methods.
 *
 * <p>Being similar to {@link ValidCommander}, this class also declares duplicate handlers
 * for the same commands. Again, this seems to be the simplest way to test invalid signatures
 * by enumerating different invalid options.
 */
public final class InvalidCommander extends AbstractCommander {

    @Command
    SigStartTask noParams() {
        return TestCommandMessage.startTask();
    }

    @Command
    SigStartTask nonCommandMessageParam(NoReaction command) {
        return TestCommandMessage.startTask();
    }

    @Command
    SigStartTask nonMessageParam(int command) {
        return TestCommandMessage.startTask();
    }

    @Command
    Optional<SigStartTask> optionalResult(SigAssignTask command) {
        return Optional.empty();
    }

    @Command
    SigStartTask threeParams(SigAssignTask command, CommandContext ctx, SigAssignTask third) {
        return TestCommandMessage.startTask();
    }

    @Command
    SigStartTask wrongSecondParam(SigAssignTask command, NoReaction message) {
        return TestCommandMessage.startTask();
    }

    @Command
    SigStartTask wrongContext(SigAssignTask command, MessageContext msg) {
        return TestCommandMessage.startTask();
    }

    @Command
    void voidMethod(SigAssignTask command) {
        // do nothing.
    }

    @Command
    NoReaction eventResult(SigAssignTask command) {
        return noReaction();
    }

    @Command
    int nonMessageResult(SigAssignTask command) {
        return 42;
    }

    @Command
    SigStartTask justInterface(SignatureTestCommand command) {
        return TestCommandMessage.startTask();
    }

    @Command
    SigStartTask interfaceAndContext(SignatureTestCommand command, CommandContext context) {
        return TestCommandMessage.startTask();
    }

    @Command
    EitherOf3<SigAssignTask, SigStartTask, DoNothing>
    eitherWithNothing(SigAddTaskToProject command) {
        return EitherOf3.withC(doNothing());
    }

    @Command
    Iterable<NoReaction> wrongIterable(SigAddTaskToProject command) {
        return ImmutableList.of();
    }

    @Command
    SigSetProjectOwner wrongThrowable(SigCreateProject command) throws RuntimeException {
        throw newIllegalStateException("Command substitution method has thrown " +
                                               "an illegal exception.");
    }
}
