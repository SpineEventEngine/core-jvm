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

package io.spine.server.commandbus;

import io.spine.core.Command;
import io.spine.server.bus.BusBuilderTest;
import io.spine.server.type.CommandEnvelope;
import io.spine.system.server.NoOpSystemWriteSide;
import io.spine.system.server.SystemWriteSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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

    @Override
    protected CommandBus.Builder builder() {
        return CommandBus.newBuilder()
                         .injectSystem(SYSTEM_WRITE_SIDE);
    }

    @Test
    @DisplayName("create new `CommandBus` instance")
    void createNewInstance() {
        var commandBus = CommandBus.newBuilder()
                .injectSystem(SYSTEM_WRITE_SIDE)
                .build();
        assertNotNull(commandBus);
    }

    @Test
    @DisplayName("not allow to omit setting `SystemWriteSide`")
    void neverOmitSystem() {
        assertThrows(IllegalStateException.class,
                     () -> CommandBus.newBuilder()
                             .build());
    }

    @Nested
    @DisplayName("allow to specify")
    class AllowToSpecify {

        @Test
        @DisplayName("if `CommandBus` is multi-tenant")
        void ifIsMultiTenant() {
            assertTrue(builder().setMultiTenant(true)
                                .build()
                                .isMultiTenant());
            assertFalse(builder().setMultiTenant(false)
                                 .build()
                                 .isMultiTenant());
        }

        @Test
        @DisplayName("system write side")
        void system() {
            SystemWriteSide systemWriteSide = NoOpSystemWriteSide.INSTANCE;
            var builder = builder().injectSystem(systemWriteSide);
            assertTrue(builder.hasSystem());
            assertSame(systemWriteSide, builder.getSystem());
        }
    }
}
