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

    /**
     * {@inheritDoc}
     *
     * <p>Closes the system context as well.
     */
    @Override
    public void close() {
        super.close();
        system.closeSystemContext();
    }
}
