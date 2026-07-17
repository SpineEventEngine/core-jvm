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

package io.spine.client;

/**
 * A column of an entity, which tells if it is deleted.
 *
 * <p>This type is an extension of the client-side query language, allowing filtering of queried
 * entities for deleted/non-deleted items.
 *
 * <pre>
 *  Query query =
 *      SomeProjection.query()
 *                    // ...
 *                    .where(DeletedColumn.is(), true)   // Only include deleted.
 *                    .build(...);
 *  ... result = execute(query);
 * </pre>
 *
 * <p>This type is a singleton.
 */
public final class DeletedColumn extends EntityLifecycleColumn<Boolean> {

    private static final DeletedColumn instance = new DeletedColumn();

    /**
     * Creates an instance of this column.
     */
    @SuppressWarnings("DuplicateStringLiteralInspection")   /* Used in a different context. */
    private DeletedColumn() {
        super("deleted");
    }

    /**
     * Returns a singleton instance of this type.
     */
    public static DeletedColumn instance() {
        return instance;
    }

    /**
     * Returns a singleton instance of this type.
     *
     * <p>Serves as a more DSL-friendly alternative to {@link #instance() instance()}.
     */
    public static DeletedColumn is() {
        return instance();
    }
}
