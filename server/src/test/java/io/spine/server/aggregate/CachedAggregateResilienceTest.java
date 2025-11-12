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

package io.spine.server.aggregate;

import io.spine.core.Command;
import io.spine.environment.Tests;
import io.spine.server.BoundedContext;
import io.spine.server.ServerEnvironment;
import io.spine.server.aggregate.given.employee.PreparedInboxStorage;
import io.spine.server.aggregate.given.employee.PreparedStorageFactory;
import io.spine.server.delivery.DeliveryStrategy;
import io.spine.server.delivery.ShardIndex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

/**
 * Tests how <i>cached</i> {@code Aggregate} handles the case when one of events,
 * emitted by a command, corrupts the {@code Aggregate}'s state.
 *
 * <p>An {@code Aggregate} is cached when multiple messages are dispatched from `Inbox` at once.
 * Under the hood, they are processed as a "batch", which triggers the aggregate
 * to be cached for their processing.
 *
 * <p>This class uses the custom {@linkplain PreparedInboxStorage InboxStorage} which allow
 * writing messages there directly. This storage is "fed" to the delivery which then is triggered
 * to perform dispatching.
 *
 * @see AbstractAggregateResilienceTest
 * @see AggregateResilienceTest
 */
@DisplayName("Cached resilient `Aggregate` should")
final class CachedAggregateResilienceTest extends AbstractAggregateResilienceTest {

    private final ShardIndex shardIndex = DeliveryStrategy.newIndex(0, 1);
    private PreparedInboxStorage inboxStorage;

    @Override
    @BeforeEach
    void setUp() {
        ServerEnvironment.instance().reset();
        inboxStorage = new PreparedInboxStorage();
        ServerEnvironment.when(Tests.class)
                         .use(PreparedStorageFactory.with(inboxStorage));
        super.setUp();
    }

    @Override
    @AfterEach
    void tearDown() throws Exception {
        ServerEnvironment.instance().reset();
        inboxStorage.close();
        super.tearDown();
    }

    /**
     * This method fills the custom {@linkplain PreparedInboxStorage InboxStorage} with the passed
     * commands and then runs delivery. The commands, dispatched this way, will be processed
     * withing a single "batch" which would cause an {@code Aggregate} to be cached.
     */
    @Override
    void dispatch(List<Command> commands, BoundedContext context) {
        inboxStorage.write(shardIndex, commands);
        ServerEnvironment.instance().delivery().deliverMessagesFrom(shardIndex);
    }
}
