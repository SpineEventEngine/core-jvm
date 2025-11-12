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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.spine.server.projection.model.ProjectionClass.asProjectionClass;
import static io.spine.testing.server.model.ModelTests.dropAllModels;
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

    @AfterEach
    void clearModel() {
        dropAllModels();
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
