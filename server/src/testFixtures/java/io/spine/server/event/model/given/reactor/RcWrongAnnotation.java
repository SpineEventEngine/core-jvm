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

package io.spine.server.event.model.given.reactor;

import io.spine.core.Subscribe;
import io.spine.server.command.Command;
import io.spine.test.reflect.event.RefProjectCreated;
import io.spine.test.reflect.event.RefProjectStarted;

/**
 * Provides a method that has correct parameter and return value, but wrong annotations.
 */
public final class RcWrongAnnotation extends TestEventReactor {

    @Command
    @Subscribe
    RefProjectStarted react(RefProjectCreated event) {
        return RefProjectStarted.newBuilder()
                                .setProjectId(event.getProjectId())
                                .build();
    }
}
