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

import io.spine.server.model.contexts.tasks.TasksContext;
import io.spine.test.model.contexts.tasks.TaskId;
import io.spine.test.model.contexts.tasks.rejections.TaskRejections.TaskAlreadyExists;
import io.spine.testing.server.TestEventFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.spine.server.model.contexts.tasks.CreationRetry.retried;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`@Command` on a `Rejection`")
class CommandOnRejectionTest {

    private static final TestEventFactory eventFactory =
            TestEventFactory.newInstance(CommandOnRejectionTest.class);

    @Test
    @DisplayName("with the command context")
    void receive() {
        var proposedId = TaskId.generate();
        try (var context = TasksContext.newInstance()) {
            context.eventBus()
                   .post(eventFactory.createEvent(
                           TaskAlreadyExists.newBuilder()
                                   .setId(proposedId)
                                   .setName("test task name")
                                   .setDescription("test task description")
                                   .build()));
        }
        assertTrue(retried(proposedId));
    }
}
