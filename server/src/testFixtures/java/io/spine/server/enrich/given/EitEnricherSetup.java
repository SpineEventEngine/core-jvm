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

package io.spine.server.enrich.given;

import io.spine.base.EntityState;
import io.spine.server.enrich.given.event.EitProjectCreated;
import io.spine.server.enrich.given.event.EitTaskCreated;
import io.spine.server.enrich.given.event.EitUserAccountCreated;
import io.spine.server.entity.Entity;
import io.spine.server.entity.Repository;
import io.spine.server.event.EventEnricher;

import static io.spine.util.Exceptions.newIllegalStateException;

public class EitEnricherSetup {

    private EitEnricherSetup() {
    }

    @SuppressWarnings("OverlyCoupledMethod") // because we match many event to enrichments
    public static EventEnricher createEnricher(EitUserRepository users,
                                               EitProjectRepository projects,
                                               EitTaskRepository tasks) {
        var enricher = EventEnricher.newBuilder()
                .add(EitUserAccountCreated.class, EitUserAccount.class,
                     (e, c) -> find(users, e.getUser()))
                .add(EitProjectCreated.class, EitProject.class,
                     (e, c) -> find(projects, e.getProject()))
                .add(EitTaskCreated.class, EitTask.class,
                     (e, c) -> find(tasks, e.getTask()))
                .build();
        return enricher;
    }

    private static <I, R extends Repository<I, E>, E extends Entity<I, S>, S extends EntityState<I>>
    S find(R repo, I id) {
        return repo.find(id)
                   .map(Entity::state)
                   .orElseThrow(() -> newIllegalStateException(
                           "Unable to find an entity with the id `%s` in the repository `%s`.",
                           id, repo
                   ));
    }
}
