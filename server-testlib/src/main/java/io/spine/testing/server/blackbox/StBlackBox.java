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

package io.spine.testing.server.blackbox;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.core.Command;
import io.spine.core.Event;
import io.spine.core.TenantId;
import io.spine.server.Application;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.testing.server.blackbox.probe.CommandCollector;
import io.spine.testing.server.blackbox.probe.EventCollector;

import static io.spine.type.ProtoTexts.shortDebugString;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * Test fixture for single-tenant Bounded Contexts.
 */
final class StBlackBox extends BlackBox {

    StBlackBox(Application application) {
        super(application);
    }

    @Override
    ImmutableList<Command> select(CommandCollector collector) {
        return collector.all();
    }

    @Override
    ImmutableList<Event> select(EventCollector collector) {
        return collector.all();
    }

    @Override
    @CanIgnoreReturnValue
    public BlackBox withTenant(TenantId tenant) {
        throw newIllegalStateException(
                "The context `%s` is single-tenant and" +
                        " cannot operate with the passed tenant ID `%s`.",
                name().value(), shortDebugString(tenant)
        );
    }

    @Override
    @SuppressWarnings("TestOnlyProblems")   /* `TestActorRequestFactory` is not test-only. */
    TestActorRequestFactory requestFactory() {
        return actor().requests();
    }
}
