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

package io.spine.server.aggregate;

import io.spine.annotation.Experimental;
import io.spine.annotation.SPI;
import io.spine.base.EntityState;

import java.util.Optional;

/**
 * A mapping of aggregate roots to the associated {@linkplain AggregatePart parts}.
 *
 * <p>In the directory, the aggregate root is represented by its type and
 * the parts - by their repositories.
 */
@Experimental
@SPI
public interface AggregateRootDirectory {

    /**
     * Associates the given aggregate part repository and the respective root type.
     */
    void register(AggregatePartRepository<?, ?, ?, ?> repository);

    /**
     * Looks up an aggregate part repository by the type of the root and
     * the type of the part state.
     *
     * <p>If a matching repository if registered, it is obtained by this method with
     * no regard to the visibility of the aggregate.
     *
     * @param rootClass
     *         the type of the aggregate root
     * @param partStateClass
     *         the type of the part state
     * @return the {@link AggregatePartRepository} or {@code Optional.empty()} if
     *         such a repository is not registered
     */
    Optional<? extends AggregatePartRepository<?, ?, ?, ?>>
    findPart(Class<? extends AggregateRoot<?>> rootClass,
             Class<? extends EntityState<?>> partStateClass);
}
