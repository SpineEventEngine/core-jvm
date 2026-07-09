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

package io.spine.server.aggregate;

import io.spine.base.Identifier;
import io.spine.protobuf.AnyPacker;
import io.spine.server.entity.EntityRecord;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A factory of records storing the {@link Aggregate} data in a storage.
 *
 * <p>The records of the event journal are created by
 * {@link io.spine.server.entity.storage.EntityEventRecords EntityEventRecords}.
 */
final class AggregateRecords {

    /**
     * Prevents this utility from instantiation.
     */
    private AggregateRecords() {
    }

    /**
     * Creates a new record to store the {@code Aggregate} state.
     *
     * <p>Since the event-sourcing cutover the persisted {@link EntityRecord} is the source of
     * truth for loading an aggregate, so the business {@linkplain Aggregate#state() state} is
     * <em>always</em> packed into the record — unconditionally of the aggregate's visibility.
     * Visibility now gates only the read-side query exposure, not the state write.
     *
     * @param aggregate
     *         an instance of the aggregate
     * @param <I>
     *         type of Aggregate identifiers
     * @return a new record
     */
    static <I> EntityRecord newStateRecord(Aggregate<I, ?, ?> aggregate) {
        checkNotNull(aggregate);

        var flags = aggregate.lifecycleFlags();
        var id = aggregate.id();
        var version = aggregate.version();
        var state = aggregate.state();

        var builder = EntityRecord.newBuilder()
                .setEntityId(Identifier.pack(id))
                .setLifecycleFlags(flags)
                .setVersion(version)
                .setState(AnyPacker.pack(state));
        return builder.build();
    }
}
