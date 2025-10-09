/*
 * Copyright 2025, TeamDev. All rights reserved.
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

package io.spine.testing.server.blackbox.probe;

import io.spine.base.Identifier;
import io.spine.core.Subscribe;
import io.spine.server.event.AbstractEventSubscriber;
import io.spine.system.server.AggregateHistoryCorrupted;
import io.spine.system.server.CannotDispatchDuplicateCommand;
import io.spine.system.server.CannotDispatchDuplicateEvent;
import io.spine.system.server.ConstraintViolated;
import io.spine.system.server.RoutingFailed;

/**
 * A subscriber for all the diagnostic events.
 *
 * <p>Logs all the received diagnostic events with a meaningful message.
 */
final class DiagnosticLog
        extends AbstractEventSubscriber
        implements DiagnosticLogging {

    private static final DiagnosticLog instance = new DiagnosticLog();

    /**
     * Obtains the only instance of {@code DiagnosticLog}.
     */
    static DiagnosticLog instance() {
        return instance;
    }

    /**
     * Prevents direct instantiation.
     */
    private DiagnosticLog() {
        super();
    }

    @Subscribe
    void on(ConstraintViolated event) {
        var entity = event.getEntity();
        var typeUrl = entity.getTypeUrl();
        var idAsString = Identifier.toString(entity.getId());
        log(event, "The state (type: `%s`) of the entity (ID: `%s`) is invalid.",
            typeUrl, idAsString);
    }

    @Subscribe
    void on(CannotDispatchDuplicateCommand event) {
        var command = event.getDuplicateCommand();
        log(event, "The command `%s` (ID: `%s`) should not be dispatched twice.",
            command.getTypeUrl(),
            command.asCommandId()
                   .getUuid());
    }

    @Subscribe
    void on(CannotDispatchDuplicateEvent event) {
        var duplicateEvent = event.getDuplicateEvent();
        log(event, "Event `%s` (ID: `%s`) should not be dispatched twice.",
            duplicateEvent.getTypeUrl(),
            duplicateEvent.asEventId()
                          .getValue());
    }

    @Subscribe
    void on(RoutingFailed event) {
        log(event, "The signal `%s` could not be routed to `%s`:%n%s%n",
            event.getHandledSignal()
                 .getTypeUrl(),
            event.getEntityType()
                 .getJavaClassName(),
            event.getError()
                 .getMessage());
    }

    @Subscribe
    void on(AggregateHistoryCorrupted event) {
        var aggregate = event.getEntity();
        var idAsString = Identifier.toString(aggregate.getId());
        log(event,
            "Unable to load the history of the aggregate (state type: `%s`, ID: `%s`):%n%s%n",
            aggregate.getTypeUrl(),
            idAsString,
            event.getError()
                 .getMessage());
    }
}
