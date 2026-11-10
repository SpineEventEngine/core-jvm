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

package io.spine.system.server;

import io.spine.server.BoundedContextBuilder;
import io.spine.system.server.event.EntityCreated;
import io.spine.system.server.given.entity.HistoryEventWatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static io.spine.system.server.SystemBoundedContexts.systemOf;
import static io.spine.system.server.given.SystemContextSettingsTestEnv.entityCreated;
import static io.spine.system.server.given.SystemContextSettingsTestEnv.entityStateChanged;
import static io.spine.system.server.given.SystemContextSettingsTestEnv.postSystemEvent;
import static io.spine.testing.server.TestContexts.buildForTests;
import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("System Bounded Context should")
class SystemContextSettingsTest {

    @Nested
    @DisplayName("by default")
    class ByDefault {

        @Test
        @DisplayName("not store events")
        void notStoreEvents() {
            var domain = buildForTests(BoundedContextBuilder.assumingTests());
            var system = systemOf(domain);
            var event = entityStateChanged();
            var observer = postSystemEvent(system.eventBus(), event);
            assertTrue(observer.isCompleted());
            assertThat(observer.responses()).isEmpty();
        }

        @Test
        @DisplayName("not store domain commands")
        void notStoreDomainCommands() {
            var domain = buildForTests(BoundedContextBuilder.assumingTests());
            var system = systemOf(domain);
            assertFalse(system.hasEntitiesWithState(CommandLog.class));
        }

        @Test
        @DisplayName("post system events in parallel")
        void postEventsInParallel() {
            var domain = buildForTests(BoundedContextBuilder.assumingTests());
            var system = systemOf(domain);
            var watcher = new HistoryEventWatcher();
            system.eventBus().register(watcher);

            var event = entityCreated();
            domain.systemClient()
                  .writeSide()
                  .postEvent(event);

            sleepUninterruptibly(ofSeconds(1));
            watcher.assertReceivedEvent(EntityCreated.class);
        }
    }

    @Nested
    @DisplayName("if required")
    class IfRequired {

        @Test
        @DisplayName("store events")
        void storeEvents() {
            var contextBuilder = BoundedContextBuilder.assumingTests();
            contextBuilder.systemSettings()
                          .persistEvents();
            var domain = buildForTests(contextBuilder);
            var system = systemOf(domain);
            var event = entityStateChanged();
            var observer = postSystemEvent(system.eventBus(), event);
            assertTrue(observer.isCompleted());
            assertThat(observer.responses()).containsExactly(event);
        }

        @Test
        @DisplayName("store domain commands")
        void storeDomainCommands() {
            var contextBuilder = BoundedContextBuilder.assumingTests();
            contextBuilder.systemSettings()
                          .enableCommandLog();
            var domain = buildForTests(contextBuilder);
            var system = systemOf(domain);
            assertTrue(system.hasEntitiesWithState(CommandLog.class));
        }

        @Test
        @DisplayName("post system events directly in the current thread")
        void postEventsInCurrentThread() {
            var contextBuilder = BoundedContextBuilder.assumingTests();
            contextBuilder.systemSettings().disableParallelPosting();
            var domain = buildForTests(contextBuilder);
            var system = systemOf(domain);
            var watcher = new HistoryEventWatcher();
            system.eventBus().register(watcher);

            var event = entityCreated();
            domain.systemClient()
                  .writeSide()
                  .postEvent(event);

            watcher.assertReceivedEvent(EntityCreated.class);
        }

        @Test
        @DisplayName("post system events using the passed `Executor`")
        void postEventsWithExecutor() {
            var calls = new AtomicInteger();
            var executor = (Executor) (command) -> {
                calls.incrementAndGet();
                command.run();
            };

            var contextBuilder = BoundedContextBuilder.assumingTests();
            contextBuilder.systemSettings()
                          .enableParallelPosting()
                          .useCustomPostingExecutor(executor);

            var domain = buildForTests(contextBuilder);
            var system = systemOf(domain);
            var watcher = new HistoryEventWatcher();
            system.eventBus().register(watcher);

            var event = entityCreated();
            domain.systemClient()
                  .writeSide()
                  .postEvent(event);

            watcher.assertReceivedEvent(EntityCreated.class);
            assertThat(calls.get()).isEqualTo(1);
        }
    }
}
