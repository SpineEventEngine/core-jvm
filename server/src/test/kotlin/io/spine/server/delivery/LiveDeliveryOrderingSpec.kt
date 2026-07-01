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

package io.spine.server.delivery

import io.kotest.matchers.shouldBe
import io.spine.core.Event
import io.spine.core.EventId
import io.spine.server.delivery.InboxLabel.IMPORT_EVENT
import io.spine.server.delivery.InboxLabel.REACT_UPON_EVENT
import io.spine.server.delivery.InboxLabel.UPDATE_SUBSCRIBER
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests the delivery ordering performed by
 * [LiveDeliveryStation.subscribersBeforeReactors], which guarantees that an event reaches
 * its subscribers before its reactors within a delivery page.
 *
 * See the [issue #925](https://github.com/SpineEventEngine/core-java/issues/925).
 */
@DisplayName("`LiveDeliveryStation` when ordering a delivery page should")
internal class LiveDeliveryOrderingSpec {

    @Test
    @DisplayName("deliver an event to a subscriber before a reactor of the same origin")
    fun subscriberBeforeReactor() {
        val subscriber = message("A", UPDATE_SUBSCRIBER)
        val reactor = message("A", REACT_UPON_EVENT)

        val ordered = LiveDeliveryStation.subscribersBeforeReactors(listOf(reactor, subscriber))

        ordered shouldBe listOf(subscriber, reactor)
    }

    @Test
    @DisplayName("keep the order when the subscriber already precedes the reactor")
    fun keepSubscriberFirst() {
        val subscriber = message("A", UPDATE_SUBSCRIBER)
        val reactor = message("A", REACT_UPON_EVENT)

        val ordered = LiveDeliveryStation.subscribersBeforeReactors(listOf(subscriber, reactor))

        ordered shouldBe listOf(subscriber, reactor)
    }

    @Test
    @DisplayName("group messages by origin, keeping the first-seen order of the groups")
    fun groupByOrigin() {
        val reactorA = message("A", REACT_UPON_EVENT)
        val reactorB = message("B", REACT_UPON_EVENT)
        val subscriberA = message("A", UPDATE_SUBSCRIBER)
        val subscriberB = message("B", UPDATE_SUBSCRIBER)

        val ordered = LiveDeliveryStation.subscribersBeforeReactors(
            listOf(reactorA, reactorB, subscriberA, subscriberB)
        )

        // Group `A` is seen first, group `B` second; within each, the subscriber goes first.
        ordered shouldBe listOf(subscriberA, reactorA, subscriberB, reactorB)
    }

    @Test
    @DisplayName("treat only subscriber messages as preceding, keeping other labels after them")
    fun onlySubscribersPrecede() {
        val importing = message("A", IMPORT_EVENT)
        val subscriber = message("A", UPDATE_SUBSCRIBER)
        val reactor = message("A", REACT_UPON_EVENT)

        val ordered = LiveDeliveryStation.subscribersBeforeReactors(
            listOf(importing, reactor, subscriber)
        )

        // The subscriber is promoted; the rest keep their relative order.
        ordered shouldBe listOf(subscriber, importing, reactor)
    }

    @Test
    @DisplayName("return a single message unchanged")
    fun singleMessage() {
        val reactor = message("A", REACT_UPON_EVENT)

        val ordered = LiveDeliveryStation.subscribersBeforeReactors(listOf(reactor))

        ordered shouldBe listOf(reactor)
    }

    /**
     * Creates a minimal [InboxMessage] carrying an event with the given [origin] identifier
     * and the given delivery [label].
     *
     * The message is built with [buildPartial][InboxMessage.Builder.buildPartial] on purpose:
     * only the origin event ID and the label are relevant for the ordering under test, so the
     * other required fields of [InboxMessage] and [Event] are intentionally left unset.
     */
    private fun message(origin: String, label: InboxLabel): InboxMessage =
        InboxMessage.newBuilder()
            .setEvent(event(origin))
            .setLabel(label)
            .buildPartial()

    private fun event(id: String): Event =
        Event.newBuilder()
            .setId(EventId.newBuilder().setValue(id))
            .buildPartial()
}
