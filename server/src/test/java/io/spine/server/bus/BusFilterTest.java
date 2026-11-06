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

package io.spine.server.bus;

import com.google.common.testing.NullPointerTester;
import io.spine.core.Ack;
import io.spine.core.ErrorCase;
import io.spine.core.Status.StatusCase;
import io.spine.server.bus.given.BusFilters;
import io.spine.server.type.CommandEnvelope;
import io.spine.testing.client.TestActorRequestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.core.Status.StatusCase.ERROR_CASE;
import static io.spine.core.Status.StatusCase.OK;
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
    @DisplayName("reject the message with an error case")
    void rejectWithErrorCase() {
        BusFilter<CommandEnvelope> filter =
                new BusFilters.RejectingWithErrorCase(ErrorCase.INVALID_REQUEST);
        var ack = filter.filter(commandEnvelope);
        assertThat(ack).isPresent();

        var theAck = ack.get();
        assertIdEquals(theAck);
        assertStatusEquals(theAck, ERROR_CASE);
        assertThat(theAck.getStatus()
                         .getErrorCase()).isEqualTo(ErrorCase.INVALID_REQUEST);
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
