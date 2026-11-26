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

package io.spine.client;

import io.spine.base.CommandMessage;
import io.spine.server.BoundedContextBuilder;
import io.spine.test.client.tasks.CTask;
import io.spine.test.client.tasks.CTaskId;
import io.spine.test.client.tasks.event.CTaskCreated;
import io.spine.testing.logging.mute.MuteLogging;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.client.given.ClientTasksTestEnv.archiveCTask;
import static io.spine.client.given.ClientTasksTestEnv.createCTask;
import static io.spine.test.client.ClientTestContext.tasks;

@MuteLogging
@DisplayName("`TopicRequest` used from Java should")
class TopicRequestJavaSpec extends AbstractClientTest {

    @Override
    protected List<BoundedContextBuilder> contexts() {
        return List.of(tasks());
    }

    @Test
    @DisplayName("subscribe to events and entity states with one subscription")
    void subscribe() {
        List<CTaskCreated> created = new ArrayList<>();
        List<CTask> tasks = new ArrayList<>();
        List<CTaskId> archived = new ArrayList<>();
        var createTask = createCTask("Java task");
        var id = createTask.getId();

        var subscription = client()
                .asGuest()
                .subscribe()
                .event(CTaskCreated.class, t -> t.observe(e -> created.add(e)))
                .state(CTask.class, t -> t.byId(id)
                                          .observe(task -> tasks.add(task))
                                          .whenNoLongerMatching(CTaskId.class, archived::add))
                .post();
        post(createTask);
        post(archiveCTask(id));

        assertThat(created).hasSize(1);
        assertThat(tasks).hasSize(1);
        assertThat(archived).containsExactly(id);
        assertThat(subscription.getTopic().getTargetCount()).isEqualTo(2);
    }

    private void post(CommandMessage command) {
        client().asGuest()
                .command(command)
                .postAndForget();
    }
}
