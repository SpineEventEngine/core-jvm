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

import io.spine.server.BoundedContextBuilder;
import io.spine.test.client.tasks.CTask;
import io.spine.test.client.tasks.event.CTaskCreated;
import io.spine.testing.logging.mute.MuteLogging;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.client.given.ClientTasksTestEnv.createCTask;
import static io.spine.test.client.ClientTestContext.tasks;

@MuteLogging
@DisplayName("`CommandRequest` used from Java should")
class CommandRequestJavaSpec extends AbstractClientTest {

    @Override
    protected List<BoundedContextBuilder> contexts() {
        return List.of(tasks());
    }

    @Test
    @DisplayName("observe events and entity states, calling `observeState()` for the states")
    void observeState() {
        List<CTaskCreated> created = new ArrayList<>();
        List<CTask> tasks = new ArrayList<>();
        var createTask = createCTask("Java task");

        Subscription subscription =
                client().asGuest()
                        .command(createTask)
                        .observe(CTaskCreated.class, e -> created.add(e))
                        .observe(CTaskCreated.class, (e, context) -> created.add(e))
                        .observeState(CTask.class, t -> t.byId(createTask.getId())
                                                         .observe(task -> tasks.add(task)))
                        .post();

        assertThat(created).hasSize(2);
        assertThat(tasks).hasSize(1);
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTopic().getTargetCount()).isEqualTo(2);
    }
}
