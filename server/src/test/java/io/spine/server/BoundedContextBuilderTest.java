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

package io.spine.server;

import com.google.common.collect.ImmutableSet;
import com.google.common.testing.NullPointerTester;
import io.spine.core.TenantId;
import io.spine.server.aggregate.AggregateRepository;
import io.spine.server.aggregate.AggregateRootDirectory;
import io.spine.server.aggregate.InMemoryRootDirectory;
import io.spine.server.bc.given.Given.NoOpCommandDispatcher;
import io.spine.server.bc.given.Given.NoOpEventDispatcher;
import io.spine.server.bc.given.ProjectAggregate;
import io.spine.server.bc.given.ProjectProjection;
import io.spine.server.bus.BusFilter;
import io.spine.server.bus.Listener;
import io.spine.server.commandbus.CommandDispatcher;
import io.spine.server.entity.Repository;
import io.spine.server.event.EventDispatcher;
import io.spine.server.projection.ProjectionRepository;
import io.spine.server.tenant.TenantIndex;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`BoundedContextBuilder` should")
class BoundedContextBuilderTest {

    private BoundedContextBuilder builder;

    @BeforeEach
    void setUp() {
        builder = BoundedContextBuilder.assumingTests(true);
    }

    @Test
    @DisplayName("not allow nulls")
    void notAcceptNulls() {
        new NullPointerTester()
                .testAllPublicInstanceMethods(builder);
    }

    @Nested
    @DisplayName("return")
    class Return {

        @Test
        @DisplayName("name if it was set")
        void name() {
            var nameString = getClass().getName();
            assertEquals(nameString, BoundedContext.singleTenant(nameString)
                                                   .name()
                                                   .getValue());
        }

        @Test
        @DisplayName("`AggregateRootDirectory` if it was set")
        void aggregateRootDirectory() {
            AggregateRootDirectory directory = new InMemoryRootDirectory();
            builder.setAggregateRootDirectory(() -> directory);
            assertEquals(directory, builder.aggregateRootDirectory());
        }
    }

    @Nested
    @DisplayName("if not given custom, create default")
    class CreateDefault {

        @Test
        @DisplayName("`TenantIndex`")
        void tenantIndex() {
            assertNotNull(BoundedContextBuilder.assumingTests(true)
                                               .build()
                                               .internalAccess()
                                               .tenantIndex());
        }

        @Test
        @DisplayName("`CommandBus` and `EventBus` simultaneously")
        void commandBusAndEventBus() {
            var boundedContext = builder.build();
            assertNotNull(boundedContext.commandBus());
            assertNotNull(boundedContext.eventBus());
        }

        @Test
        @DisplayName("`AggregateRootDirectory`")
        void aggregateRootDirectory() {
            var context = builder.build();
            assertNotNull(context.internalAccess()
                                 .aggregateRootDirectory());
        }
    }

    @Test
    @DisplayName("support multitenancy")
    void supportMultitenancy() {
        var builder = BoundedContextBuilder.assumingTests(true);
        assertTrue(builder.isMultitenant());
    }

    @Test
    @DisplayName("allow `TenantIndex` configuration")
    void setTenantIndex() {
        TenantIndex tenantIndex = new StubTenantIndex();

        var builder = BoundedContextBuilder
                .assumingTests()
                .setTenantIndex(tenantIndex);

        assertThat(builder.getTenantIndex())
                .isEqualTo(tenantIndex);
    }

    /**
     * Stub implementation of {@code TenantIndex}.
     */
    private static class StubTenantIndex implements TenantIndex {

        private boolean closed = false;

        @Override
        public void keep(TenantId id) {
            // Do nothing.
        }

        @Override
        public Set<TenantId> all() {
            return ImmutableSet.of();
        }

