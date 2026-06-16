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

package io.spine.server.entity;

import io.spine.annotation.Internal;
import io.spine.base.EntityState;
import io.spine.client.ResponseFormat;
import io.spine.client.TargetFilters;
import io.spine.query.EntityQuery;

import java.util.Iterator;

import static io.spine.protobuf.AnyPacker.unpack;

/**
 * A repository that may be queried for {@linkplain EntityRecord entity records}
 * or states of stored entities.
 *
 * @param <I>
 *         the type of identifiers of stored entities
 * @param <S>
 *         the type of stored entity states
 */
@Internal
public interface QueryableRepository<I, S extends EntityState<I>> {

    /**
     * Queries this repository for the records according to the specified filters and returns
     * the response in the format of choice.
     *
     * @param filters
     *         filters to apply to the records when querying
     * @param format
     *         the format of the response
     * @return an iterator over the results
     */
    Iterator<EntityRecord> findRecords(TargetFilters filters, ResponseFormat format);

    /**
     * Queries this repository for the records and returns them according to the specified format.
     *
     * @param format
     *         the format of the response
     * @return an iterator over the results
     */
    Iterator<EntityRecord> findRecords(ResponseFormat format);

    /**
     * Queries this repository for the states of stored entities.
     *
     * @param query
     *         the query to execute
     * @return an iterator over the results
     */
    Iterator<S> findStates(EntityQuery<I, S, ?> query);

    /**
     * Unpacks the {@code Entity} state stored in the provided {@link EntityRecord} into
     * a message of type {@code S}.
     *
     * <p>It is a responsibility of a caller to submit the {@code EntityRecord}s that
     * store the states of a compatible type.
     *
     * @param record
     *         record storing the packed state of type {@code S}
     * @return unpacked {@code Entity} state
     */
    @SuppressWarnings("unchecked")  /* Ensured by the repository contract. */
    default S stateFrom(EntityRecord record) {
        return (S) unpack(record.getState());
    }
}
