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

package io.spine.server.given.groups;

import io.spine.core.EventContext;
import io.spine.core.External;
import io.spine.core.Subscribe;
import io.spine.server.given.organizations.Organization;
import io.spine.server.projection.Projection;
import io.spine.server.projection.ProjectionRepository;
import io.spine.server.route.StateUpdateRouting;

import static io.spine.server.route.EventRoute.withId;

public final class GroupProjection extends Projection<GroupId, Group, Group.Builder> {

    @Subscribe /* `Organization` belongs to another Context called `Organizations`. */
    void on(@External Organization organization, EventContext systemContext) {
        var updateTime = systemContext.getTimestamp();
        builder().setId(id())
                 .setName(organization.getName() + updateTime)
                 .addAllParticipant(organization.getMemberList())
                 .addParticipant(organization.getHead());
    }

    public static final class Repository
            extends ProjectionRepository<GroupId, GroupProjection, Group> {

        @Override
        protected void setupStateRouting(StateUpdateRouting<GroupId> routing) {
            routing.route(Organization.class, (org, eventContext) ->
                    withId(GroupId.newBuilder()
                                   .setUuid(org.getHead().getValue())
                                   .build())
            );
        }
    }
}
