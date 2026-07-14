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

import io.spine.base.Identifier;

import java.util.Iterator;

import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * An iterator of all entities from the storage.
 *
 * <p>This iterator does not allow removal.
 *
 * @param <I>
 *         the type of entity identifiers
 * @param <E>
 *         the type of entities
 */
class EntityIterator<I, E extends Entity<I, ?>> implements Iterator<E> {

    private final Repository<I, E> repository;
    private final Iterator<I> index;

    EntityIterator(Repository<I, E> repository) {
        this.repository = repository;
        this.index = repository.storage()
                               .index();
    }

    @Override
    public boolean hasNext() {
        var result = index.hasNext();
        return result;
    }

    @Override
    public E next() {
        var id = index.next();
        var loaded = repository.find(id);
        if (loaded.isEmpty()) {
            var idStr = Identifier.toString(id);
            throw newIllegalStateException("Unable to load entity with ID: `%s`.", idStr);
        }

        var entity = loaded.get();
        return entity;
    }
}
