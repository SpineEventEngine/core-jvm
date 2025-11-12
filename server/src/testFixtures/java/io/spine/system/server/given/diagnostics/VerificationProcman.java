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

package io.spine.system.server.given.diagnostics;

import io.spine.core.UserId;
import io.spine.net.EmailAddress;
import io.spine.server.command.Assign;
import io.spine.server.procman.ProcessManager;
import io.spine.system.server.test.StartVerification;
import io.spine.system.server.test.VerificationEmailSent;
import io.spine.test.diagnostics.Verification;

import static io.spine.test.diagnostics.Verification.Status.EMAIL_SENT;

public class VerificationProcman
        extends ProcessManager<UserId, Verification, Verification.Builder> {

    @Assign
    VerificationEmailSent handle(StartVerification command) {
        builder().setUserId(command.getUserId())
                 .setEmail(EmailAddress.getDefaultInstance())
                 .setStatus(EMAIL_SENT);
        return VerificationEmailSent
                .newBuilder()
                .setUserId(command.getUserId())
                .setAddress(command.getAddress())
                .build();
    }
}
