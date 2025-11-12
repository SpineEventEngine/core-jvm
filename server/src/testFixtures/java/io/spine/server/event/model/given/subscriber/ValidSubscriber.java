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

package io.spine.server.event.model.given.subscriber;

import io.spine.core.CommandContext;
import io.spine.core.EventContext;
import io.spine.core.Subscribe;
import io.spine.model.contexts.projects.command.SigCreateProject;
import io.spine.model.contexts.projects.event.SigTaskAddedToProject;
import io.spine.model.contexts.projects.rejection.ProjectRejections;
import io.spine.server.event.AbstractEventSubscriber;

/**
 * A standalone event subscriber which declares valid {@link Subscribe event-subscribing
 * handlers}.
 *
 * <p>This class declares the duplicate handlers for some events, hence it cannot be
 * registered in any Bounded Context. This is done for simplicity of enumerating all possible
 * combinations of parameters.
 *
 * @see io.spine.server.event.model.EventSubscriberSignatureTest
 */
public final class ValidSubscriber extends AbstractEventSubscriber {

    @Subscribe
    void singleMsgSingleResult(SigTaskAddedToProject event) {
        // do nothing.
    }

    @Subscribe
    void msgWithCtxSingleResult(SigTaskAddedToProject event, EventContext ctx) {
        // do nothing.
    }

    @Subscribe
    void justRejection(ProjectRejections.SigCannotCreateProject rejection) {
        // do nothing.
    }

    @Subscribe
    void rejectionWithCtx(ProjectRejections.SigCannotCreateProject r, CommandContext ctx) {
        // do nothing.
    }

    @Subscribe
    void rejectionWithCommand(ProjectRejections.SigCannotCreateProject rejection,
                              SigCreateProject command) {
        // do nothing.
    }

    @Subscribe
    void rejectionWithCommandAndCtx(ProjectRejections.SigCannotCreateProject rejection,
                                    SigCreateProject cmd,
                                    CommandContext ctx) {
        // do nothing.
    }

    @Subscribe
    private void privateHandler(SigTaskAddedToProject event) {
        // do nothing.
    }

    @Subscribe
    private void protectedHandler(SigTaskAddedToProject event) {
        // do nothing.
    }

    @Subscribe
    public void publicHandler(SigTaskAddedToProject event) {
        // do nothing.
    }
}
