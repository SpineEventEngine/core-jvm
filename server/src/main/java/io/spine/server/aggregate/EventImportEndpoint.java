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

package io.spine.server.aggregate;

import io.spine.core.Event;
import io.spine.logging.WithLogging;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.dispatch.Success;
import io.spine.server.type.EventEnvelope;

import static java.lang.String.format;

/**
 * The endpoint for importing events into aggregates.
 *
 * <p>Importing events one by one uses the same delivery mechanism as in event reaction of
 * aggregates. But unlike for event reaction, only one aggregate can be a target for event
 * being imported.
 *
 * @param <I>
 *         the type of the aggregate IDs
 * @param <A>
 *         the type of the aggregates
 * @see io.spine.server.aggregate.Apply#allowImport()
 */
final class EventImportEndpoint<I, A extends Aggregate<I, ?, ?>>
    extends AggregateEventEndpoint<I, A> implements WithLogging {

    EventImportEndpoint(AggregateRepository<I, A, ?> repository, EventEnvelope event) {
        super(repository, event);
    }

    /**
     * Notifies the repository about the event being imported and returns the enclosed
     * {@link Event} instance.
     *
     * @return the list with one {@code Event} which is being imported
     * @implNote We do not need to perform anything with the aggregate and the passed
     *         event. The aggregate would consume the passed event when dispatching result is
     *         {@link io.spine.server.aggregate.AggregateEndpoint#handleAndApplyEvents(Aggregate)
     *         applied}.
     */
    @Override
    protected DispatchOutcome invokeDispatcher(A aggregate) {
        var event = envelope().outerObject();
        var success = Success.newBuilder();
        success.getProducedEventsBuilder()
               .addEvent(event);
        var outcome = DispatchOutcome.newBuilder()
                .setPropagatedSignal(event.messageId())
                .setSuccess(success)
                .build();
        return outcome;
    }

    @Override
    protected void afterDispatched(I entityId) {
        repository().lifecycleOf(entityId)
                    .onEventImported(envelope().outerObject());
    }

    @Override
    protected void onEmptyResult(A aggregate) {
        logger().atError().log(() -> format(
                "The aggregate `%s` was not modified during the import of the event `%s`.",
                     aggregate, envelope()));
    }
}
