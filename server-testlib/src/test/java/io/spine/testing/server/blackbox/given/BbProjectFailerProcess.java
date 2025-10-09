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

package io.spine.testing.server.blackbox.given;

import io.spine.server.command.Assign;
import io.spine.server.event.NoReaction;
import io.spine.server.event.React;
import io.spine.server.procman.ProcessManager;
import io.spine.testing.server.blackbox.BbProjectFailer;
import io.spine.testing.server.blackbox.BbProjectId;
import io.spine.testing.server.blackbox.command.BbFailProject;
import io.spine.testing.server.blackbox.event.BbProjectFailed;
import io.spine.testing.server.blackbox.probe.FailedHandlerGuard;

/**
 * Test environment for testing
 * {@link FailedHandlerGuard FailedHandlerGuard} integration.
 */
public final class BbProjectFailerProcess
        extends ProcessManager<BbProjectId, BbProjectFailer, BbProjectFailer.Builder> {

    @Assign
    @SuppressWarnings("DoNotCallSuggester") // Does not apply to this test env. case.
    BbProjectFailed on(BbFailProject c) {
        throw new RuntimeException("Command handling failed unexpectedly.");
    }

    @React
    @SuppressWarnings("DoNotCallSuggester") // Does not apply to this test env. case.
    NoReaction on(BbProjectFailed e) {
        throw new RuntimeException("Reaction on the event failed unexpectedly.");
    }
}
