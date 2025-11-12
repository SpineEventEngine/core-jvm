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

package io.spine.server.projection.model;

import io.spine.base.EventMessage;
import io.spine.server.entity.given.Given;
import io.spine.server.model.DuplicateReceptorError;
import io.spine.server.model.HandlerFieldFilterClashError;
import io.spine.server.projection.given.SavedString;
import io.spine.server.projection.given.cls.Calcumulator;
import io.spine.server.projection.given.cls.DifferentFieldSubscription;
import io.spine.server.projection.given.cls.DuplicateValueSubscription;
import io.spine.server.projection.given.cls.FilteringProjection;
import io.spine.server.type.EventClass;
import io.spine.string.StringifierRegistry;
import io.spine.string.Stringifiers;
import io.spine.test.projection.ProjectId;
import io.spine.test.projection.event.Int32Imported;
import io.spine.test.projection.event.StringImported;
import io.spine.testing.server.TestEventFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Identifier.newUuid;
import static io.spine.server.projection.given.cls.FilteringProjection.SET_A;
import static io.spine.server.projection.given.cls.FilteringProjection.SET_B;
import static io.spine.server.projection.given.cls.FilteringProjection.VALUE_A;
import static io.spine.server.projection.given.cls.FilteringProjection.VALUE_B;
import static io.spine.server.projection.given.dispatch.ProjectionEventDispatcher.dispatch;
import static io.spine.server.projection.model.ProjectionClass.asProjectionClass;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`ProjectionClass` should")
class ProjectionClassTest {

    private static final TestEventFactory eventFactory =
            TestEventFactory.newInstance(ProjectionClassTest.class);

    /**
     * Registers the stringifier for {@code Integer}, which is used for parsing filter field values.
     */
    @BeforeAll
    static void prepare() {
        StringifierRegistry.instance()
                           .register(Stringifiers.forInteger(), Integer.TYPE);
    }

    @Test
    @DisplayName("return handled event classes")
    void exposeEventClasses() {
        Set<EventClass> classes =
                asProjectionClass(Calcumulator.class).events();

        assertThat(classes).containsExactly(
                EventClass.from(StringImported.class),
                EventClass.from(Int32Imported.class)
        );
    }

    @Nested
    @DisplayName("subscribe to events with specific field values")
    class FilteringSubscription {

        private FilteringProjection projection;

        @BeforeEach
        void createProjection() {
            var id = newId();
            projection = Given.projectionOfClass(FilteringProjection.class)
                              .withId(id.getId())
                              .withVersion(42)
                              .withState(SavedString.getDefaultInstance())
                              .build();
        }

        @Test
        @DisplayName("delivering events")
        void eventDelivery() {
            assertDispatched(event(SET_A), VALUE_A);
            assertDispatched(event(SET_B), VALUE_B);
            var customText = "Pass this to no-filtering handler";
            assertDispatched(event(customText), customText);
        }

        private EventMessage event(String value) {
            return StringImported
                    .newBuilder()
                    .setValue(value)
                    .build();
        }

        private void assertDispatched(EventMessage event, String expectedState) {
            dispatch(projection, eventFactory.createEvent(event));
            var newState = projection.state().getValue();
            assertThat(newState).isEqualTo(expectedState);
        }
    }

    @Test
    @DisplayName("fail on duplicate filter values")
    void failOnDuplicateFilters() {
        assertThrows(
                DuplicateReceptorError.class,
                () -> asProjectionClass(DuplicateValueSubscription.class)
        );
    }

    @Test
    @DisplayName("fail to subscribe to the same event filtering by different fields")
    void failToSubscribeByDifferentFields() {
        assertThrows(
                HandlerFieldFilterClashError.class,
                () -> asProjectionClass(DifferentFieldSubscription.class)
        );
    }

    private static ProjectId newId() {
        return ProjectId.newBuilder()
                .setId(newUuid())
                .build();
    }
}
