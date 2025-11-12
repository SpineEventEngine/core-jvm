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

package io.spine.server.entity.given.tx;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Message;
import io.spine.core.Subscribe;
import io.spine.server.entity.given.tx.ProjectionState.ProjectionType;
import io.spine.server.entity.given.tx.event.TxCreated;
import io.spine.server.entity.given.tx.event.TxErrorRequested;
import io.spine.server.entity.given.tx.event.TxStateErrorRequested;
import io.spine.server.projection.Projection;

import java.util.List;

import static com.google.common.collect.Lists.newLinkedList;
import static io.spine.server.entity.given.tx.ProjectionState.ProjectionType.USEFUL;
import static io.spine.server.entity.given.tx.ProjectionState.ProjectionType.VERY_USEFUL;

/**
 * Test environment projection for {@link io.spine.server.projection.ProjectionTransactionTest}.
 */
public final class TxProjection
        extends Projection<Id, ProjectionState, ProjectionState.Builder> {

    private final List<Message> receivedEvents = newLinkedList();

    private final boolean isNullType;

    public TxProjection(Id id, boolean isNullType) {
        super(id);
        this.isNullType = isNullType;
    }

    public TxProjection(Id id) {
        this(id, false);
    }

    @Subscribe
    void event(TxCreated e) {
        receivedEvents.add(e);
        builder().setId(id())
                 .setName(e.getName());
    }

    /**
     * Always throws {@code RuntimeException} to emulate an error in
     * a subscribing method of a Projection.
     *
     * @see io.spine.server.projection.ProjectionTransactionTest#failingInHandler
     */
    @Subscribe
    @SuppressWarnings("DoNotCallSuggester") /* Does not apply to this test env. class. */
    void event(TxErrorRequested e) {
        throw new RuntimeException("that tests the projection tx behaviour");
    }

    @Subscribe
    void event(TxStateErrorRequested e) {
        // By convention, the first field of the state is required.
        // Clearing it should fail the validation when the transaction is committed.
        builder().clearId();
    }

    public List<Message> receivedEvents() {
        return ImmutableList.copyOf(receivedEvents);
    }

    @Override
    protected void onBeforeCommit() {
        updateNameLength();
        updateTypeValue();
    }

    private void updateTypeValue() {
        var newTypeValue = typeValueOf(isNullType);
        builder().setType(newTypeValue);
    }

    private void updateNameLength() {
        var newLengthValue = calculateLength(builder().getName());
        builder().setNameLength(newLengthValue);
    }

    public static ProjectionType typeValueOf(boolean isNullType) {
        return isNullType
               ? USEFUL
               : VERY_USEFUL;
    }

    public static int calculateLength(String name) {
        var lengthValue = name.length();
        return lengthValue;
    }
}
