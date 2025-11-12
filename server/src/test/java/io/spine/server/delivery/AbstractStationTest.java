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

package io.spine.server.delivery;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Timestamp;
import io.spine.test.delivery.Calc;
import io.spine.test.delivery.DCounter;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;

import static com.google.common.truth.Truth.assertThat;
import static com.google.protobuf.util.Timestamps.compare;
import static java.util.stream.Collectors.toSet;

/**
 * An abstract base for the tests of {@link Station}s.
 */
@SuppressWarnings("OverloadedVarargsMethod")    // OK for the tests.
abstract class AbstractStationTest {

    static final String targetOne = "first-catching-up-target-ID";
    static final String targetTwo = "catching-up-target-ID";
    static final TypeUrl type = TypeUrl.of(DCounter.class);
    static final TypeUrl anotherType = TypeUrl.of(Calc.class);

    /**
     * Creates a new station of the tested type with the specified delivery action.
     */
    abstract Station newStation(DeliveryAction action);

    @Test
    @DisplayName("do nothing on an empty conveyor")
    void doNothingOnEmptyConveyor() {
        var action = new MemoizingAction();
        var station = newStation(action);
        var emptyConveyor = new Conveyor(new ArrayList<>(), new DeliveredMessagesCache());

        var result = station.process(emptyConveyor);
        assertDeliveredCount(result, 0);

        assertThat(action.passedMessages()).isNull();
    }

    static void assertContainsExactly(Iterator<InboxMessage> actual,
                                      InboxMessage... expected) {
        assertThat(ImmutableSet.copyOf(actual))
                .containsExactlyElementsIn(ImmutableSet.copyOf(expected));
    }

    static void assertContainsExactly(Collection<InboxMessage> actual,
                                      InboxMessage... expected) {
        assertThat(ImmutableSet.copyOf(actual))
                .containsExactlyElementsIn(ImmutableSet.copyOf(expected));
    }

    static void assertContainsExactly(Stream<InboxMessage> actual,
                                      InboxMessage... expected) {
        assertThat(actual.collect(toSet()))
                .containsExactlyElementsIn(ImmutableSet.copyOf(expected));
    }

    static void assertDeliveredCount(Station.Result result, int howMany) {
        assertThat(result.deliveredCount()).isEqualTo(howMany);
        assertThat(result.errors()
                         .hasErrors()).isFalse();
    }

    static void assertKeptForLonger(InboxMessageId id,
                                    Map<InboxMessageId, InboxMessage> remaindersById) {
        var message = remaindersById.get(id);
        assertThat(
                compare(message.getKeepUntil(), message.getWhenReceived())
        ).isGreaterThan(0);
    }

    static void assertNotKeptForLonger(InboxMessageId id,
                                    Map<InboxMessageId, InboxMessage> remaindersById) {
        var message = remaindersById.get(id);
        assertThat(message.getKeepUntil()).isEqualTo(Timestamp.getDefaultInstance());
    }
}
