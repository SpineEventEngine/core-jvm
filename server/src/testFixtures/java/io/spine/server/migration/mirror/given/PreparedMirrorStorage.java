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

package io.spine.server.migration.mirror.given;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.base.EntityState;
import io.spine.server.migration.mirror.MirrorStorage;
import io.spine.system.server.Mirror;

import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Fills a {@linkplain MirrorStorage} with data.
 */
public final class PreparedMirrorStorage {

    private final MirrorStorage storage;

    public PreparedMirrorStorage(MirrorStorage storage) {
        this.storage = storage;
    }

    @CanIgnoreReturnValue
    public PreparedMirrorStorage put(Supplier<EntityState<?>> stateSupplier,
                                     int numberOfStates) {
        return put(stateSupplier, numberOfStates, false, false);
    }

    @CanIgnoreReturnValue
    public PreparedMirrorStorage putDeleted(Supplier<EntityState<?>> stateSupplier,
                                            int numberOfStates) {
        return put(stateSupplier, numberOfStates, false, true);
    }

    @CanIgnoreReturnValue
    public PreparedMirrorStorage putArchived(Supplier<EntityState<?>> stateSupplier,
                                             int numberOfStates) {
        return put(stateSupplier, numberOfStates, true, false);
    }

    private PreparedMirrorStorage put(Supplier<EntityState<?>> supplier, int numberOfStates,
                                      boolean archived, boolean deleted) {

        var mirrors = IntStream.rangeClosed(1, numberOfStates)
                               .mapToObj(i -> mirror(supplier, archived, deleted))
                               .collect(Collectors.toList());
        storage.writeBatch(mirrors);
        return this;
    }

    private static Mirror mirror(Supplier<EntityState<?>> stateSupplier,
                                 boolean archived, boolean deleted) {

        var state = stateSupplier.get();
        var lifecycle = MirrorToEntityRecordTestEnv.lifecycle(archived, deleted);
        var mirror = MirrorToEntityRecordTestEnv.mirror(state, lifecycle);
        return mirror;
    }

    public MirrorStorage get() {
        return storage;
    }
}
