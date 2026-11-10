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

package io.spine.server.integration;

import com.google.common.testing.NullPointerTester;
import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.type.EventClass;
import io.spine.test.integration.event.ItgProjectCreated;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.google.common.testing.NullPointerTester.Visibility.PACKAGE;
import static com.google.common.truth.Truth.assertThat;

@DisplayName("`DomesticEventPublisher` should")
class DomesticEventPublisherTest {

    private static final EventClass EVENT_CLASS = EventClass.from(ItgProjectCreated.class);

    private IntegrationBroker broker;
    private BoundedContext context;

    @BeforeEach
    void initBus() {
        context = BoundedContextBuilder.assumingTests().build();
        broker = context.internalAccess()
                        .broker();
    }

    @AfterEach
    void closeContext() throws Exception {
        context.close();
    }

    @Test
    @DisplayName("not accept `null`s on construction")
    void notAcceptNulls() {
        new NullPointerTester()
                .setDefault(IntegrationBroker.class, broker)
                .setDefault(EventClass.class, EVENT_CLASS)
                .testConstructors(DomesticEventPublisher.class, PACKAGE);
    }

    @Test
    @DisplayName("dispatch only one event type")
    void dispatchSingleEvent() {
        var publisher = new DomesticEventPublisher(broker, EVENT_CLASS);
        Set<EventClass> classes = publisher.messageClasses();
        assertThat(classes).containsExactly(EVENT_CLASS);
    }

    @Test
    @DisplayName("dispatch no external events")
    void dispatchNoExternalEvents() {
        var publisher = new DomesticEventPublisher(broker, EVENT_CLASS);
        Set<EventClass> classes = publisher.externalEventClasses();
        assertThat(classes).isEmpty();
    }
}
