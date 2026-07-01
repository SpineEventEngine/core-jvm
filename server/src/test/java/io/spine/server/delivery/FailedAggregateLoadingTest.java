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

package io.spine.server.delivery;

import io.spine.server.delivery.given.ReceptionFailureTestEnv.ObservingMonitor;
import io.spine.system.server.DiagnosticMonitor;
import io.spine.testing.logging.mute.MuteLogging;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Identifier.newUuid;
import static io.spine.server.delivery.given.ReceptionFailureTestEnv.blackBoxWith;
import static io.spine.server.delivery.given.ReceptionFailureTestEnv.configureSynchronousDelivery;
import static io.spine.server.delivery.given.ReceptionFailureTestEnv.inboxMessages;
import static io.spine.server.delivery.given.ReceptionFailureTestEnv.receptionist;
import static io.spine.server.delivery.given.ReceptionFailureTestEnv.tellToTurnConditioner;
import static io.spine.server.delivery.given.ReceptionistAggregate.makeApplierFail;
import static io.spine.server.delivery.given.ReceptionistAggregate.makeApplierPass;

/**
 * A regression test for <a href="https://github.com/SpineEventEngine/core-jvm/issues/1472">issue
 * #1472</a> — {@code Delivery} should handle a failed aggregate loading during message delivery.
 *
 * <p>When an aggregate cannot apply an event (e.g. its {@code @Apply} method throws), its command
 * fails to be handled. This test verifies that such a failure is contained: it does not leak into
 * the delivery of <em>other</em> signals sharing the same shard, and — in particular — a
 * further, distinct command to the failing aggregate is <em>not</em> mistaken for a duplicate.
 */
@DisplayName("`Delivery` should handle a failed aggregate loading during message delivery")
final class FailedAggregateLoadingTest extends AbstractDeliveryTest {

    @AfterEach
    void resetApplier() {
        makeApplierPass();
    }

    @Test
    @DisplayName("without raising a spurious `CannotDispatchDuplicateCommand`, " +
            "and still delivering to other entities in the same shard")
    @MuteLogging
    @SuppressWarnings("resource" /* We don't care about closing the black box in this test. */)
    void handleFailedApplier() {
        var deliveryMonitor = new ObservingMonitor();
        configureSynchronousDelivery(deliveryMonitor);

        var diagnostics = new DiagnosticMonitor();
        var context = blackBoxWith(diagnostics).tolerateFailures();

        // The "poison" aggregate whose event applier always throws.
        var poisonId = newUuid();
        makeApplierFail();
        context.receivesCommand(tellToTurnConditioner(poisonId));  // The first command.
        context.receivesCommand(tellToTurnConditioner(poisonId));  // The second command.

        // A different entity, sharing the same (single) shard, is not affected by the failure.
        var bystanderId = newUuid();
        makeApplierPass();
        context.receivesCommand(tellToTurnConditioner(bystanderId));

        // Both commands to the poison aggregate failed and were reported as
        // `HandlerFailedUnexpectedly`. This also confirms the monitor is wired to the same
        // diagnostic sink that would emit `CannotDispatchDuplicateCommand`, so the emptiness
        // asserted below is a real absence, not a missed subscription.
        assertThat(diagnostics.handlerFailureEvents()).hasSize(2);

        // The second command to the failing aggregate is not treated as a duplicate.
        assertThat(diagnostics.duplicateCommandEvents()).isEmpty();

        // The failure of the poison aggregate was observed by the delivery monitor.
        assertThat(deliveryMonitor.lastFailure()).isPresent();

        // The other entity handled its command successfully.
        context.assertState(bystanderId, receptionist(bystanderId, 1));

        // No message is stuck in the inbox for an endless re-delivery.
        assertThat(inboxMessages()).isEmpty();
    }
}
