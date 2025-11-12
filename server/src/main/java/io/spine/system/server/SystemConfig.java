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

package io.spine.system.server;

import org.jspecify.annotations.Nullable;
import javax.annotation.concurrent.Immutable;
import java.util.Objects;
import java.util.concurrent.Executor;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Objects.hash;
import static java.util.Objects.nonNull;

/**
 * An immutable set of features of a {@link SystemContext}.
 */
@Immutable
final class SystemConfig implements SystemFeatures {

    private final boolean commandLog;
    private final boolean storeEvents;
    private final @Nullable Executor postingExecutor;

    SystemConfig(boolean commandLog,
                 boolean storeEvents,
                 @Nullable Executor postingExecutor) {
        this.commandLog = commandLog;
        this.storeEvents = storeEvents;
        this.postingExecutor = postingExecutor;
    }

    @Override
    public boolean includeCommandLog() {
        return commandLog;
    }

    @Override
    public boolean includePersistentEvents() {
        return storeEvents;
    }

    @Override
    public boolean postEventsInParallel() {
        return nonNull(postingExecutor);
    }

    /**
     * Returns an {@code Executor} to be used to post system events in parallel.
     *
     * <p>Before call the method, make sure {@link #postEventsInParallel()} is enabled.
     */
    Executor postingExecutor() {
        checkNotNull(postingExecutor);
        return postingExecutor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SystemConfig)) {
            return false;
        }
        var config = (SystemConfig) o;
        return commandLog == config.commandLog &&
                storeEvents == config.storeEvents &&
                Objects.equals(postingExecutor, config.postingExecutor);
    }

    @Override
    public int hashCode() {
        return hash(commandLog, storeEvents);
    }
}
