/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.server.aggregate.given.repo;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Any;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.Identifier;
import io.spine.core.Origin;
import io.spine.server.Identity;
import io.spine.server.aggregate.AggregateRepository;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.EntityRecordChange;
import io.spine.server.entity.storage.EntityEventStorage;
import io.spine.server.route.EventRouting;
import io.spine.test.aggregate.AggProject;
import io.spine.test.aggregate.ProjectId;
import io.spine.test.aggregate.event.AggProjectArchived;
import io.spine.test.aggregate.event.AggProjectDeleted;

import static io.spine.protobuf.AnyPacker.pack;

/**
 * The repository of {@linkplain ProjectAggregate aggregates} used in positive scenarios.
 */
public class ProjectAggregateRepository
        extends AggregateRepository<ProjectId, ProjectAggregate, AggProject> {

    @Override
    protected void setupEventRouting(EventRouting<ProjectId> routing) {
        super.setupEventRouting(routing);
        routing.route(AggProjectArchived.class,
                      (msg, ctx) -> ImmutableSet.copyOf(msg.getChildProjectIdList()))
               .route(AggProjectDeleted.class,
                      (msg, ctx) -> ImmutableSet.copyOf(msg.getChildProjectIdList()));

    }

    void storeAggregate(ProjectAggregate aggregate) {
        store(aggregate);
        postStateUpdate(aggregate);
    }

    private void postStateUpdate(ProjectAggregate aggregate) {
        var id = Identifier.pack(aggregate.id());
        var state = pack(aggregate.state());
        var previousRecord = EntityRecord.newBuilder()
                .setEntityId(id)
                .setState(Any.getDefaultInstance())
                .build();
        var newRecord = previousRecord.toBuilder()
                .setEntityId(id)
                .setState(state)
                .build();
        var change = EntityRecordChange.newBuilder()
                .setPreviousValue(previousRecord)
                .setNewValue(newRecord)
                .build();
        var origin = Identity.byString("some-random-origin");
        lifecycleOf(aggregate.id())
                .onStateChanged(change, ImmutableSet.of(origin), Origin.getDefaultInstance());
    }

    /** Enables the double-dispatch guard, exposing the protected opt-in for tests. */
    @VisibleForTesting
    public void enableGuard() {
        useDoubleDispatchGuard();
    }

    /** Tells whether the double-dispatch guard is enabled, exposing the query for tests. */
    @VisibleForTesting
    public boolean guardEnabled() {
        return doubleDispatchGuardEnabled();
    }

    /** Exposes the event-history depth to the tests. */
    @Override
    public int eventHistoryDepth() {
        return super.eventHistoryDepth();
    }

    /** Exposes the event-history depth setter to the tests. */
    @Override
    public void setEventHistoryDepth(int depth) {
        super.setEventHistoryDepth(depth);
    }

    /** Exposes the event journal to the tests. */
    @VisibleForTesting
    public EntityEventStorage<ProjectId> journal() {
        return eventStorage();
    }
}
