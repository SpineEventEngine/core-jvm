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

package io.spine.testing.server.blackbox.probe;

import io.spine.system.server.HandlerFailedUnexpectedly;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.util.logging.Logger;

import static io.spine.base.Errors.causeOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`FailedHandlerGuard` should")
final class FailedHandlerGuardTest extends DiagnosticLoggingTest {

    private FailedHandlerGuard guard;

    @BeforeEach
    void initGuard() {
        guard = new FailedHandlerGuard();
    }

    @Test
    @DisplayName("log `HandlerFailedUnexpectedly` event")
    void tolerateException() {
        guard.tolerateFailures();
        var error = causeOf(new IllegalStateException("Test exception. The handler is OK."));
        guard.on(
                HandlerFailedUnexpectedly.newBuilder()
                        .setEntity(entity())
                        .setError(error)
                        .build()
        );
        assertLogged(error.getMessage());
    }

    @Test
    @DisplayName("fail test on `HandlerFailedUnexpectedly` event")
    void failTest() {
        var error = causeOf(new IllegalStateException("Test exception. Handler is fine."));
        assertThrows(AssertionFailedError.class, () -> guard.on(
                HandlerFailedUnexpectedly.newBuilder()
                        .setEntity(entity())
                        .setError(error)
                        .build()
        ));
    }

    @Override
    protected Logger logger() {
        return Logger.getLogger(FailedHandlerGuard.class.getName());
    }
}
