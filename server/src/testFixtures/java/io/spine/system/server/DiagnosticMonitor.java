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

package io.spine.system.server;

import com.google.common.collect.ImmutableList;
import io.spine.core.Subscribe;
import io.spine.server.event.AbstractEventSubscriber;

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

/**
 * A subscriber of some diagnostic events.
 *
 * <p>Memoizes all the received events for test purposes.
 */
public final class DiagnosticMonitor extends AbstractEventSubscriber {

    private final List<CannotDispatchDuplicateCommand> duplicateCommands = newArrayList();
    private final List<CannotDispatchDuplicateEvent> duplicateEvents = newArrayList();
    private final List<HandlerFailedUnexpectedly> handlerFailures = newArrayList();
    private final List<RoutingFailed> routingFailures = newArrayList();
    private final List<EventValidationFailed> eventValidationFailures = newArrayList();
    private final List<UnsupportedEventReceived> unsupportedEvents = newArrayList();

    @Subscribe
    void on(EventValidationFailed event) {
        eventValidationFailures.add(event);
    }

    @Subscribe
    void on(UnsupportedEventReceived event) {
        unsupportedEvents.add(event);
    }

    @Subscribe
    void on(CannotDispatchDuplicateCommand event) {
        duplicateCommands.add(event);
    }

    @Subscribe
    void on(CannotDispatchDuplicateEvent event) {
        duplicateEvents.add(event);
    }

    @Subscribe
    void on(HandlerFailedUnexpectedly event) {
        handlerFailures.add(event);
    }

    @Subscribe
    void on(RoutingFailed event) {
        routingFailures.add(event);
    }

    public List<CannotDispatchDuplicateCommand> duplicateCommandEvents() {
        return ImmutableList.copyOf(duplicateCommands);
    }

    public List<CannotDispatchDuplicateEvent> duplicateEventEvents() {
        return ImmutableList.copyOf(duplicateEvents);
    }

    public List<HandlerFailedUnexpectedly> handlerFailureEvents() {
        return ImmutableList.copyOf(handlerFailures);
    }

    public List<RoutingFailed> routingFailures() {
        return ImmutableList.copyOf(routingFailures);
    }

    public List<EventValidationFailed> eventValidationFailures() {
        return ImmutableList.copyOf(eventValidationFailures);
    }

    public List<UnsupportedEventReceived> unsupportedEvents() {
        return ImmutableList.copyOf(unsupportedEvents);
    }
}
