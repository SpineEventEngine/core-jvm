/*
 * Copyright 2023, TeamDev. All rights reserved.
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

package io.spine.client;

import com.google.common.collect.ImmutableList;
import io.spine.base.CommandMessage;
import io.spine.server.BoundedContextBuilder;
import io.spine.test.client.tasks.CTask;
import io.spine.test.client.tasks.CTaskId;
import io.spine.testing.logging.mute.MuteLogging;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static io.spine.client.given.ClientTasksTestEnv.archiveCTask;
import static io.spine.client.given.ClientTasksTestEnv.createCTask;
import static io.spine.client.given.ClientTasksTestEnv.deleteCTask;
import static io.spine.client.given.ClientTasksTestEnv.restoreCTask;
import static io.spine.client.given.ClientTasksTestEnv.stateAfter;
import static io.spine.client.given.ClientTasksTestEnv.unarchiveCTask;
import static io.spine.test.client.ClientTestContext.tasks;
import static java.time.Duration.ofSeconds;

@MuteLogging
@DisplayName("Subscription API for `Client` should allow subscribe to ")
final class ClientSubscriptionsTest extends AbstractClientTest {

    @Override
    protected List<BoundedContextBuilder> contexts() {
        return ImmutableList.of(tasks());
    }

    @DisplayName("changes")
    @Nested
    class Changes {

        @Test
        @DisplayName("in state of an Aggregate")
        void aggregate() {
            var updateReceived = new AtomicBoolean(false);
            var client = client();

            var createTask = createCTask("My task");
            var expectedState = stateAfter(createTask);
            client.asGuest()
                  .subscribeTo(CTask.class)
                  .observe(update -> {
                      updateReceived.set(true);
                      assertThat(update)
                              .comparingExpectedFieldsOnly()
                              .isEqualTo(expectedState);
                  })
                  .post();
            postAndForget(client, createTask);
            sleepUninterruptibly(ofSeconds(1));
            assertThat(updateReceived.get())
                    .isTrue();
        }
    }

    @DisplayName("deletion and restoration")
    @Nested
    class Deletion {

        @Test
        @DisplayName("of an Aggregate")
        void aggregate() {
            List<CTask> updates = new ArrayList<>();
            List<CTaskId> noLongerMatchingIds = new ArrayList<>();

            var client = client();
            var createTask = createCTask("Soon to be deleted and restored");
            var id = createTask.getId();
            var deleteTask = deleteCTask(id);
            var restoreTask = restoreCTask(id);
            client.asGuest()
                  .subscribeTo(CTask.class)
                  .observe(updates::add)
                  .whenNoLongerMatching(CTaskId.class, noLongerMatchingIds::add)
                  .post();

            assertThat(noLongerMatchingIds).isEmpty();
            assertNumOfUpdates(updates, 1, client, createTask);

            assertNumOfUpdates(updates, 1, client, deleteTask);
            // `CTask` became deleted. It should stop matching the subscription criteria.
            assertThat(noLongerMatchingIds).containsExactly(id);

            assertNumOfUpdates(updates, 2, client, restoreTask);
            assertThat(noLongerMatchingIds).containsExactly(id);
        }
    }

    @DisplayName("archiving and un-archiving")
    @Nested
    class Archiving {

        @Test
        @DisplayName("of an Aggregate")
        void aggregate() {
            List<CTask> updates = new ArrayList<>();
            List<CTaskId> noLongerMatchingIds = new ArrayList<>();

            var client = client();
            var createTask = createCTask("Soon to be archived and un-archived");
            var id = createTask.getId();
            var archiveTask = archiveCTask(id);
            var unarchiveCTask = unarchiveCTask(id);
            client.asGuest()
                  .subscribeTo(CTask.class)
                  .observe(updates::add)
                  .whenNoLongerMatching(CTaskId.class, noLongerMatchingIds::add)
                  .post();

            assertThat(noLongerMatchingIds).isEmpty();
            assertNumOfUpdates(updates, 1, client, createTask);

            assertNumOfUpdates(updates, 1, client, archiveTask);
            // `CTask` became archived. It should stop matching the subscription criteria.
            assertThat(noLongerMatchingIds).containsExactly(id);

            assertNumOfUpdates(updates, 2, client, unarchiveCTask);
            assertThat(noLongerMatchingIds).containsExactly(id);
        }
    }

    private static void postAndForget(Client client, CommandMessage command) {
        client.asGuest()
              .command(command)
              .postAndForget();
    }

    private static void assertNumOfUpdates(List<CTask> updates, int expectedCount,
                                           Client client, CommandMessage cmd) {
        postAndForget(client, cmd);
        sleepUninterruptibly(ofSeconds(1));
        assertThat(updates.size())
                .isEqualTo(expectedCount);
    }
}
