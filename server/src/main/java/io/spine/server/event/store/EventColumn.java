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

package io.spine.server.event.store;

import com.google.protobuf.Timestamp;
import io.spine.core.Event;
import io.spine.query.Columns;
import io.spine.query.RecordColumn;
import io.spine.query.RecordColumns;

import static io.spine.query.RecordColumn.create;

/**
 * Columns stored along with {@link Event}.
 */
@RecordColumns(ofType = Event.class)
@SuppressWarnings("BadImport")    // `create` looks fine in this context.
final class EventColumn {

    /**
     * Stores the Protobuf type name of the event.
     *
     * <p>For example, an Event of type {@code io.spine.test.TaskAdded} whose definition
     * is enclosed in the {@code spine.test} Protobuf package would have this column
     * equal to {@code "spine.test.TaskAdded"}.
     */
    static final RecordColumn<Event, String>
            type = create("type", String.class, (m) -> m.enclosedTypeUrl()
                                                        .typeName()
                                                        .value());

    /**
     * Stores the time when the event was created.
     */
    @SuppressWarnings("DuplicateStringLiteralInspection")   // popular column name.
    static final RecordColumn<Event, Timestamp>
            created = create("created", Timestamp.class, (m) -> m.getContext()
                                                                 .getTimestamp());

    /**
     * Prevents this type from instantiation.
     *
     * <p>This class exists exclusively as a container of the column definitions. Thus it isn't
     * expected to be instantiated at all. See the {@link RecordColumns} docs for more details on
     * this approach.
     */
    private EventColumn() {
    }

    /**
     * Returns all the column definitions.
     */
    static Columns<Event> definitions() {
        return Columns.of(type, created);
    }
}
