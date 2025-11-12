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

package io.spine.server.delivery.memory;

import com.google.protobuf.Duration;
import io.spine.server.NodeId;
import io.spine.server.delivery.AbstractWorkRegistry;
import io.spine.server.delivery.PickUpOutcome;
import io.spine.server.delivery.ShardIndex;
import io.spine.server.delivery.ShardSessionRecord;
import io.spine.server.delivery.ShardedWorkRegistry;
import io.spine.server.delivery.WorkerId;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import static com.google.common.collect.Iterators.unmodifiableIterator;
import static com.google.common.collect.Maps.newConcurrentMap;

/**
 * An in-memory implementation of {@link ShardedWorkRegistry ShardedWorkRegistry}.
 *
 * @implNote This implementation synchronizes methods of {@code AbstractWorkRegistry} and
 *         uses a concurrent collection in order to guarantee thread safety.
 */
public final class InMemoryShardedWorkRegistry extends AbstractWorkRegistry {

    private final Map<ShardIndex, ShardSessionRecord> workByNode = newConcurrentMap();

    @Override
    public synchronized PickUpOutcome pickUp(ShardIndex index, NodeId node) {
        return super.pickUp(index, node);
    }

    @Override
    public synchronized void release(ShardSessionRecord session) {
        var record = workByNode.get(session.getIndex());
        clearNode(record);
    }

    /**
     * Returns an identifier of the current worker that is now going to process the shard.
     *
     * <p>This implementation uses an identifier of the current thread as a {@code WorkerId}.
     *
     * @param node
     *         the node to which the resulted worker belongs
     */
    @Override
    protected WorkerId currentWorkerFor(NodeId node) {
        var currentThread = Thread.currentThread().getId();
        var worker = WorkerId.newBuilder()
                .setNodeId(node)
                .setValue(String.valueOf(currentThread))
                .build();
        return worker;
    }

    @Override
    public synchronized Iterable<ShardIndex> releaseExpiredSessions(Duration inactivityPeriod) {
        return super.releaseExpiredSessions(inactivityPeriod);
    }

    @Override
    protected synchronized void clearNode(ShardSessionRecord session) {
        super.clearNode(session);
    }

    @Override
    protected Iterator<ShardSessionRecord> allRecords() {
        return unmodifiableIterator(workByNode.values().iterator());
    }

    @Override
    protected void write(ShardSessionRecord session) {
        workByNode.put(session.getIndex(), session);
    }

    @Override
    protected Optional<ShardSessionRecord> find(ShardIndex index) {
        return Optional.ofNullable(workByNode.get(index));
    }
}
