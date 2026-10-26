/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.server.delivery;

import io.spine.server.delivery.given.ReceptionFailureTestEnv.MarkFailureDeliveredMonitor;
import io.spine.server.delivery.given.ReceptionFailureTestEnv.ObservingMonitor;
import io.spine.testing.SlowTest;
import io.spine.testing.logging.mute.MuteLogging;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Identifier.newUuid;
import static io.spine.server.delivery.given.ReceptionFailureTestEnv.blackBox;
import static io.spine.server.delivery.given.ReceptionFailureTestEnv.configureDelivery;
import static io.spine.server.delivery.given.ReceptionFailureTestEnv.inboxMessages;
import static io.spine.server.delivery.given.ReceptionFailureTestEnv.receptionist;
import static io.spine.server.delivery.given.ReceptionFailureTestEnv.sleep;
import static io.spine.server.delivery.given.ReceptionFailureTestEnv.tellToTurnConditioner;
import static io.spine.server.delivery.given.ReceptionistAggregate.FAILURE_MESSAGE;
import static io.spine.server.delivery.given.ReceptionistAggregate.makeApplierFail;
import static io.spine.server.delivery.given.ReceptionistAggregate.makeApplierPass;

@SlowTest
@DisplayName("`Delivery` should allow to monitor the failed reception of signals ")
final class ReceptionFailureTest extends AbstractDeliveryTest {

    @Test
    @DisplayName("and repeat dispatching of the corresponding `InboxMessage`")
    @MuteLogging
    void allowFailureRethrow() {
        var monitor = new ObservingMonitor();
        configureDelivery(monitor);
        try (var context = blackBox().tolerateFailures()) {
            var receptionistId = newUuid();
            var command = tellToTurnConditioner(receptionistId);
            makeApplierPass();
            context.receivesCommand(command);
            sleep();
            assertThat(monitor.lastFailure()).isEmpty();
            context.assertState(receptionistId, receptionist(receptionistId, 1));

            var failureObserved = new AtomicBoolean(false);
            monitor.setResolver((failure) -> {
                failureObserved.set(true);
                var error = failure.error();
                assertThat(error.getStacktrace()).contains(FAILURE_MESSAGE);

                makeApplierPass();
                return failure.repeatDispatching();
            });

            makeApplierFail();
            context.receivesCommand(command);
            sleep();

            assertThat(failureObserved.get())
                    .isTrue();
            assertInboxEmpty();
        }
    }

    @Test
    @DisplayName("and mark the corresponding `InboxMessage` as delivered")
    @MuteLogging
    void allowMarkingFailedMessageAsDelivered() {
        var monitor = new MarkFailureDeliveredMonitor();
        configureDelivery(monitor);
        try (var context = blackBox().tolerateFailures()) {
            var receptionistId = newUuid();
            var command = tellToTurnConditioner(receptionistId);
            makeApplierFail();
            context.receivesCommand(command);
            sleep();

            assertThat(monitor.failureReceived()).isTrue();
            assertInboxEmpty();
        }
    }

    private static void assertInboxEmpty() {
        var messages = inboxMessages();
        assertThat(messages.size()).isEqualTo(0);
    }
}
