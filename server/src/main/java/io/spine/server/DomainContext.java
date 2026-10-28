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

package io.spine.server;

import io.spine.server.entity.Repository;
import io.spine.system.server.SystemClient;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A bounded context representing a user-specific domain model.
 *
 * <p>The {@link BoundedContext} instances typically seen to the users
 * (i.e. built with a {@link BoundedContextBuilder}) are instances of this class.
 *
 * <p>All the user interactions with the system (such as
 * {@linkplain BoundedContext#register(Repository) repository registration},
 * {@linkplain BoundedContext#commandBus() command posting},
 * {@linkplain BoundedContext.InternalAccess#findRepository(Class) query processing}, etc.) happen
 * through an instance of this class.
 *
 * <p>Each {@code DomainContext} has an associated
 * {@link io.spine.system.server.SystemContext SystemContext}, which manages the meta-information
 * about entities of this Bounded Context.
 *
 * @see io.spine.system.server.SystemContext SystemContext
 */
final class DomainContext extends BoundedContext {

    private final SystemClient system;

    private DomainContext(BoundedContextBuilder builder, SystemClient system) {
        super(builder);
        this.system = checkNotNull(system);
    }

    static DomainContext newInstance(BoundedContextBuilder builder, SystemClient system) {
        checkNotNull(builder);
        checkNotNull(system);

        var result = new DomainContext(builder, system);
        result.init();
        return result;
    }

    @Override
    public SystemClient systemClient() {
        return system;
    }

    @Override
    protected boolean isSystem() {
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Closes the system context as well, even if closing this context fails.
     */
    @Override
    public void close() {
        try {
            super.close();
        } finally {
            system.closeSystemContext();
        }
    }
}
