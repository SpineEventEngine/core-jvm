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

package io.spine.server.model;

import io.spine.base.Identifier;
import io.spine.model.contexts.projects.ProjectId;
import io.spine.model.contexts.projects.command.SigCreateProject;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.command.model.AssigneeSignature;
import io.spine.server.model.given.map.CompletionWatch;
import io.spine.server.model.given.map.DupEventFilterValue;
import io.spine.server.model.given.map.DuplicateCommandHandlers;
import io.spine.server.model.given.map.ProjectAgg;
import io.spine.server.model.given.map.TwoFieldsInSubscription;
import io.spine.string.StringifierRegistry;
import io.spine.string.Stringifiers;
import io.spine.testing.server.blackbox.ContextAwareTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.spine.server.projection.model.ProjectionClass.asProjectionClass;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`HandlerMap` should")
class ReceptorMapTest {

    /**
     * Registers the stringifier for {@code Integer}, which is used for parsing filter field values.
     */
    @BeforeAll
    static void prepare() {
        StringifierRegistry.instance()
                           .register(Stringifiers.forInteger(), Integer.TYPE);
    }

    @Nested
    @DisplayName("not allow")
    class DuplicateHandler {

        @Test
        @DisplayName("duplicate message classes in handlers")
        void rejectDuplicateHandlers() {
            assertDuplicate(() -> ReceptorMap.create(
                    DuplicateCommandHandlers.class, new AssigneeSignature()
            ));
        }

        @Test
        @DisplayName("the same value of the filtered event field (Where)")
        void rejectFilterFieldDuplicationWhere() {
            assertDuplicate(() -> asProjectionClass(DupEventFilterValue.class));
        }

        @Test
        @DisplayName("the same event filtering by different fields")
        void failToSubscribeByDifferentFields() {
            assertThrows(
                    HandlerFieldFilterClashError.class,
                    () -> asProjectionClass(TwoFieldsInSubscription.class)
            );
        }

        void assertDuplicate(Runnable runnable) {
            assertThrows(DuplicateReceptorError.class, runnable::run);
        }
    }

    @Nested
    @DisplayName("gracefully handle a missing handler for a rejection with a specific origin")
    class SpecificRejection extends ContextAwareTest {

        @Override
        protected BoundedContextBuilder contextBuilder() {
            return BoundedContextBuilder
                    .assumingTests()
                    .add(ProjectAgg.class)
                    .addEventDispatcher(new CompletionWatch());
        }

        @Test
        void handleThrownRejectionGracefully() {
            var project = ProjectId.newBuilder()
                    .setId(Identifier.newUuid())
                    .build();
            var createProject = SigCreateProject.newBuilder()
                    .setId(project)
                    .build();
            assertDoesNotThrow(() -> context().receivesCommand(createProject));
        }
    }
}
