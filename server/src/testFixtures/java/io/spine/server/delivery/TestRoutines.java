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

package io.spine.server.delivery;

import io.spine.server.projection.Projection;
import io.spine.server.projection.ProjectionRepository;

import java.util.List;
import java.util.concurrent.Callable;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.Executors.newFixedThreadPool;

/**
 * Test-only routines to use when testing the catch-up and delivery API.
 */
public final class TestRoutines {

    private TestRoutines() {
    }

    public static <P extends Projection<String, ?, ?>> P
    findView(ProjectionRepository<String, P, ?> repo, String id) {
        var view = repo.find(id);
        assertThat(view).isPresent();
        return view.get();
    }

    public static void post(List<Callable<Object>> jobs, int threads) throws InterruptedException {
        var service = newFixedThreadPool(threads);
        service.invokeAll(jobs);
        var leftovers = service.shutdownNow();
        assertThat(leftovers).isEmpty();
    }
}
