/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
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
