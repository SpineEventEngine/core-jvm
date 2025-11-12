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

package io.spine.system.server;

import io.spine.environment.Environment;
import io.spine.environment.DefaultMode;
import io.spine.environment.Tests;
import io.spine.server.given.environment.Local;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DisplayName("`SystemSettings` should")
class SystemSettingsTest {

    private final Environment env = Environment.instance();

    @AfterEach
    void resetEnv() {
        Environment.instance()
                   .reset();
    }

    @Nested
    @DisplayName("by default")
    class ByDefault {

        @Test
        @DisplayName("disable command log")
        void disableCommandLog() {
            var settings = SystemSettings.defaults();
            assertFalse(settings.includeCommandLog());
        }

        @Test
        @DisplayName("disable event store")
        void disableEventStore() {
            var settings = SystemSettings.defaults();
            assertFalse(settings.includePersistentEvents());
        }

        @Test
        @DisplayName("disallow parallel posting of system events in the `Test` environment")
        void disallowParallelPostingInTestEnv() {
            assumeTrue(env.is(Tests.class));
            var settings = SystemSettings.defaults();
            assertFalse(settings.postEventsInParallel());
        }

        @Nested
        @DisplayName("allow parallel posting of system events")
        class AllowParallelPosting {

            @Test
            @DisplayName("in the `Production` environment")
            void inProductionEnv() {
                env.setTo(DefaultMode.class);
                var settings = SystemSettings.defaults();
                assertTrue(settings.postEventsInParallel());
            }

            @Test
            @DisplayName("in a custom environment")
            void inCustomEnv() {
                env.setTo(Local.class);
                var settings = SystemSettings.defaults();
                assertTrue(settings.postEventsInParallel());
            }
        }
    }

    @Nested
    @DisplayName("configure")
    class Configure {

        @Test
        @DisplayName("command log")
        void commandLog() {
            var settings = SystemSettings.defaults();
            settings.enableCommandLog();
            assertTrue(settings.includeCommandLog());
        }

        @Test
        @DisplayName("event store")
        void eventStore() {
            var settings = SystemSettings.defaults();
            settings.persistEvents();
            assertTrue(settings.includePersistentEvents());
        }

        @Nested
        @DisplayName("system events to be posted")
        class SystemEventsPosted {

            @Test
            @DisplayName("directly in the current thread")
            void usingCurrentThread() {
                env.setTo(DefaultMode.class);
                var settings = SystemSettings.defaults();
                assumeTrue(settings.postEventsInParallel());

                settings.disableParallelPosting();
                assertFalse(settings.postEventsInParallel());
            }

            @Test
            @DisplayName("using the passed `Executor`")
            void usingPassedExecutor() {
                env.setTo(DefaultMode.class);
                var settings = SystemSettings.defaults();
                assumeTrue(settings.postEventsInParallel());
                assertDefaultExecutor(settings);

                var executor = (Executor) command -> { };
                settings.useCustomPostingExecutor(executor);
                var newExecutor = settings.freeze().postingExecutor();
                assertSame(newExecutor, executor);
            }

            @Test
            @DisplayName("using the default `Executor`")
            void usingDefaultExecutor() {
                env.setTo(DefaultMode.class);
                var executor = (Executor) command -> { };
                var settings = SystemSettings.defaults();
                assumeTrue(settings.postEventsInParallel());
                settings.useCustomPostingExecutor(executor);
                var currentExecutor = settings.freeze().postingExecutor();
                assertSame(currentExecutor, executor);

                settings.useDefaultPostingExecutor();
                assertDefaultExecutor(settings);
            }
        }
    }

    @Nested
    @DisplayName("not configure posting executor")
    class NotConfigurePostingExecutor {

        @Test
        @DisplayName("when parallel posting is disabled")
        void whenParallelPostingDisabled() {
            env.setTo(Tests.class);
            var executor = (Executor) command -> { };
            var settings = SystemSettings.defaults();
            assertFalse(settings.postEventsInParallel());
            assertThrowsExactly(
                    IllegalStateException.class,
                    () -> settings.useCustomPostingExecutor(executor)
            );
        }
    }

    private static void assertDefaultExecutor(SystemSettings settings) {
        var postingExecutor = settings.freeze().postingExecutor();
        assertEquals(postingExecutor.getClass(), ForkJoinPool.class);
    }
}
