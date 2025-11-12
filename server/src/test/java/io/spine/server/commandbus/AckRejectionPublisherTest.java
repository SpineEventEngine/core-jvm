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

package io.spine.server.commandbus;

import io.spine.core.CommandId;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.bus.Listener;
import io.spine.server.event.RejectionFactory;
import io.spine.server.type.EventEnvelope;
import io.spine.test.commandbus.CmdBusCaffetteriaId;
import io.spine.test.commandbus.command.CmdBusEntryDenied;
import io.spine.testing.client.TestActorRequestFactory;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.server.bus.MessageIdExtensions.acknowledge;
import static io.spine.server.bus.MessageIdExtensions.reject;
import static io.spine.testing.Assertions.assertIllegalState;

@DisplayName("`AckRejectionPublisher` should")
class AckRejectionPublisherTest {

    private MemoizingListener listener;
    private AckRejectionPublisher publisher;

    @BeforeEach
    void createPublisher() {
        listener = new MemoizingListener();
        var context = BoundedContextBuilder
                .assumingTests()
                .addEventListener(listener)
                .build();
        var eventBus = context.eventBus();
        publisher = new AckRejectionPublisher(eventBus);
    }

    @Test
    @DisplayName("publish the passed rejection to event bus")
    void publishRejection() {
        var requestFactory = new TestActorRequestFactory(AckRejectionPublisherTest.class);
        var command = requestFactory.generateCommand();

        var throwable = stubThrowable();
        var rejection = RejectionFactory.reject(command, throwable);
        var ack = reject(CommandId.generate(), rejection);
        publisher.onNext(ack);
        assertThat(listener.lastReceived)
                .isNotNull();
        assertThat(listener.lastReceived.message())
                .isEqualTo(throwable.messageThrown());
    }

    private static CmdBusEntryDenied stubThrowable() {
        var throwable = CmdBusEntryDenied.newBuilder()
                .setId(CmdBusCaffetteriaId.generate())
                .setVisitorCount(15)
                .setReason("Test command bus rejection.")
                .build();
        return throwable;
    }

    @Test
    @DisplayName("ignore non-rejection `Ack`s")
    void ignoreNonRejection() {
        var ack = acknowledge(CommandId.generate());
        publisher.onNext(ack);
        assertThat(listener.lastReceived)
                .isNull();
    }

    @Test
    @DisplayName("re-throw an error passed to `onError` as `IllegalStateException`")
    void rethrowError() {
        var exception = new RuntimeException("Test Ack publisher exception.");
        var thrown = assertIllegalState(() -> publisher.onError(exception));
        assertThat(thrown.getCause())
                .isEqualTo(exception);
    }

    private static class MemoizingListener implements Listener<EventEnvelope> {

        private @Nullable EventEnvelope lastReceived;

        @Override
        public void accept(EventEnvelope envelope) {
            lastReceived = envelope;
        }
    }
}
