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

package io.spine.server.commandbus;

import io.spine.core.Command;
import io.spine.server.BoundedContext;
import io.spine.server.bus.BusBuilderTest;
import io.spine.server.tenant.TenantIndex;
import io.spine.server.type.CommandEnvelope;
import io.spine.system.server.NoOpSystemWriteSide;
import io.spine.system.server.SystemWriteSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.spine.testing.TestValues.nullRef;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("DuplicateStringLiteralInspection") // Common test display names.
@DisplayName("`CommandBus.Builder` should")
class CommandBusBuilderTest
        extends BusBuilderTest<CommandBus.Builder, CommandEnvelope, Command> {

    private static final SystemWriteSide SYSTEM_WRITE_SIDE = NoOpSystemWriteSide.INSTANCE;

    private TenantIndex tenantIndex;
    private BoundedContext context;

    @Override
    protected CommandBus.Builder builder() {
        return CommandBus.newBuilder()
                         .injectContextSpec(context.spec())
                         .injectSystem(SYSTEM_WRITE_SIDE)
                         .injectTenantIndex(tenantIndex);
    }

    @BeforeEach
    void setUp() {
        context =
                BoundedContext.multitenant(getClass().getSimpleName())
                              .build();
        tenantIndex = context.internalAccess()
                             .tenantIndex();
    }

    @Test
    @DisplayName("create new `CommandBus` instance")
    void createNewInstance() {
        var commandBus = CommandBus.newBuilder()
                .injectContextSpec(context.spec())
                .injectTenantIndex(tenantIndex)
                .injectSystem(SYSTEM_WRITE_SIDE)
                .build();
        assertNotNull(commandBus);
    }
    }

    @Test
    @DisplayName("not accept `null` `CommandStore`")
    void notAcceptNullCommandStore() {
        assertThrows(NullPointerException.class,
                     () -> builder().injectTenantIndex(nullRef()));
    }

    @Test
    @DisplayName("not allow to omit setting `CommandStore`")
    void neverOmitCommandStore() {
        assertThrows(IllegalStateException.class,
                     () -> CommandBus.newBuilder()
                             .injectContextSpec(context.spec())
                             .injectSystem(SYSTEM_WRITE_SIDE)
                             .build());
    }

    @Test
    @DisplayName("not allow to omit setting `SystemWriteSide`")
    void neverOmitSystem() {
        assertThrows(IllegalStateException.class,
                     () -> CommandBus.newBuilder()
                             .injectContextSpec(context.spec())
                             .injectTenantIndex(tenantIndex)
                             .build());
    }

    @Test
    @DisplayName("not allow to omit setting `ContextSpec`")
    void neverOmitContextSpec() {
        assertThrows(IllegalStateException.class,
                     () -> CommandBus.newBuilder()
                             .injectSystem(SYSTEM_WRITE_SIDE)
                             .injectTenantIndex(tenantIndex)
                             .build());
    }

    @Nested
    @DisplayName("allow to specify")
    class AllowToSpecify {

        @Test
        @DisplayName("system write side")
        void system() {
            SystemWriteSide systemWriteSide = NoOpSystemWriteSide.INSTANCE;
            var builder = builder().injectSystem(systemWriteSide);
            assertTrue(builder.hasSystem());
            assertSame(systemWriteSide, builder.getSystem());
        }

        @Test
        @DisplayName("tenant index")
        void tenantIndex() {
            var index = TenantIndex.singleTenant();
            var builder = builder().injectTenantIndex(index);
            assertTrue(builder.hasTenantIndex());
            assertSame(index, builder.getTenantIndex());
        }
    }

    @Nested
    @DisplayName("inherit multitenancy from context spec")
    class InheritMultitenancy {

        @Test
        @DisplayName("when context is multitenant")
        void multitenantContext() {
            var mtContext = BoundedContext.multitenant("MultitenantTest").build();
            var mtTenantIndex = mtContext.internalAccess().tenantIndex();
            var commandBus = CommandBus.newBuilder()
                    .injectContextSpec(mtContext.spec())
                    .injectTenantIndex(mtTenantIndex)
                    .injectSystem(SYSTEM_WRITE_SIDE)
                    .build();
            assertTrue(commandBus.isMultitenant());
        }

        @Test
        @DisplayName("when context is single-tenant")
        void singleTenantContext() {
            var stContext = BoundedContext.singleTenant("SingleTenantTest").build();
            var stTenantIndex = stContext.internalAccess().tenantIndex();
            var commandBus = CommandBus.newBuilder()
                    .injectContextSpec(stContext.spec())
                    .injectTenantIndex(stTenantIndex)
                    .injectSystem(SYSTEM_WRITE_SIDE)
                    .build();
            assertFalse(commandBus.isMultitenant());
        }
    }
}
