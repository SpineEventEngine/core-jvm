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

package io.spine.system.server;

import io.spine.core.UserId;
import io.spine.net.EmailAddress;
import io.spine.system.server.given.diagnostics.ValidatedAggregate;
import io.spine.system.server.given.diagnostics.VerificationProcman;
import io.spine.system.server.given.diagnostics.ViolationsWatch;
import io.spine.system.server.test.InvalidText;
import io.spine.system.server.test.StartVerification;
import io.spine.system.server.test.ValidateAndSet;
import io.spine.system.server.test.ValidatedId;
import io.spine.testing.logging.mute.MuteLogging;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Identifier.newUuid;
import static io.spine.system.server.given.diagnostics.ViolationsWatch.DEFAULT;
import static io.spine.testing.server.blackbox.BlackBox.singleTenantWith;

@DisplayName("`ConstraintViolated` should be emitted when")
class ConstraintViolatedTest {

    @Test
    @MuteLogging
    @DisplayName("an entity state is set to an invalid value as a result of an event")
    void afterEvent() {
        try (var context = singleTenantWith(
                     ValidatedAggregate.class,
                     new ViolationsWatch.Repository()
             ).tolerateFailures()
        ) {
            var invalidText = "123-non numerical";
            context.receivesCommand(
                    ValidateAndSet.newBuilder()
                            .setId(ValidatedId.generate())
                            .setTextToValidate(invalidText)
                            .build()
            );
            context.assertEntity(DEFAULT, ViolationsWatch.class)
                   .hasStateThat()
                   .isEqualTo(InvalidText.newBuilder()
                                      .setId(DEFAULT)
                                      .setInvalidText(invalidText)
                                      .buildPartial()
                   );
        }
    }

    @Test
    @MuteLogging
    @DisplayName("an entity state is set to an invalid value as a result of a command")
    void afterCommand() {
        try (var context = singleTenantWith(VerificationProcman.class,
                                            new ViolationsWatch.Repository())
                .tolerateFailures()
        ) {
            context.receivesCommand(
                    StartVerification.newBuilder()
                            .setUserId(UserId.newBuilder().setValue(newUuid()))
                            .setAddress(EmailAddress.newBuilder().setValue("a@b.c"))
                            .build()
            );
            var state = (InvalidText) context.assertEntity(DEFAULT, ViolationsWatch.class)
                                             .actual()
                                             .state();
            var assertErrorMessage = assertThat(state.getErrorMessage());

            // Assert the reference to the invalid field.
            assertErrorMessage.contains("spine.test.diagnostics.Verification.email");
            // Assert the type of the field.
            assertErrorMessage.contains("spine.net.EmailAddress");
            // Asser the text of the error.
            assertErrorMessage.contains("must have a non-default value.");
        }
    }
}
