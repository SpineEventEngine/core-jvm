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

import io.spine.base.Error;
import io.spine.server.aggregate.IncompleteHistoryRetryMonitor;
import io.spine.server.delivery.given.ReceptionistAggregate;
import io.spine.server.delivery.given.TransientFailure;
import io.spine.testing.SlowTest;
import io.spine.testing.logging.mute.MuteLogging;
import io.spine.testing.server.blackbox.BlackBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Identifier.newUuid;
import static io.spine.server.delivery.given.ReceptionFailureTestEnv.blackBox;
import static io.spine.server.delivery.given.ReceptionFailureTestEnv.configureDelivery;
import static io.spine.server.delivery.given.ReceptionFailureTestEnv.inboxMessages;
import static io.spine.server.delivery.given.ReceptionFailureTestEnv.receptionist;
import static io.spine.server.delivery.given.ReceptionFailureTestEnv.sleep;
import static io.spine.server.delivery.given.ReceptionFailureTestEnv.tellToTurnConditioner;

/**
 * Tests the {@link IncompleteHistoryRetryMonitor} against the actual delivery pipeline.
 *
 * <p>The reception failure is simulated with a {@link TransientFailure}, which the monitor under
 * test is configured to treat as retryable. The recognition of the production
 * {@link io.spine.server.aggregate.IncompleteHistoryException} by the default monitor is covered
 * by {@code IncompleteHistoryRetryMonitorSpec}.
 */
@SlowTest
@DisplayName("`IncompleteHistoryRetryMonitor` should")
@SuppressWarnings("resource" /* We don't care about closing black boxes in this test. */)
final class IncompleteHistoryRetryTest extends AbstractDeliveryTest {

    private @Nullable IncompleteHistoryRetryMonitor monitor;

    @AfterEach
    void cleanUp() {
        if (monitor != null) {
            monitor.close();
            monitor = null;
        }
        ReceptionistAggregate.resetFailures();
    }

    @Test
    @DisplayName("retry the delivery until the storage settles, then handle the signal")
    @MuteLogging
    void retryUntilSettles() {
        var context = configure(5);
        var id = newUuid();
        ReceptionistAggregate.failTransiently(id, 2);

        context.receivesCommand(tellToTurnConditioner(id));
        sleep();

        assertThat(ReceptionistAggregate.applierInvocations(id)).isEqualTo(3);
        context.assertState(id, receptionist(id, 1));
        assertInboxEmpty();
    }

    @Test
    @DisplayName("give up and drain the message after the maximum number of attempts")
    @MuteLogging
    void giveUpAfterMaxAttempts() {
        var context = configure(3);
        var id = newUuid();
        ReceptionistAggregate.failAlwaysTransiently(id);

        context.receivesCommand(tellToTurnConditioner(id));
        sleep();

        assertThat(ReceptionistAggregate.applierInvocations(id)).isEqualTo(3);
        assertInboxEmpty();
    }

    @Test
    @DisplayName("not block the delivery of other entities in the same shard")
    @MuteLogging
    void notBlockOtherEntities() {
        var context = configure(100);
        var failing = newUuid();
        var healthy = newUuid();
        ReceptionistAggregate.failAlwaysTransiently(failing);

        context.receivesCommand(tellToTurnConditioner(failing));
        context.receivesCommand(tellToTurnConditioner(healthy));
        sleep();

        // The healthy entity is handled (exactly-once effect) even though its shard-mate keeps
        // failing and being retried. The exact number of the healthy applier invocations is not
        // asserted, as a background retry run may re-load the aggregate (replaying its history)
        // before the delivery-level deduplication discards the redundant attempt.
        context.assertState(healthy, receptionist(healthy, 1));
        assertThat(ReceptionistAggregate.applierInvocations(failing)).isGreaterThan(1);
    }

    @Test
    @DisplayName("not retry a failure of an unrelated type")
    @MuteLogging
    void ignoreUnrelatedFailures() {
        var context = configure(5);
        var id = newUuid();
        ReceptionistAggregate.failUnrelated(id);

        context.receivesCommand(tellToTurnConditioner(id));
        sleep();

        assertThat(ReceptionistAggregate.applierInvocations(id)).isEqualTo(1);
        assertInboxEmpty();
    }

    private BlackBox configure(int maxAttempts) {
        monitor = retryMonitor(maxAttempts);
        configureDelivery(monitor);
        return blackBox().tolerateFailures();
    }

    private static IncompleteHistoryRetryMonitor retryMonitor(int maxAttempts) {
        var builder = IncompleteHistoryRetryMonitor.newBuilder()
                .maxAttempts(maxAttempts)
                .initialDelay(Duration.ofMillis(30))
                .maxDelay(Duration.ofMillis(60));
        return new IncompleteHistoryRetryMonitor(builder) {
            @Override
            protected boolean isRetryable(Error error) {
                return TransientFailure.class.getCanonicalName()
                                             .equals(error.getType());
            }
        };
    }

    private static void assertInboxEmpty() {
        var messages = inboxMessages();
        assertThat(messages.size()).isEqualTo(0);
    }
}
