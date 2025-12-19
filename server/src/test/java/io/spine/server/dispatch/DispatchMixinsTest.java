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

package io.spine.server.dispatch;

import io.spine.server.dispatch.given.Given;
import io.spine.validation.FieldAwareMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests of mixins related to the framework message dispatching.
 */
class DispatchMixinsTest {

    private static final String SHOULD_MAKE_FIELDS_REACHABLE =
            "should override `readValue` so that the fields are reachable";

    @Test
    @DisplayName("`BatchDispatchOutcomeMixin` " + SHOULD_MAKE_FIELDS_REACHABLE)
    void batchDispatchOutcomeMixin() {
        checkReachable(BatchDispatchOutcome.getDefaultInstance());
    }

    @Test
    @DisplayName("`DispatchOutcomeMixin` " + SHOULD_MAKE_FIELDS_REACHABLE)
    void dispatchOutcomeMixin() {
        checkReachable(DispatchOutcome.getDefaultInstance());
    }

    @Test
    @DisplayName("`DispatchOutcomeMixin` should provide a `hasRejection` shortcut")
    void dispatchOutcomeMixinHasRejectionShortcut() {
        var noRejectionOutcome = DispatchOutcome.getDefaultInstance();
        assertThat(noRejectionOutcome.hasRejection()).isFalse();
        var event = Given.rejectionEvent();
        var rejectionSuccessOutcome = Success.newBuilder()
                .setRejection(event)
                .build();
        var rejectionOutcome = DispatchOutcome.newBuilder()
                .setSuccess(rejectionSuccessOutcome)
                .setPropagatedSignal(event.messageId())
                .build();
        assertThat(rejectionOutcome.hasRejection()).isTrue();
    }

    @Test
    @DisplayName("`IgnoreMixin` " + SHOULD_MAKE_FIELDS_REACHABLE)
    void ignoreMixin() {
        checkReachable(Ignore.getDefaultInstance());
    }

    @Test
    @DisplayName("`InterruptionMixin` " + SHOULD_MAKE_FIELDS_REACHABLE)
    void interruptionMixin() {
        checkReachable(Interruption.getDefaultInstance());
    }

    @Test
    @DisplayName("`ProducedCommandsMixin` " + SHOULD_MAKE_FIELDS_REACHABLE)
    void producedCommandsMixin() {
        checkReachable(ProducedCommands.getDefaultInstance());
    }

    @Test
    @DisplayName("`ProducedEventsMixin` " + SHOULD_MAKE_FIELDS_REACHABLE)
    void producedEventsMixin() {
        checkReachable(ProducedEvents.getDefaultInstance());
    }

    @Test
    @DisplayName("`SuccessMixin` " + SHOULD_MAKE_FIELDS_REACHABLE)
    void successMixin() {
        checkReachable(Success.getDefaultInstance());
    }

    private static void checkReachable(FieldAwareMessage msg) {
        assertThat(msg.checkFieldsReachable()).isTrue();
    }
}
