/*
 * Copyright 2022, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.server.model.given;

import io.spine.base.RejectionMessage;
import io.spine.base.RejectionThrowable;
import io.spine.test.model.Rejections;

import java.io.IOException;

import static io.spine.base.Identifier.newUuid;

/**
 * Provides methods used by {@link io.spine.server.model.MethodExceptionCheckTest}.
 */
@SuppressWarnings("unused") // Reflective access.
public final class StubMethodContainer {

    private static void noExceptions() {
    }

    private static void checkedException() throws Exception {
        throw new IOException("Test checked exception");
    }

    private static void runtimeException() throws RuntimeException {
        throw new RuntimeException("Test runtime exception");
    }

    private static void customException() throws IOException {
        throw new IOException("Test custom exception");
    }

    private static void derivedException() throws DerivedThrowable {
        var rejection = Rejections.ModProjectAlreadyExists.newBuilder()
                .setId(newUuid())
                .build();
        throw new DerivedThrowable(rejection);
    }

    private static final class DerivedThrowable extends RejectionThrowable {

        private static final long serialVersionUID = 0L;

        private DerivedThrowable(RejectionMessage message) {
            super(message);
        }
    }
}
