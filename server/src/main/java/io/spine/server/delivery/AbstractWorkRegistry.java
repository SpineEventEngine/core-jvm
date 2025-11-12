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

package io.spine.server.delivery;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Duration;
import com.google.protobuf.util.Durations;
import io.spine.annotation.SPI;
import io.spine.server.NodeId;

import java.util.Iterator;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.protobuf.util.Timestamps.between;
import static io.spine.base.Time.currentTime;

/**
 * An implementation base for {@link ShardedWorkRegistry ShardedWorkRegistries} based on a specific
 * persistence mechanism.
 *
 * @implNote This class is NOT thread safe. Synchronize the atomic persistence operations
 *         as well as the methods implemented in this class to make an implementation thread safe.
 */
@SPI
public abstract class AbstractWorkRegistry implements ShardedWorkRegistry {

    @Override
    public PickUpOutcome pickUp(ShardIndex index, NodeId node) {
        checkNotNull(index);
        checkNotNull(node);

        var worker = currentWorkerFor(node);
        var session = pickUp(index, worker);

        return session;
    }

    private PickUpOutcome pickUp(ShardIndex index, WorkerId worker) {
        var optionalRecord = find(index);
        if (optionalRecord.isEmpty()) {
            var newRecord = createRecord(index, worker);
            return PickUpOutcomeMixin.pickedUp(newRecord);
        }

        var record = optionalRecord.get();
        if (hasWorker(record)) {
            return PickUpOutcomeMixin
                    .alreadyPicked(record.getWorker(), record.getWhenLastPicked());
        }

        var updatedRecord = updateNode(record, worker);
        return PickUpOutcomeMixin.pickedUp(updatedRecord);
    }

    /**
     * Returns an identifier of the current worker that is now going to process the shard.
     *
     * <p>An example of such an identifier could be ID of the thread which performs processing.
     *
     * @param node
     *         the node to which the resulted worker belongs
     */
    protected abstract WorkerId currentWorkerFor(NodeId node);

    private static boolean hasWorker(ShardSessionRecord record) {
        return !WorkerId.getDefaultInstance().equals(record.getWorker());
    }

    private ShardSessionRecord createRecord(ShardIndex index, WorkerId worker) {
        var newRecord = ShardSessionRecord.newBuilder()
                .setIndex(index)
                .setWorker(worker)
                .setWhenLastPicked(currentTime())
                .build();
        write(newRecord);
        return newRecord;
    }

    private ShardSessionRecord updateNode(ShardSessionRecord record, WorkerId worker) {
        var updatedRecord = record.toBuilder()
                .setWorker(worker)
                .setWhenLastPicked(currentTime())
                .build();
        write(updatedRecord);
        return updatedRecord;
    }

    @Override
    public Iterable<ShardIndex> releaseExpiredSessions(Duration inactivityPeriod) {
        checkNotNull(inactivityPeriod);
        ImmutableSet.Builder<ShardIndex> resultBuilder = ImmutableSet.builder();
        allRecords().forEachRemaining(record -> {
            if (record.hasWorker()) {
                var whenPicked = record.getWhenLastPicked();
                var elapsed = between(whenPicked, currentTime());

                var comparison = Durations.compare(elapsed, inactivityPeriod);
                if (comparison >= 0) {
                    clearNode(record);
                    resultBuilder.add(record.getIndex());
                }
            }
        });
        return resultBuilder.build();
    }

    /**
     * Clears the value of {@code ShardSessionRecord.worker} and stores the session.
     */
    protected void clearNode(ShardSessionRecord session) {
        var record = session.toBuilder()
                .clearWorker()
                .build();
        write(record);
    }

    /**
     * Obtains all the session records associated with this registry.
     */
    protected abstract Iterator<ShardSessionRecord> allRecords();

    /**
     * Stores the given session.
     *
     * <p>The session may or may be not present in the registry already. After calling this method,
     * the given session must be reachable via {@link #find(ShardIndex)} and {@link #allRecords()}.
     */
    protected abstract void write(ShardSessionRecord session);

    /**
     * Looks for the session record by the given shard index.
     *
     * @param index
     *         shard index to find a session for
     * @return a session record or {@code Optional.empty()} if the record is not present in
     *         the registry
     */
    protected abstract Optional<ShardSessionRecord> find(ShardIndex index);
}
