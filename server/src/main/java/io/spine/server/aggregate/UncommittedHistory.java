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

import com.google.common.collect.ImmutableList;
import io.spine.core.Event;
import io.spine.server.type.EventEnvelope;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * Uncommitted events and snapshots created for this aggregate during the dispatching.
 *
 * <p>Watches how the events {@linkplain Aggregate#invokeApplier(EventEnvelope) are sent}
 * to the {@link Aggregate} applier methods. Remembers all such events as uncommitted.
 *
 * <p>Once an aggregate is loaded from the storage, the {@code UncommittedHistory}
 * {@linkplain #onAggregateRestored(AggregateHistory) remembers} the event count
 * after the last snapshot.
 *
 * <p>If during the dispatching of events the number of events since the last snapshot exceeds
 * the snapshot trigger, a snapshot is created and remembered as a part of uncommitted history.
 *
 * <p>In order to ignore the events fed to the aggregate when it's being loaded from the storage,
 * the {@code UncommittedHistory}'s tracking is only {@linkplain #startTracking(int) activated}
 * when the new and truly un-yet-committed events are dispatched to the applier methods.
 * The tracking {@linkplain #stopTracking(boolean) stops} after all the new events have been played
 * on the aggregate instance; if the batch failed to apply, its events are discarded rather than
 * remembered.
 *
 * @see Aggregate#apply(List, int) on activation and deactivation of event tracking
 * @see Aggregate#replay(AggregateHistory) on supplying the history stats when loading aggregate
 *         instances from the storage
 */
final class UncommittedHistory {

    private final Supplier<Snapshot> makeSnapshot;
    private final List<AggregateHistory> historySegments = new ArrayList<>();
    private final List<Event> currentSegment = new ArrayList<>();

    private int eventCountAfterLastSnapshot;
    private @Nullable Integer snapshotTrigger = null;
    private boolean enabled = false;

    /**
     * The size of {@link #historySegments}, a copy of {@link #currentSegment}, and the value of
     * {@link #eventCountAfterLastSnapshot} captured when the current batch
     * {@linkplain #startTracking(int) started}.
     *
     * <p>These fields allow {@linkplain #stopTracking(boolean) rolling the batch back} if it fails
     * to apply, so that events of a rolled-back dispatch do not linger in the uncommitted history.
     */
    private int segmentCountBeforeBatch;
    private @Nullable List<Event> currentSegmentBeforeBatch;
    private int eventCountBeforeBatch;

    /**
     * Creates an instance of the uncommitted history.
     *
     * @param makeSnapshot
     *         a callback which would supply a snapshot of the aggregate upon demand
     */
    UncommittedHistory(Supplier<Snapshot> makeSnapshot) {
        this.makeSnapshot = makeSnapshot;
    }

    /**
     * Enables the tracking of events.
     *
     * <p>All events {@linkplain #track(EventEnvelope) sent} to this instance will now be counted
     * as new and uncommitted events in the aggregate's history.
     *
     * @param snapshotTrigger
     *         the snapshot trigger to consider each time a new event is tracked
     */
    void startTracking(int snapshotTrigger) {
        enabled = true;
        this.snapshotTrigger = snapshotTrigger;
        this.segmentCountBeforeBatch = historySegments.size();
        this.currentSegmentBeforeBatch = new ArrayList<>(currentSegment);
        this.eventCountBeforeBatch = eventCountAfterLastSnapshot;
    }

    /**
     * Stops the tracking of the events.
     *
     * <p>All the events {@linkplain #track(EventEnvelope) sent to the tracking} will be counted
     * as the events already present in the aggregate storage.
     *
     * <p>If the batch of events failed to apply, all the events tracked since the matching
     * {@link #startTracking(int)} are discarded, restoring the uncommitted history to the state
     * it had before the batch. This prevents events of a rolled-back dispatch from lingering in
     * the uncommitted history and being stored later — e.g. together with a subsequent successful
     * dispatch of a cached aggregate.
     *
     * @param successful
     *         whether the batch of events was applied without an error
     */
    void stopTracking(boolean successful) {
        enabled = false;
        snapshotTrigger = null;
        if (!successful) {
            discardCurrentBatch();
        }
        currentSegmentBeforeBatch = null;
    }

    /**
     * Removes from the uncommitted history all the events tracked during the current batch,
     * restoring it to the state captured when the batch {@linkplain #startTracking(int) started}.
     */
    private void discardCurrentBatch() {
        var addedSegments =
                historySegments.subList(segmentCountBeforeBatch, historySegments.size());
        addedSegments.clear();
        currentSegment.clear();
        currentSegment.addAll(requireNonNull(currentSegmentBeforeBatch));
        eventCountAfterLastSnapshot = eventCountBeforeBatch;
    }

    /**
     * Tracks the event dispatched to the Aggregate's applier.
     *
     * <p>If the tracking is not {@linkplain #startTracking(int) started}, the event is considered
     * an old one and such as not requiring storage and tracking. In this case, this method
     * does nothing.
     *
     * <p>If the event is a new one, it is remembered as a part of the uncommitted history.
     *
     * <p>If the number of events since the last snapshot equals or exceeds the snapshot trigger,
     * a new snapshot is made and saved to the uncommitted history.
     *
     * @param envelope
     *         an event to track
     */
    void track(EventEnvelope envelope) {
        if (!enabled) {
            return;
        }
        requireNonNull(snapshotTrigger,
                       "The snapshot trigger must be set" +
                               " to track the events applied to an `Aggregate`.");

        var event = envelope.outerObject();
        if (event.isRejection()) {
            return;
        }
        currentSegment.add(event);
        var eventsInSegment = currentSegment.size();
        if (eventCountAfterLastSnapshot + eventsInSegment >= snapshotTrigger) {
            var snapshot = makeSnapshot.get();
            var completedSegment = historyFrom(currentSegment, snapshot);
            historySegments.add(completedSegment);
            currentSegment.clear();
            eventCountAfterLastSnapshot = 0;
        }
    }

    /**
     * Composes and obtains the collected uncommitted history.
     *
     * <p>The returned items represent the uncommitted events split into {@code AggregateHistory},
     * each containing a snapshot made for each segment, if the snapshot was triggered.
     */
    ImmutableList<AggregateHistory> get() {
        ImmutableList.Builder<AggregateHistory> builder = ImmutableList.builder();
        builder.addAll(historySegments);
        if (currentSegment.size() > 0) {
            var lastSegment = historyFrom(currentSegment);
            builder.add(lastSegment);
        }
        return builder.build();
    }

    /**
     * Returns all tracked uncommitted events.
     */
    UncommittedEvents events() {
        var events = get().stream()
                                  .flatMap(segment -> segment.getEventList()
                                                             .stream())
                                  .collect(toList());
        return UncommittedEvents.ofNone()
                                .append(events);
    }

    /**
     * Tells if this history contains any uncommitted events.
     */
    boolean hasEvents() {
        return !currentSegment.isEmpty() || !historySegments.isEmpty();
    }

    /**
     * Marks this history as stored and no longer uncommitted.
     */
    void commit() {
        historySegments.clear();
        currentSegment.clear();
    }

    /**
     * Records the history loaded from the aggregate storage.
     *
     * <p>This is only required in order to know the number of events since the last snapshot.
     */
    void onAggregateRestored(AggregateHistory history) {
        this.eventCountAfterLastSnapshot = history.getEventCount();
    }

    private static AggregateHistory historyFrom(List<Event> events, Snapshot snapshot) {
        return AggregateHistory.newBuilder()
                .addAllEvent(events)
                .setSnapshot(snapshot)
                .build();
    }

    private static AggregateHistory historyFrom(List<Event> events) {
        return AggregateHistory.newBuilder()
                .addAllEvent(events)
                .build();
    }
}