        @Override
        public boolean isOpen() {
            return !closed;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @Nested
    class Repositories {

        private Repository<?, ?> repository;
        private BoundedContextBuilder builder;

        @BeforeEach
        void setUp() {
            this.builder = BoundedContextBuilder.assumingTests();
            repository = DefaultRepository.of(ProjectAggregate.class);
        }

        @Test
        @DisplayName("add repository")
        void addRepo() {
            assertFalse(builder.hasRepository(repository));

            builder.add(repository);

            assertTrue(builder.hasRepository(repository));
        }

        @Test
        @DisplayName("remove repository")
        void removeRepo() {
            builder.add(repository);
            assertTrue(builder.hasRepository(repository));

            builder.remove(repository);
            assertFalse(builder.hasRepository(repository));
        }

        @Test
        @DisplayName("add default repository for entity class")
        void addByEntityClass() {
            assertFalse(builder.hasRepository(repository));

            builder.add(repository.entityClass());

            assertTrue(builder.hasRepository(repository.entityClass()));
        }

        @Test
        @DisplayName("remove repository by entity class")
        void removeByEntityClass() {
            builder.add(repository);
            assertTrue(builder.hasRepository(repository));

            builder.remove(repository.entityClass());
            assertFalse(builder.hasRepository(repository));
        }
    }

    @Nested
    class CommandDispatchers {

        private BoundedContextBuilder builder;
        private CommandDispatcher dispatcher;
        private AggregateRepository<?, ?, ?> repository;

        @BeforeEach
        void setUp() {
            builder = BoundedContextBuilder.assumingTests();
            dispatcher = new NoOpCommandDispatcher();
            repository =
                    (AggregateRepository<?, ?, ?>) DefaultRepository.of(ProjectAggregate.class);
        }

        @Test
        @DisplayName("add command dispatcher")
        void addDispatcher() {
            assertFalse(builder.hasCommandDispatcher(dispatcher));

            builder.addCommandDispatcher(dispatcher);

            assertTrue(builder.hasCommandDispatcher(dispatcher));
        }

        @Test
        @DisplayName("remove command dispatcher")
        void removeDispatcher() {
            builder.addCommandDispatcher(dispatcher);
            assertTrue(builder.hasCommandDispatcher(dispatcher));

            builder.removeCommandDispatcher(dispatcher);
            assertFalse(builder.hasCommandDispatcher(dispatcher));
        }

        @Test
        @DisplayName("register repository if it's passed as a command dispatcher")
        void registerRepo() {
            assertFalse(builder.hasRepository(repository));
            builder.addCommandDispatcher(repository);
            assertTrue(builder.hasRepository(repository));
        }

        @Test
        @DisplayName("remove registered repository if it's passed as a command dispatcher")
        void removeRegisteredRepo() {
            builder.add(repository);
            assertTrue(builder.hasRepository(repository));

            builder.removeCommandDispatcher(repository);
            assertFalse(builder.hasRepository(repository));
        }

        @Test
        @DisplayName("check repository presence if it's queried as a command dispatcher")
        void checkHasRepo() {
            builder.add(repository);
            assertTrue(builder.hasCommandDispatcher(repository));
        }
    }

    @Nested
    class EventDispatchers {

        private BoundedContextBuilder builder;
        private EventDispatcher dispatcher;
        private ProjectionRepository<?, ?, ?> repository;

        @BeforeEach
        void setUp() {
            builder = BoundedContextBuilder.assumingTests();
            dispatcher = new NoOpEventDispatcher();
            repository =
                    (ProjectionRepository<?, ?, ?>) DefaultRepository.of(ProjectProjection.class);
        }

        @Test
        @DisplayName("add event dispatcher")
        void addDispatcher() {
            assertFalse(builder.hasEventDispatcher(dispatcher));

            builder.addEventDispatcher(dispatcher);

            assertTrue(builder.hasEventDispatcher(dispatcher));
        }

        @Test
        @DisplayName("remove event dispatcher")
        void removeDispatcher() {
            builder.addEventDispatcher(dispatcher);
            assertTrue(builder.hasEventDispatcher(dispatcher));

            builder.removeEventDispatcher(dispatcher);
            assertFalse(builder.hasEventDispatcher(dispatcher));
        }

        @Test
        @DisplayName("register repository if it's passed as event dispatcher")
        void registerRepo() {
            assertFalse(builder.hasRepository(repository));
            builder.addEventDispatcher(repository);
            assertTrue(builder.hasRepository(repository));
        }

        @Test
        @DisplayName("remove registered repository if it's passed as event dispatcher")
        void removeRegisteredRepo() {
            builder.add(repository);
            assertTrue(builder.hasRepository(repository));

            builder.removeEventDispatcher(repository);
            assertFalse(builder.hasRepository(repository));
        }

        @Test
        @DisplayName("check repository presence if it's queried as an event dispatcher")
        void checkHasRepo() {
            builder.add(repository);
            assertTrue(builder.hasEventDispatcher(repository));
        }

    }

    @Nested
    @DisplayName("allow adding filters for")
    class Filters {

        @Test
        @DisplayName("commands")
        void forCommands() {
            BusFilter<CommandEnvelope> filter = command -> Optional.empty();

            builder.addCommandFilter(filter);

            assertTrue(builder.build()
                              .commandBus()
                              .hasFilter(filter));
        }

        @Test
        @DisplayName("events")
        void forEvents() {
            BusFilter<EventEnvelope> filter = event -> Optional.empty();

            builder.addEventFilter(filter);

            assertTrue(builder.build()
                              .eventBus()
                              .hasFilter(filter));
        }
    }

    @Nested
    @DisplayName("allow adding listeners for")
    class Listeners {

        @Test
        @DisplayName("commands")
        void forCommands() {
            Listener<CommandEnvelope> listener = c -> {};

            builder.addCommandListener(listener);

            assertTrue(builder.build()
                              .commandBus()
                              .hasListener(listener));
        }

        @Test
        @DisplayName("events")
        void forEvents() {
            Listener<EventEnvelope> listener = c -> {};

            builder.addEventListener(listener);

            assertTrue(builder.build()
                              .eventBus()
                              .hasListener(listener));
        }
    }
}
