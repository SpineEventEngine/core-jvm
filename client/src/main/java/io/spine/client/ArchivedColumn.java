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
 * A column of an entity, which tells if an entity is archived.
 *
 * <p>The sole purpose of this type is to provide an extension for the client-side query language,
 * allowing querying for archived/non-archived entities.
 *
 * <pre>
 *  Query query =
 *      MyProjection.query()
 *                  // ...
 *                  .where(ArchivedColumn.is(), true)   // Only include archived.
 *                  .build(...);
 *  ... result = execute(query);
 * </pre>
 *
 * <p>This type is a singleton.
 */
public final class ArchivedColumn extends EntityLifecycleColumn<Boolean> {

    private static final ArchivedColumn instance = new ArchivedColumn();

    /**
     * Creates an instance of this column.
     */
    @SuppressWarnings("DuplicateStringLiteralInspection")   /* Used in a different context. */
    private ArchivedColumn() {
        super("archived");
    }

    /**
     * Returns a singleton instance of this type.
     */
    public static ArchivedColumn instance() {
        return instance;
    }

    /**
     * Returns a singleton instance of this type.
     *
     * <p>Serves as a more DSL-friendly alternative to {@link #instance() instance()}.
     */
    public static ArchivedColumn is() {
        return instance();
    }
}
