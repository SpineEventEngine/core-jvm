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

package io.spine.server.stand.given;

import com.google.common.collect.ImmutableSet;
import io.spine.annotation.Internal;
import io.spine.client.ResponseFormat;
import io.spine.client.TargetFilters;
import io.spine.core.EventContext;
import io.spine.core.Subscribe;
import io.spine.server.entity.EntityRecord;
import io.spine.server.projection.Projection;
import io.spine.server.projection.ProjectionRepository;
import io.spine.server.route.EventRoute;
import io.spine.server.route.EventRouting;
import io.spine.test.projection.Project;
import io.spine.test.projection.ProjectId;
import io.spine.test.projection.event.PrjProjectCreated;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.Optional;

import static io.spine.base.Identifier.newUuid;

public class Given {

    private static final String PROJECT_UUID = newUuid();

    private Given() {
    }

    /**
     * An implementation of a projection repository, which allows to
     * {@linkplain StandTestProjectionRepository#setRecords(Iterator) pre-set} the records to return
     * in {@code find...()} and {@code load...()} calls.
     *
     * <p>If the records were not pre-set, the repository behaves as usual.
     */
    public static class StandTestProjectionRepository
            extends ProjectionRepository<ProjectId, StandTestProjection, Project> {

        private @Nullable Iterator<EntityRecord> records;

        private static final EventRoute<ProjectId, PrjProjectCreated> EVENT_TARGETS_FN =
                (msg, ctx) -> ImmutableSet.of(ProjectId.newBuilder()
                                                      .setId(PROJECT_UUID)
                                                      .build());

        public void setRecords(Iterator<EntityRecord> records) {
            this.records = records;
        }

        @Internal
        @Override
        public Iterator<EntityRecord> findRecords(ResponseFormat format) {
            return records == null ? super.findRecords(format) : records;
        }

        @Internal
        @Override
        public Iterator<EntityRecord> findRecords(TargetFilters filters, ResponseFormat format) {
            return records == null ? super.findRecords(filters, format) : records;
        }

        @Override
        public Optional<StandTestProjection> find(ProjectId id) {
            return Optional.of(new StandTestProjection(id));
        }

        @Override
        protected void setupEventRouting(EventRouting<ProjectId> routing) {
            super.setupEventRouting(routing);
            routing.route(PrjProjectCreated.class, EVENT_TARGETS_FN);
        }
    }

    public static final class StandTestProjection
            extends Projection<ProjectId, Project, Project.Builder> {

        public StandTestProjection(ProjectId id) {
            super(id);
        }

        @SuppressWarnings("unused") // OK for test class.
        @Subscribe
        void handle(PrjProjectCreated event, EventContext context) {
            builder().setId(event.getProjectId());
        }
    }
}
