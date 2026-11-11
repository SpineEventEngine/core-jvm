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

package io.spine.server.storage.memory;

import io.spine.server.entity.EntityRecord;
import io.spine.test.storage.StgProjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static com.google.common.collect.Lists.newArrayListWithExpectedSize;
import static com.google.common.collect.Sets.newHashSetWithExpectedSize;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("`MultiTenantStorage` should")
class MultiTenantStorageTest {

    private static final boolean IS_MULTI_TENANT = false;

    private MultiTenantStorage<TenantRecords<StgProjectId, EntityRecord>> multiTenantStorage;

    @BeforeEach
    void setUp() {
        multiTenantStorage = new MultiTenantStorage<>(IS_MULTI_TENANT) {
            @Override
            TenantRecords<StgProjectId, EntityRecord> createSlice() {
                return new TenantRecords<>();
            }
        };
    }

    @Test
    @DisplayName("return same slice within single tenant and multitenant environment")
    void returnSameSlice()
            throws InterruptedException, ExecutionException {
        var numberOfTasks = 1000;
        Collection<Callable<TenantRecords<StgProjectId, EntityRecord>>> tasks =
                newArrayListWithExpectedSize(numberOfTasks);

        for (var i = 0; i < numberOfTasks; i++) {
            tasks.add(() -> {
                var storage = multiTenantStorage.currentSlice();
                return storage;
            });
        }

        var futures = executeInMultithreadedEnvironment(tasks);
        var tenantRecords = convertFuturesToSetOfCompletedResults(futures);

        var expected = 1;
        assertEquals(expected, tenantRecords.size());
    }

    private static <R> Set<R> convertFuturesToSetOfCompletedResults(List<Future<R>> futures)
            throws ExecutionException, InterruptedException {
        Set<R> tenantRecords = newHashSetWithExpectedSize(futures.size());
        for (var future : futures) {
            tenantRecords.add(future.get());
        }
        return tenantRecords;
    }

    private static <R> List<Future<R>>
    executeInMultithreadedEnvironment(Collection<Callable<R>> tasks) throws InterruptedException {
        var executor = newFixedThreadPool(
                Runtime.getRuntime().availableProcessors() * 2);
        var futures = executor.invokeAll(tasks);
        return futures;
    }
}
