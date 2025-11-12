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

import io.spine.environment.Tests;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.Delivery;
import io.spine.server.delivery.InboxStorage;
import io.spine.server.entity.Entity;
import io.spine.server.model.given.filter.CreateProjectCommander;
import io.spine.server.model.given.filter.CreateProjectEventCommander;
import io.spine.server.model.given.filter.ModSplitAggregate;
import io.spine.server.model.given.filter.ModSplitEventAggregate;
import io.spine.server.model.given.filter.ProjectCreatedReactor;
import io.spine.server.model.given.filter.ProjectCreatedSubscriber;
import io.spine.server.model.given.filter.ProjectTasksRepository;
import io.spine.server.model.given.filter.ProjectTasksSubscriber;
import io.spine.server.model.given.storage.ModelTestStorageFactory;
import io.spine.server.storage.StorageFactory;
import io.spine.server.storage.memory.InMemoryStorageFactory;
import io.spine.test.model.ModProjectCreated;
import io.spine.testing.server.blackbox.BlackBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Identifier.newUuid;
import static io.spine.testing.server.model.ModelTests.dropAllModels;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("Handler methods with field filters should")
class FilterTest {

    @AfterEach
    void clearModel() {
        dropAllModels();
    }

    @Nested
    @DisplayName("be OK on a")
    class Accept {

        @Test
        @DisplayName("`@Subscribe`-r")
        void subscribe() {
            assertValid(ProjectCreatedSubscriber.class);
        }

        @Test
        @DisplayName("`@React`-or")
        void react() {
            assertValid(ProjectCreatedReactor.class);
        }

        @Test
        @DisplayName("event-accepting `@Command`-er")
        void eventCommand() {
            assertValid(CreateProjectEventCommander.class);
        }
    }

    @Nested
    @DisplayName("be rejected in a")
    class Rejected {

        @Test
        @DisplayName("`@Assign`-ed command-handling method")
        void assign() {
            assertInvalid(ModSplitAggregate.class);
        }

        @Test
        @DisplayName("command-accepting `@Command`-er")
        void commandCommand() {
            assertInvalid(CreateProjectCommander.class);
        }

        @Test
        @DisplayName("`@Apply`-er")
        void apply() {
            assertInvalid(ModSplitEventAggregate.class);
        }


        @Test
        @DisplayName("be not acceptable for a state `@Subscribe`-r method")
        void state() {
            assertInvalid(ProjectTasksSubscriber.class);
        }
    }


    @Nested
    @DisplayName("filter out events before they hit inbox")
    class DeliveryTest {

        private StorageFactory storageFactory;
        private InboxStorage inboxStorage;

        @BeforeEach
        void prepareEnv() {
            storageFactory = new ModelTestStorageFactory(InMemoryStorageFactory.newInstance());
            inboxStorage = storageFactory.createInboxStorage(false);
            var delivery = Delivery.newBuilder()
                    .setInboxStorage(inboxStorage)
                    .build();
            ServerEnvironment.when(Tests.class)
                             .use(delivery);
        }

        @AfterEach
        void closeEnv() throws Exception {
            storageFactory.close();
            ServerEnvironment.instance().reset();
            dropAllModels();
        }

        @Test
        void beforeDelivery() {
            var context = BoundedContextBuilder
                    .assumingTests()
                    .add(new ProjectTasksRepository());
            var event = ModProjectCreated.newBuilder()
                    .setId(newUuid()) // Not the value expected in `ProjectTasksProjection`.
                    .build();
            BlackBox.from(context)
                    .receivesEvent(event);
            assertThat(newArrayList(inboxStorage.index()))
                 .isEmpty();
        }
    }

    private static void assertValid(Class<?> classWithHandler) {
        try {
            triggerModelConstruction(classWithHandler);
        } catch (Throwable e) {
            fail(e);
        }
    }

    private static void assertInvalid(Class<?> classWithHandler) {
        assertThrows(ModelError.class, () -> triggerModelConstruction(classWithHandler));
    }

    @SuppressWarnings({
            "ClassNewInstance", // We don't care about custom exceptions.
            "ResultOfMethodCallIgnored" // Call for side effect.
            , "deprecation"})
    private static void triggerModelConstruction(Class<?> modelClass)
            throws InstantiationException, IllegalAccessException {
        var instance = modelClass.newInstance();
        if (Entity.class.isAssignableFrom(modelClass)) {
            ((Entity<?, ?>) instance).modelClass();
        }
    }
}
