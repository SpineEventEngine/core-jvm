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

package io.spine.server.model.given.filter;

import io.spine.core.Where;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;
import io.spine.test.model.ModCreateProject;
import io.spine.test.model.ModProjectCreated;
import io.spine.test.model.filter.ModProject;

/**
 * This aggregate declares a command-handling method that breaks the contract imposed by
 * {@link Assign}, by using {@link Where} filters.
 */
public final class ModSplitAggregate extends Aggregate<String, ModProject, ModProject.Builder> {

    private static final String FANCY_ID = "1";
    private static final String INTERNAL_FANCY_ID = "very fancy project";

    @Assign
    ModProjectCreated handleFancy(@Where(field = "id", equals = FANCY_ID)
                                  ModCreateProject cmd) {
        return ModProjectCreated
                .newBuilder()
                .setId(INTERNAL_FANCY_ID)
                .build();
    }

    @Assign
    ModProjectCreated handle(ModCreateProject cmd) {
        return ModProjectCreated
                .newBuilder()
                .setId(cmd.getId())
                .build();
    }

    @Apply
    private void on(ModProjectCreated e) {
        builder().setProjectId(e.getId());
    }
}
