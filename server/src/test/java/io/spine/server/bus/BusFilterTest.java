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

package io.spine.server.bus;

import com.google.common.testing.NullPointerTester;
import io.spine.base.Error;
import io.spine.core.Ack;
import io.spine.core.Status.StatusCase;
import io.spine.server.bus.given.BusFilters;
import io.spine.server.type.CommandEnvelope;
import io.spine.test.bus.ShareId;
import io.spine.test.bus.command.ShareCannotBeTraded;
import io.spine.testing.client.TestActorRequestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Identifier.newUuid;
import static io.spine.core.Status.StatusCase.ERROR;
import static io.spine.core.Status.StatusCase.OK;
import static io.spine.core.Status.StatusCase.REJECTION;
import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.testing.DisplayNames.NOT_ACCEPT_NULLS;

@DisplayName("`BusFilter` should")
class BusFilterTest {

    private CommandEnvelope commandEnvelope;

    @BeforeEach
    void createCommandEnvelope() {
        commandEnvelope = commandEnvelope();
    }

    @Test
    @DisplayName(NOT_ACCEPT_NULLS)
    void passNullToleranceCheck() {
        new NullPointerTester().testAllPublicInstanceMethods(new BusFilters.RejectingWithOk());
    }

    @Test
    @DisplayName("let the message pass the filter")
    void letPass() {
        BusFilter<CommandEnvelope> filter = new BusFilters.Accepting();
        var ack = filter.filter(commandEnvelope);
        assertThat(ack).isEmpty();
    }

    @Test
    @DisplayName("reject the message with the `OK` status")
    void rejectWithOk() {
        BusFilter<CommandEnvelope> filter = new BusFilters.RejectingWithOk();
        var ack = filter.filter(commandEnvelope);
        assertThat(ack).isPresent();

        var theAck = ack.get();
        assertIdEquals(theAck);
        assertStatusEquals(theAck, OK);
    }

    @Test
    @DisplayName("reject the message with an error")
    void rejectWithError() {
        var error = Error.newBuilder()
                .setType(BusFilterTest.class.getCanonicalName())
                .setMessage("Ignore this error.")
                .build();
        BusFilter<CommandEnvelope> filter = new BusFilters.RejectingWithError(error);
        var ack = filter.filter(commandEnvelope);
        assertThat(ack).isPresent();

        var theAck = ack.get();
        assertIdEquals(theAck);
        assertStatusEquals(theAck, ERROR);
        assertThat(theAck.getStatus()
                         .getError()).isEqualTo(error);
    }

    @Test
    @DisplayName("reject the message with a rejection")
    void rejectWithRejectionThrowable() {
        var rejection = ShareCannotBeTraded.newBuilder()
                .setShare(ShareId.newBuilder().setValue(newUuid()).build())
                .setReason("The test rejection.")
                .build();
        BusFilter<CommandEnvelope> filter =
                new BusFilters.RejectingWithRejectionThrowable(rejection);
        var ack = filter.filter(commandEnvelope);
        assertThat(ack).isPresent();

        var theAck = ack.get();
        assertIdEquals(theAck);
        assertStatusEquals(theAck, REJECTION);
        var rejectionMessage = unpack(theAck.getStatus()
                                            .getRejection()
                                            .getMessage());
        assertThat(rejectionMessage).isEqualTo(rejection.messageThrown());
    }

    private void assertIdEquals(Ack ack) {
        var id = unpack(ack.getMessageId());
        assertThat(id).isEqualTo(commandEnvelope.id());
    }

    private static void assertStatusEquals(Ack ack, StatusCase status) {
        assertThat(ack.getStatus().getStatusCase()).isEqualTo(status);
    }

    private static CommandEnvelope commandEnvelope() {
        var requestFactory = new TestActorRequestFactory(BusFilterTest.class);
        var command = requestFactory.generateCommand();
        return CommandEnvelope.of(command);
    }
}
