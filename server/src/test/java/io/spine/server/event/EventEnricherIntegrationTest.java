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

package io.spine.server.event;

import io.spine.server.BoundedContextBuilder;
import io.spine.server.enrich.given.EitProjectRepository;
import io.spine.server.enrich.given.EitTaskRepository;
import io.spine.server.enrich.given.EitUserRepository;
import io.spine.testing.server.blackbox.BlackBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.spine.server.enrich.given.EitEnricherSetup.createEnricher;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("`Enricher` should")
class EventEnricherIntegrationTest {

    private BlackBox context;

    @BeforeEach
    void setUp() {
        var projects = new EitProjectRepository();
        var tasks = new EitTaskRepository();
        var users = new EitUserRepository();
        var enricher = createEnricher(users, projects, tasks);
        var contextBuilder = BoundedContextBuilder.assumingTests(/* multitenant = */ true)
                .enrichEventsUsing(enricher)
                .add(projects)
                .add(tasks)
                .add(users);
        context = BlackBox.from(contextBuilder);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    @DisplayName("have builder")
    void haveBuilder() {
        assertNotNull(EventEnricher.newBuilder());
    }
}
