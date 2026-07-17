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

package io.spine.server.entity;

import io.spine.annotation.Internal;
import io.spine.core.Version;
import io.spine.core.Versions;
import io.spine.server.type.EventEnvelope;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A strategy for incrementing a version of an entity being modified in a transaction.
 */
@Internal
public abstract class VersionIncrement {

    /**
     * Creates a new version for the entity in transaction.
     *
     * @return the incremented {@code Version} of the entity
     */
    protected abstract Version nextVersion();

    /**
     * Creates a version increment that sets the new version from the given event.
     *
     * <p>Such an increment strategy is applied to the {@link Entity} types that represent
     * a sequence of events.
     *
     * <p>One example of such an entity is {@link io.spine.server.aggregate.Aggregate Aggregate}.
     * As a sequence of events, an {@code Aggregate} has no versioning system of its own, thus inherits
     * the versions of the {@linkplain io.spine.server.aggregate.Apply applied} events.
     * In other words, the current version of an {@code Aggregate} is the version of the last
     * applied event.
     */
    public static VersionIncrement fromEvent(EventEnvelope e) {
        return new IncrementFromEvent(e);
    }

    /**
     * Creates a version increment that always advances the given entity version by 1.
     *
     * <p>Such an increment strategy is applied to the {@link Entity} types that cannot use
     * the event versions, such as {@link io.spine.server.projection.Projection Projection}s.
     *
     * <p>A {@code Projection} represents an arbitrary cast of data at a specific moment in
     * time. The events applied to a {@code Projection} are produced by different {@code Entities}
     * and have no common versioning. Thus, a {@code Projection} has its own versioning system.
     * Each event <i>increments</i> the {@code Projection} version by one.
     */
    public static VersionIncrement sequentially(Transaction<?, ?, ?, ?> tx) {
        return new AutoIncrement(tx);
    }

    /**
     * Takes the version from a passed event.
     */
    private static final class IncrementFromEvent extends VersionIncrement {

        private final EventEnvelope event;

        private IncrementFromEvent(EventEnvelope event) {
            super();
            this.event = event;
        }

        @Override
        protected Version nextVersion() {
            var result = event.context().getVersion();
            return result;
        }
    }

    /**
     * Increments the version of the passed transaction by one.
     */
    private static class AutoIncrement extends VersionIncrement {

        private final Transaction<?, ?, ?, ?> transaction;

        private AutoIncrement(Transaction<?, ?, ?, ?> transaction) {
            super();
            this.transaction = checkNotNull(transaction);
        }

        @Override
        protected Version nextVersion() {
            var current = transaction.version();
            var result = Versions.increment(current);
            return result;
        }
    }
}
