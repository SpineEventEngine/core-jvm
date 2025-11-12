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

import com.google.protobuf.Duration;
import io.spine.annotation.SPI;
import io.spine.server.NodeId;

/**
 * The registry of the shard indexes along with the identifiers of the nodes, which
 * process the messages corresponding to each index.
 */
@SPI
public interface ShardedWorkRegistry {

    /**
     * Picks up the shard at a given index to process.
     *
     * <p>This action is intended to be exclusive, i.e. a single shard may be served
     * by a single application node at a given moment of time.
     *
     * <p>In case of a successful operation, an instance of {@link PickUpOutcome} containing
     * {@code ShardSessionRecord} is returned. The worker node which obtained the session,
     * should release {@link #release(ShardSessionRecord) release()} the session
     * at the end of its work.
     *
     * <p>If the shard at a given index is already picked up by some node,
     * a {@link PickUpOutcome} containing {@link ShardAlreadyPickedUp} is returned. This outcome
     * contains information about the {@code WorkerId} of the worker owning the session
     * at the moment, and a {@code Timestamp} telling the shard was picked up.
     *
     * @param index
     *         the index of the shard to pick up for processing
     * @param node
     *         the identifier of the node for which to pick the shard
     * @return outcome of the operation
     */
    PickUpOutcome pickUp(ShardIndex index, NodeId node);

    /**
     * Ends the given {@code session} and releases the picked up shard, making it available
     * for picking up again.
     *
     * @param session
     *         a session to end
     */
    void release(ShardSessionRecord session);

    /**
     * Clears up the recorded {@code NodeId}s from the session records if there was no activity
     * for longer than passed {@code inactivityPeriod}.
     *
     * <p>It may be handy if an application node hangs or gets killed — so that it is not able
     * to complete the session in a conventional way.
     *
     * @param inactivityPeriod
     *         the duration of the period after which the session is considered expired
     * @return the indexes of shards which sessions have been released
     */
    Iterable<ShardIndex> releaseExpiredSessions(Duration inactivityPeriod);
}
