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

package io.spine.server.event;

import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.event.given.bus.GivenEvent;
import io.spine.server.event.given.bus.ProjectAggregate;
import io.spine.server.event.given.bus.RememberingSubscriber;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;

@DisplayName("`EventBus` should manage event enrichment")
class EventBusEnrichmentTest {

    private EventBus eventBus;
    private BoundedContext context;
    private RememberingSubscriber subscriber;

    @BeforeEach
    void setUp() {
        var enricher = EventEnricher.newBuilder().build();
        subscriber = new RememberingSubscriber();
        context = BoundedContextBuilder
                .assumingTests(/* multitenant = */ true)
                .enrichEventsUsing(enricher)
                .addEventDispatcher(subscriber)
                .add(ProjectAggregate.class)
                .build();
        eventBus = context.eventBus();
    }

    @AfterEach
    void closeBoundedContext() throws Exception {
        context.close();
    }

    @Test
    @DisplayName("for event that cannot be enriched")
    void forNonEnrichable() {
        eventBus.post(GivenEvent.projectCreated());

        var eventContext = subscriber.getEventContext();
        assertThat(eventContext.getEnrichment()
                               .getContainer()
                               .getItemsCount())
                .isEqualTo(0);
    }
}
