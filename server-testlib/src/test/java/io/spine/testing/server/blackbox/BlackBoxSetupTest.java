/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.testing.server.blackbox;

import io.spine.server.type.CommandEnvelope;
import io.spine.testing.client.TestActorRequestFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.testing.server.blackbox.given.Given.createProject;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("`BlackBoxSetup.command()` should")
class BlackBoxSetupTest {

    private final TestActorRequestFactory requestFactory =
            new TestActorRequestFactory(BlackBoxSetupTest.class);

    @Test
    @DisplayName("wrap a command message into a `Command`")
    void wrapCommandMessage() {
        var message = createProject();

        var command = BlackBoxSetup.command(message, requestFactory);

        assertThat(CommandEnvelope.of(command).message()).isEqualTo(message);
    }

    @Test
    @DisplayName("return an already built `Command` as-is")
    void returnPreBuiltCommand() {
        var prebuilt = requestFactory.command()
                                     .create(createProject());

        var command = BlackBoxSetup.command(prebuilt, requestFactory);

        assertSame(prebuilt, command);
    }
}
