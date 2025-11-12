/*
 * Copyright 2025, TeamDev. All rights reserved.
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
package io.spine.server.event.store;

import com.google.common.collect.ImmutableList;
import io.grpc.stub.StreamObserver;
import io.spine.core.Event;
import io.spine.core.EventId;
import io.spine.core.Signal;
import io.spine.logging.WithLogging;
import io.spine.server.ContextSpec;
import io.spine.server.event.EventStore;
import io.spine.server.event.EventStreamQuery;
import io.spine.server.storage.MessageStorage;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.RecordWithColumns;
import io.spine.server.storage.StorageFactory;
import io.spine.server.tenant.EventOperation;
import io.spine.server.tenant.TenantAwareOperation;

import java.util.Iterator;
import java.util.Objects;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Streams.stream;
import static io.spine.server.event.EventComparator.chronological;
import static io.spine.type.ProtoTexts.shortDebugString;
import static java.lang.String.format;
import static java.util.stream.Collectors.toSet;

/**
 * Default implementation of {@link EventStore}.
 */
public final class DefaultEventStore extends MessageStorage<EventId, Event>
        implements EventStore, WithLogging {

    private static final String TENANT_MISMATCH_ERROR_MSG =
            "Events, that target different tenants, cannot be stored in a single operation. " +
                    System.lineSeparator() +
                    "Observed tenants are: %s.";

    private final Log log;

    /**
     * Constructs new instance.
     */
    public DefaultEventStore(ContextSpec context, StorageFactory factory) {
        super(context, factory.createRecordStorage(context, spec()));
        this.log = new Log();
    }

    private static RecordSpec<EventId, Event> spec() {
        var spec = new RecordSpec<>(EventId.class, Event.class, Signal::id,
                                    EventColumn.definitions());
        return spec;
    }

    private static void ensureSameTenant(ImmutableList<Event> events) {
        checkNotNull(events);
        var tenants = events.stream()
                .map(Event::tenant)
                .collect(toSet());
        checkArgument(tenants.size() == 1, TENANT_MISMATCH_ERROR_MSG, tenants);
    }

    @Override
    public void append(Event event) {
        checkNotNull(event);
        TenantAwareOperation op = new EventOperation(event) {
            @Override
            public void run() {
                store(event);
            }
        };
        op.execute();
        log.stored(event);
    }

    @Override
    public void appendAll(Iterable<Event> events) {
        checkNotNull(events);
        var eventList = stream(events)
                .filter(Objects::nonNull)
                .collect(toImmutableList());
        if (eventList.isEmpty()) {
            return;
        }
        var event = eventList.get(0);
        TenantAwareOperation op = new EventOperation(event) {
            @Override
            public void run() {
                if (isTenantSet()) { // If multitenant context
                    ensureSameTenant(eventList);
                }
                store(eventList);
            }
        };
        op.execute();

        log.stored(events);
    }

    @Override
    public void read(EventStreamQuery request, StreamObserver<Event> responseObserver) {
        checkNotNull(request);
        checkNotNull(responseObserver);

        log.readingStart(request, responseObserver);

        var eventRecords = iterator(request);
        while (eventRecords.hasNext()) {
            var event = eventRecords.next();
            responseObserver.onNext(event);
        }
        responseObserver.onCompleted();

        log.readingComplete(responseObserver);
    }

    /**
     * Obtains an iterator over events matching the passed query.
     * The iteration is chronologically sorted.
     */
    private Iterator<Event> iterator(EventStreamQuery query) {
        checkNotNull(query);
        var iterator = find(query);
        var entities = ImmutableList.copyOf(iterator);
        Predicate<Event> predicate = new MatchesStreamQuery(query);
        var result = entities.stream()
                .filter(predicate)
                .sorted(chronological())
                .iterator();
        return result;
    }

    /**
     * Obtains iteration over entities matching the passed query.
     */
    private Iterator<Event> find(EventStreamQuery query) {
        var converted = Queries.convert(query);
        return readAll(converted);
    }

    private void store(Event event) {
        var toStore = event.clearEnrichments();
        write(toStore);
    }

    private void store(Iterable<Event> events) {
        var records = stream(events)
                .map(Event::clearEnrichments)
                .map((e) -> RecordWithColumns.create(e, recordSpec()))
                .collect(toImmutableList());
        writeAll(records);
    }

    /**
     * Logging for operations of {@link DefaultEventStore}.
     */
    private final class Log {

        private final boolean debugEnabled = logger().atDebug().isEnabled();

        private void stored(Event event) {
            logger().atDebug().log(() -> format(
                    "Stored: %s.", shortDebugString(event)));
        }

        private void stored(Iterable<Event> events) {
            if (debugEnabled) {
                for (var event : events) {
                    stored(event);
                }
            }
        }

        private void readingStart(EventStreamQuery query, StreamObserver<Event> observer) {
            logger().atDebug().log(() -> format(
                    "Creating stream on request: `%s` for observer: `%s`.",
                      shortDebugString(query),
                      observer));
        }

        private void readingComplete(StreamObserver<Event> observer) {
            logger().atDebug().log(() -> format(
                    "Observer `%s` got all queried events.", observer));
        }
    }
}
