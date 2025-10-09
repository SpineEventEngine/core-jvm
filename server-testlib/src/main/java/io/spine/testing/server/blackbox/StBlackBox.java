/*
 * Copyright 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.testing.server.blackbox;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.core.Command;
import io.spine.core.Event;
import io.spine.core.TenantId;
import io.spine.server.BoundedContext;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.testing.server.blackbox.probe.CommandCollector;
import io.spine.testing.server.blackbox.probe.EventCollector;

import static io.spine.type.ProtoTexts.shortDebugString;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * Test fixture for single-tenant Bounded Contexts.
 */
final class StBlackBox extends BlackBox {

    StBlackBox(BoundedContext c) {
        super(c);
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
