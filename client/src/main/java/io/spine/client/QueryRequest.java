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

package io.spine.client;

import com.google.common.collect.ImmutableList;
import io.spine.base.EntityState;
import io.spine.query.EntityQuery;

/**
 * Fetches the results of the specified {@link EntityQuery}.
 *
 * @param <S>
 *         the type of the queried entity states
 */
final class QueryRequest<S extends EntityState<?>> extends ClientRequestBase {

    /** The type of entities returned by the request. */
    private final Class<S> entityStateType;

    /** The query to run. */
    private final EntityQuery<?, S, ?> entityQuery;

    /**
     * Transforms the {@link EntityQuery} to the Proto {@link Query} in order
     * to send it over the wire.
     */
    private final EntityQueryToProto transformer;

    /**
     * Creates an instance of the request based on the passed parent {@code ClientRequest},
     * for the given {@code EntityQuery}.
     */
    QueryRequest(ClientRequest parent, EntityQuery<?, S, ?> query) {
        super(parent);
        this.entityQuery = query;
        this.entityStateType = query.subject()
                                    .recordType();
        var factory = client().requestOf(user()).query();
        this.transformer = EntityQueryToProto.transformWith(factory);
    }

    /**
     * Executes and obtains results of the query.
     */
    ImmutableList<S> run() {
        var query = transformer.apply(entityQuery);
        var result = client().read(query, entityStateType);
        return result;
    }
}
